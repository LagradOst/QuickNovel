package com.lagradost.quicknovel.network

import android.util.Log
import androidx.annotation.AnyThread
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.USER_AGENT
import com.lagradost.quicknovel.network.utils.CookiesUtils.clearCookiesForHost
import com.lagradost.quicknovel.network.utils.CookiesUtils.getAllCookiesForUrl
import com.lagradost.quicknovel.network.utils.CookiesUtils.toCookieString
import com.lagradost.quicknovel.network.utils.HostSession
import com.lagradost.quicknovel.util.CommonHeaders
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

/**
 * Interceptor designed to bypass Cloudflare and Turnstile protections.
 * It detects challenges and uses a hidden WebView to solve them and capture the resulting session (cookies + headers).
 */
@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val mutex = Mutex() // Ensures only one bypass runs at a time
    }

    /** In-memory cache for cookies and headers associated with each host*/
    val savedSessions = ConcurrentHashMap<String, HostSession>()

    private fun getSession(host: String, url: String): HostSession {
        return savedSessions[host] ?: HostSession(
            getAllCookiesForUrl(url),
            WebViewResolver.capturedHeaders[host] ?: emptyMap()
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val url = request.url.toString()
        val host = request.url.host

        val selectorToWait = request.header(CommonHeaders.cloudflareKillerWaitSelectorHeader)
        val scriptToWait = request.header(CommonHeaders.cloudflareKillerWaitScriptHeader)
        
        val session = getSession(host, url)
        var initialCookies = session.cookies
        var initialHeaders = session.headers

        val cleanRequest = request.newBuilder()
            .removeHeader(CommonHeaders.cloudflareKillerWaitSelectorHeader)
            .removeHeader(CommonHeaders.cloudflareKillerWaitScriptHeader)
            .build()


        runCatching {
            val response = proceed(cleanRequest, initialCookies, initialHeaders)
            if (!looksLikeCloudflareChallenge(response, selectorToWait)) return@runBlocking response
            response.close()
        }

        clearCookiesForHost(request.url)
        savedSessions.remove(host)

        // Bypass needed. Locked section to prevent multiple WebViews from opening
        mutex.withLock {
            val lockedSession = getSession(host, url)
            initialCookies = lockedSession.cookies
            initialHeaders = lockedSession.headers

            if (!initialCookies.isEmpty()) {
                runCatching {
                    val response = proceed(cleanRequest, initialCookies, initialHeaders)
                    if (!looksLikeCloudflareChallenge(response, selectorToWait)) return@runBlocking response
                    response.close()
                }
                clearCookiesForHost(request.url)
                savedSessions.remove(host)
            }

            Log.d(TAG, "Resolving Cloudflare for $host...")
            return@runBlocking bypassCloudflare(cleanRequest, selectorToWait, scriptToWait)
        }
    }

    private fun looksLikeCloudflareChallenge(response: Response, selectorToWait: String? = null): Boolean {
        val hasCloudflareHeaders =
            response.header("cf-ray") != null ||
                    response.header("server")?.contains("cloudflare", ignoreCase = true) == true

        var bodySample = runCatching {
            response.peekBody(1024 * 10).string()
        }.getOrDefault("").lowercase()

        val isChallengeBody = bodySample.contains("cf-browser-verification") ||
                bodySample.contains("checking your browser") ||
                bodySample.contains("just a moment") ||
                bodySample.contains("/cdn-cgi/") ||
                bodySample.contains("loading...") ||
                bodySample.contains("one moment...")

        if (!selectorToWait.isNullOrBlank()) {
            bodySample = runCatching {
                response.peekBody(1024 * 500).string()
            }.getOrDefault("")

            val isFound = runCatching {
                val doc = Jsoup.parse(bodySample)
                doc.selectFirst(selectorToWait) != null
            }.getOrDefault(false)
            return !isFound
        }

        if (response.code in listOf(403, 429, 503)) {
            if (hasCloudflareHeaders || isChallengeBody) return true
        }

        return response.header("location")
            .orEmpty()
            .lowercase()
            .contains("/cdn-cgi/") || isChallengeBody
    }

    /**
     * Reconstructs the request to mirror a real browser's identity using cached or captured headers
     */
    private suspend fun proceed(
        request: Request,
        cookiesMap: Map<String, String>,
        headersMap: Map<String, String> = emptyMap()
    ): Response {
        val host = request.url.host

        val builder = Headers.Builder()

        builder.add("Host", host)

        //Add each header
        if (headersMap.isNotEmpty()) {
            headersMap.forEach { (k, v) ->
                if (!k.equals("Host", ignoreCase = true) && !k.equals("Cookie", ignoreCase = true)) {
                    builder.add(k, v)
                }
            }
        } else {
            builder.add("User-Agent", USER_AGENT)
            builder.add("Origin", "${request.url.scheme}://${request.url.host}")
            builder.add("Referer", "${request.url.scheme}://${request.url.host}/")
        }

        if (cookiesMap.isNotEmpty()) {
            builder.add("Cookie", cookiesMap.toCookieString())
        }

        val usedKeys = builder.build().names()
        request.headers.forEach { (k, v) ->
            if (!usedKeys.contains(k) && !k.equals("Cookie", ignoreCase = true)) {
                builder.add(k, v)
            }
        }

        return app.baseClient.newCall(
            request.newBuilder()
                .headers(builder.build())
                .build()
        ).await()
    }

    /** Invokes the WebView to solve the challenge and caches both cookies and headers in HostSession*/
    private suspend fun bypassCloudflare(
        request: Request,
        selectorToFinish: String? = null,
        scriptToFinish: String? = null
    ): Response {
        val url = request.url.toString()
        val host = request.url.host

        Log.d(TAG, "Loading webview to solve cloudflare for ${request.url}")
        val resolvedRequest = WebViewResolver(
            selectorToFinish = selectorToFinish,
            scriptToFinish = scriptToFinish
        ).getWellFormedRequest(
            request
        ) {
            /**
             * Returns true if the cf cookies were successfully fetched from the CookieManager
             * */
            getAllCookiesForUrl(request.url.toString()).containsKey("cf_clearance")
        }

        val cookies = getAllCookiesForUrl(url)
        val headers = WebViewResolver.capturedHeaders[host] ?: emptyMap()
        savedSessions[host] = HostSession(cookies, headers)

        return app.baseClient.newCall(resolvedRequest).await()
    }
}