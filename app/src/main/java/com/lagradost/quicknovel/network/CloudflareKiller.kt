package com.lagradost.quicknovel.network

import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.AnyThread
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.cookies
import com.lagradost.nicehttp.getHeaders
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.mvvm.debugWarning
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.net.URI

import java.util.concurrent.ConcurrentHashMap

@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val mutex = Mutex()

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    val savedCookies = ConcurrentHashMap<String, Map<String, String>>()


    /**
     * Gets the headers with cookies, webview user agent included!
     * */
    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders = WebViewResolver.webViewUserAgent?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        return getHeaders(userAgentHeaders, null, savedCookies[URI(url).host] ?: emptyMap())
    }

    private fun clearCookiesForHost(url: HttpUrl) {
        val host = url.host
        val sUrl = url.toString()
        savedCookies.remove(host)

        val cookieManager = CookieManager.getInstance()
        cookieManager.getCookie(sUrl)?.let{ cookies ->
            cookies.split(";").forEach { cookie ->
                val cookieName = cookie.split("=").getOrNull(0)?.trim()
                if (cookieName != null) {
                    cookieManager.setCookie(sUrl, "$cookieName=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                }
            }
            cookieManager.flush()
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val host = request.url.host

        savedCookies[host]?.let { cookies ->
            val response = proceed(request, cookies)
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
            savedCookies[host]?.let { cookies ->
                val response = proceed(request, cookies)
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

        debugWarning({ true }) { "Failed cloudflare at: ${request.url}" }
        return@runBlocking chain.proceed(request)
    }

    private fun looksLikeCloudflareChallenge(response: Response): Boolean {
        val code = response.code
        val hasCloudflareHeaders =
            response.header("cf-ray") != null ||
                    response.header("server")?.contains("cloudflare", ignoreCase = true) == true

        if (code == 403 || code == 429 || code == 503) {
            if (hasCloudflareHeaders) return true

            val bodySample = runCatching {
                response.peekBody(1024 * 10).string().lowercase()
            }.getOrDefault("")

            return bodySample.contains("cf-browser-verification") ||
                    bodySample.contains("checking your browser") ||
                    bodySample.contains("just a moment") ||
                    bodySample.contains("/cdn-cgi/")
        }

        val location = response.header("location").orEmpty().lowercase()
        return location.contains("/cdn-cgi/")
    }

    private fun getWebViewCookie(url: String): String? {
        return CookieManager.getInstance()?.getCookie(url)
    }

    /**
     * Returns true if the cf cookies were successfully fetched from the CookieManager
     * Also saves the cookies.
     * */
    private fun trySolveWithSavedCookies(request: Request): Boolean {
        // Not sure if this takes expiration into account
        return getWebViewCookie(request.url.toString())?.let { cookie ->
            if (cookie.contains("cf_clearance")) {
                savedCookies[request.url.host] = parseCookieMap(cookie)
                true
            } else false
        } ?: false
    }

    private suspend fun proceed(request: Request, cookies: Map<String, String>): Response {
        val ua = WebViewResolver.webViewUserAgent ?: WebViewResolver.getWebViewUserAgent()
        val userAgentMap = ua?.let { mapOf("user-agent" to it) } ?: emptyMap()

        val headers = getHeaders(request.headers.toMap() + userAgentMap, null, cookies + request.cookies)

        return app.baseClient.newCall(
            request.newBuilder()
                .headers(headers)
                .build()
        ).await()
    }

    private suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()

        // If no cookies then try to get them
        // Remove this if statement if cookies expire
        if (!trySolveWithSavedCookies(request)) {
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
                trySolveWithSavedCookies(request)
            }
        }

        val cookies = savedCookies[request.url.host] ?: return null
        return proceed(request, cookies)
    }
}