package com.lagradost.quicknovel.network

import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.AnyThread
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.getHeaders
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.USER_AGENT
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val mutex = Mutex()

        fun parseCookieMap(cookie: String?): Map<String, String> {
            if (cookie == null) return emptyMap()
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
        fun Map<String, String>.toCookieString(): String {
            return this.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
    }

    /*//this is used to testing
    init {
        CookieManager.getInstance().removeAllCookies(null)
    }
    */
    val savedCookies = ConcurrentHashMap<String, Map<String, String>>()

    /**
     * Gets the headers with cookies, webview user agent included!
     * */
    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders = WebViewResolver.webViewUserAgent?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        val cookies = getAllCookiesForUrl(url)
        return getHeaders(userAgentHeaders, null, cookies)
    }

    private fun getAllCookiesForUrl(url: String): Map<String, String> {
        val manager = CookieManager.getInstance()
        val uri = url.toHttpUrlOrNull() ?: return emptyMap()

        val rootDomain = uri.topPrivateDomain() ?: uri.host
        val rootCookies = parseCookieMap(manager.getCookie("${uri.scheme}://$rootDomain"))
        val subCookies = parseCookieMap(manager.getCookie(url))

        return rootCookies + subCookies
    }

    private fun clearCookiesForHost(url: HttpUrl) {
        val manager = CookieManager.getInstance()
        val rootDomain = url.topPrivateDomain() ?: url.host
        manager.setCookie(url.toString(), "cf_clearance=; Max-Age=0")
        manager.setCookie("${url.scheme}://$rootDomain", "cf_clearance=; Max-Age=0")
        manager.flush()
        savedCookies.remove(url.host)
        savedCookies.remove(rootDomain)
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val url = request.url.toString()
        val host = request.url.host

        val initialCookies = savedCookies[host] ?: getAllCookiesForUrl(url)
        if (initialCookies.containsKey("cf_clearance")) {
            val response = proceed(request, initialCookies)
            if (!looksLikeCloudflareChallenge(response)) {
                return@runBlocking response
            }
            response.close()
            clearCookiesForHost(request.url)
        }

        // First try the request normally. Only invoke WebView bypass when
        // the response actually looks like a Cloudflare challenge.
        val initialResponse = chain.proceed(request)
        if (!looksLikeCloudflareChallenge(initialResponse)) {
            return@runBlocking initialResponse
        }
        initialResponse.close()

        mutex.withLock {
            val currentCookies = savedCookies[host] ?: getAllCookiesForUrl(url)
            if (currentCookies.containsKey("cf_clearance")) {
                val response = proceed(request, currentCookies)
                if (!looksLikeCloudflareChallenge(response)) return@runBlocking response
                response.close()
                clearCookiesForHost(request.url)
            }

            Log.d(TAG, "Resolving Cloudflare for $host...")
            val bypassResponse = bypassCloudflare(request)

            if (bypassResponse != null) {
                Log.d(TAG, "Succeeded bypassing cloudflare: ${request.url}")
                return@runBlocking bypassResponse
            }
        }

        return@runBlocking chain.proceed(request)
    }

    private fun looksLikeCloudflareChallenge(response: Response): Boolean {
        val code = response.code
        val hasCloudflareHeaders =
            response.header("cf-ray") != null ||
                    response.header("server")?.contains("cloudflare", ignoreCase = true) == true

        val bodySample = runCatching {
            response.peekBody(1024 * 10).string().lowercase()
        }.getOrDefault("")

        val isChallengeBody = bodySample.contains("cf-browser-verification") ||
                bodySample.contains("checking your browser") ||
                bodySample.contains("just a moment") ||
                bodySample.contains("un momento") ||
                bodySample.contains("/cdn-cgi/")

        if (code == 403 || code == 429 || code == 503 || (code == 200 && (bodySample.contains("one moment")))) {
            if (hasCloudflareHeaders || isChallengeBody) return true
        }

        val location = response.header("location").orEmpty().lowercase()
        return location.contains("/cdn-cgi/") || isChallengeBody
    }


    private suspend fun proceed(request: Request, cookiesMap: Map<String, String>): Response {
        val host = request.url.host
        val ua = WebViewResolver.webViewUserAgent ?: WebViewResolver.getWebViewUserAgent() ?: USER_AGENT
        val captured = WebViewResolver.capturedHeaders[host] ?: emptyMap()

        val builder = Headers.Builder()
        builder.add("Host", host)
        captured.filter { it.key.lowercase().startsWith("sec-ch-ua") }.forEach { (k, v) ->
            val masked = v.replace(", \"Android WebView\";v=\"150\"", "").replace("Android WebView", "Chromium")
            builder.add(k, masked)
        }
        builder.add("User-Agent", ua)
        builder.add("Accept", captured["Accept"] ?: "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        if (request.method == "POST") {
            builder.add("Origin", "${request.url.scheme}://${request.url.host}")
        }
        captured.filter { it.key.lowercase().startsWith("sec-fetch-") }.forEach { (k, v) ->
            builder.add(k, v)
        }
        val referer = request.header("Referer") ?: captured["Referer"] ?: "${request.url.scheme}://${request.url.host}/"
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

    private suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()
        val host = request.url.host

        // If no cookies then try to get them
        // Remove this if statement if cookies expire
        Log.d(TAG, "Loading webview to solve cloudflare for ${request.url}")
        WebViewResolver(
            // Never exit based on url
            Regex(".^"),
            // Cloudflare needs default user agent
            userAgent = null,
            // Cannot use okhttp (i think intercepting cookies fails which causes the issues)
            useOkhttp = false,
            // Match every url for the requestCallBack
            additionalUrls = listOf(Regex("."))
        ).resolveUsingWebView(
            url
        ) {
            /**
             * Returns true if the cf cookies were successfully fetched from the CookieManager
             * */
            val cookie = CookieManager.getInstance().getCookie(it.url.toString())
            cookie?.contains("cf_clearance") == true
        }

        val cookies = getAllCookiesForUrl(url)
        if (cookies.containsKey("cf_clearance")) {
            savedCookies[host] = cookies
            return proceed(request, cookies)
        }
        return null
    }
}