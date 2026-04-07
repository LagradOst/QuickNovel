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
import okhttp3.*
import java.net.URI


@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    /**
     * Gets the headers with cookies, webview user agent included!
     * */
    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders =  WebViewResolver.webViewUserAgent?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        return getHeaders(userAgentHeaders, null,savedCookies[URI(url).host] ?: emptyMap())
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val host = request.url.host
        val cookies = savedCookies[host]

        if (cookies != null) {
            val response = proceed(request, cookies)
            if (!looksLikeCloudflareChallenge(response)) {
                return@runBlocking response
            }
            response.close()
            savedCookies.remove(host)
        }

        // First try the request normally. Only invoke WebView bypass when
        // the response actually looks like a Cloudflare challenge.
        val initialResponse = chain.proceed(request)
        if (!looksLikeCloudflareChallenge(initialResponse)) {
            return@runBlocking initialResponse
        }
        initialResponse.close()

        CookieManager.getInstance().removeAllCookies(null)

        bypassCloudflare(request)?.let {
            Log.d(TAG, "Succeeded bypassing cloudflare: ${request.url}")
            return@runBlocking it
        }

        debugWarning({ true }) { "Failed cloudflare at: ${request.url}" }
        return@runBlocking chain.proceed(request)
    }

    private fun looksLikeCloudflareChallenge(response: Response): Boolean {
        val code = response.code
        val hasCloudflareHeaders =
            response.header("cf-ray") != null ||
                    response.header("server")?.contains("cloudflare", ignoreCase = true) == true

        val location = response.header("location").orEmpty().lowercase()
        if (location.contains("/cdn-cgi/")) return true

        val bodySample = runCatching {
            response.peekBody(64 * 1024).string().lowercase()
        }.getOrDefault("")

        val bodyLooksLikeChallenge =
            bodySample.contains("cf-browser-verification") ||
                    bodySample.contains("checking your browser") ||
                    bodySample.contains("just a moment") ||
                    bodySample.contains("attention required") ||
                    bodySample.contains("/cdn-cgi/")

        if (bodyLooksLikeChallenge) return true

        val challengeLikeStatus = code == 403 || code == 429 || code == 503
        return hasCloudflareHeaders && challengeLikeStatus
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
            cookie.contains("cf_clearance").also { solved ->
                if (solved) savedCookies[request.url.host] = parseCookieMap(cookie)
            }
        } ?: false
    }

    private suspend fun proceed(request: Request, cookies: Map<String, String>): Response {
        val userAgentMap = WebViewResolver.getWebViewUserAgent()?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        val headers =
            getHeaders(request.headers.toMap() + userAgentMap, null, cookies + request.cookies)
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