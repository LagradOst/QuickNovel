package com.lagradost.quicknovel.network

import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.AnyThread
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.USER_AGENT
import com.lagradost.quicknovel.network.utils.CookiesUtils.clearCookiesForHost
import com.lagradost.quicknovel.network.utils.CookiesUtils.getAllCookiesForUrl
import com.lagradost.quicknovel.network.utils.CookiesUtils.toCookieString
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Interceptor designed to bypass Cloudflare and Turnstile protections.
 * It detects challenges and uses a hidden WebView to solve them and capture the resulting session.
 */
@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val mutex = Mutex() // Ensures only one bypass runs at a time
    }

    /*//this is used to testing
    init {
        CookieManager.getInstance().removeAllCookies(null)
    }
    */
    /** In-memory cache for cookies associated with each host*/
    val savedCookies = ConcurrentHashMap<String, Map<String, String>>()

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val url = request.url.toString()
        val host = request.url.host

        // If we already have cookies in CookieManager, use them
        val initialCookies = savedCookies[host] ?: getAllCookiesForUrl(url)
        if (initialCookies.containsKey("cf_clearance")) {
            val response = proceed(request, initialCookies)
            if (!looksLikeCloudflareChallenge(response)) return@runBlocking response

            // If the saved cookies still trigger a challenge, they are expired/invalid
            response.close()
            clearCookiesForHost(request.url)
            savedCookies.remove(host)
        }

        // try the request normally. Only invoke WebView bypass when
        // the response actually looks like a Cloudflare challenge
        val initialResponse = chain.proceed(request)
        if (!looksLikeCloudflareChallenge(initialResponse)) return@runBlocking initialResponse
        initialResponse.close()

        // Bypass needed. Locked section to prevent multiple WebViews from opening
        mutex.withLock {
            val currentCookies = savedCookies[host] ?: getAllCookiesForUrl(url)
            if (currentCookies.containsKey("cf_clearance")) {
                val response = proceed(request, currentCookies)
                if (!looksLikeCloudflareChallenge(response)) return@runBlocking response

                response.close()
                clearCookiesForHost(request.url)
                savedCookies.remove(host)
            }

            Log.d(TAG, "Resolving Cloudflare for $host...")
            val bypassResponse = bypassCloudflare(request)

            if (bypassResponse != null) {
                if (!looksLikeCloudflareChallenge(bypassResponse)) {
                    Log.d(TAG, "Succeeded bypassing cloudflare: ${request.url}")
                    return@runBlocking bypassResponse
                }
                bypassResponse.close()
            }
        }

        return@runBlocking chain.proceed(request)
    }

    private fun looksLikeCloudflareChallenge(response: Response): Boolean {
        val hasCloudflareHeaders =
            response.header("cf-ray") != null ||
                    response.header("server")?.contains("cloudflare", ignoreCase = true) == true

        // Read a small sample of the body to check for challenge scripts.
        val bodySample = runCatching {
            response.peekBody(1024 * 10).string().lowercase()
        }.getOrDefault("")

        val isChallengeBody = bodySample.contains("cf-browser-verification") ||
                bodySample.contains("checking your browser") ||
                bodySample.contains("just a moment") ||
                bodySample.contains("/cdn-cgi/") ||
                bodySample.contains("one moment...")

        if (response.code in listOf(403, 429, 503)) {
            if (hasCloudflareHeaders || isChallengeBody) return true
        }

        return response.header("location")
            .orEmpty()
            .lowercase()
            .contains("/cdn-cgi/") || isChallengeBody
    }

    /**
     * Reconstructs the request to mirror a real browser's identity
     * This clones the WebView's header order and Client Hints to bypass bot detection
     */
    private suspend fun proceed(request: Request, cookiesMap: Map<String, String>): Response {
        val host = request.url.host
        val ua = WebViewResolver.webViewUserAgent
                ?: WebViewResolver.getWebViewUserAgent()
                ?: USER_AGENT
        val captured = WebViewResolver.capturedHeaders[host] ?: emptyMap()

        val builder = Headers.Builder()
        builder.add("Host", host)
        captured.filter { it.key.lowercase().startsWith("sec-ch-ua") }.forEach { (k, v) ->
            val masked = v.replace(", \"Android WebView\";v=\"150\"", "")
                .replace("Android WebView", "Chromium")
            builder.add(k, masked)
        }
        builder.add("User-Agent", ua)
        builder.add(
            "Accept",
            captured["Accept"]
                ?: "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
        )
        builder.add("Origin", "${request.url.scheme}://${request.url.host}")

        captured.filter { it.key.lowercase().startsWith("sec-fetch-") }.forEach { (k, v) ->
            builder.add(k, v)
        }
        val referer = request.header("Referer") ?: captured["Referer"]
        ?: "${request.url.scheme}://${request.url.host}/"
        builder.add("Referer", referer)
        builder.add("Accept-Language", captured["Accept-Language"] ?: "en-US,en;q=0.9")

        val finalCookies = cookiesMap + request.headers.values("Cookie").associate {
            val split = it.split("=")
            (split.getOrNull(0) ?: "") to (split.getOrNull(1) ?: "")
        }.filter { it.key.isNotBlank() }

        builder.add("Cookie", finalCookies.toCookieString())

        val usedKeys = builder.build().names()
        request.headers.forEach { (k, v) ->
            if (!usedKeys.contains(k) //&&
            //!k.equals(DefaultImagesHeaders.useCloudflareKillerHeader.first, true) &&
            /*!k.equals(DefaultImagesHeaders.useIgnore500Header.first, true)*/) {
                builder.add(k, v)
            }
        }

        return app.baseClient.newCall(
            request.newBuilder()
                .headers(builder.build())
                .build()
        ).await()
    }

    /** Invokes the WebView to solve the challenge and extract new cookies*/
    private suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()
        val host = request.url.host

        // If no cookies then try to get them
        // Remove this if statement if cookies expire
        Log.d(TAG, "Loading webview to solve cloudflare for ${request.url}")
        WebViewResolver(
            // Never exit based on url
            interceptUrl = null,
            // Cloudflare needs default user agent
            userAgent = null,
            // Cannot use okhttp (i think intercepting cookies fails which causes the issues)
            useOkhttp = false,
        ).resolveUsingWebView(
            url
        ) {
            /**
             * Returns true if the cf cookies were successfully fetched from the CookieManager
             * */
            getAllCookiesForUrl(it.url.toString()).containsKey("cf_clearance")
        }

        val cookies = getAllCookiesForUrl(url)
        if (!cookies.containsKey("cf_clearance")) return null
        savedCookies[host] = cookies
        return proceed(request, cookies)
    }
}