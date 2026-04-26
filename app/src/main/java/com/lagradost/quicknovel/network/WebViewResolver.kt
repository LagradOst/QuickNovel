package com.lagradost.quicknovel.network

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.*
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.Coroutines.mainWork
import com.lagradost.nicehttp.requestCreator
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.USER_AGENT
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.net.URI

/**
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp will try to use the okhttp client as much as possible, but this might cause some requests to fail. Disable for cloudflare.
 * */
class WebViewResolver(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = USER_AGENT,
    val useOkhttp: Boolean = true
) :
    Interceptor {
    private val blockedTrackerHosts = setOf(
        "google-analytics.com",
        "googletagmanager.com",
        "googlesyndication.com",
        "doubleclick.net",
        "adtrafficquality.google",
        "sharethis.com",
        "count-server.sharethis.com",
        "fundingchoicesmessages.google.com"
    )

    private val blacklistedExtensions = setOf(
        "jpg", "png", "webp", "mpg", "mpeg", "jpeg", "webm",
        "mp4", "mp3", "gifv", "flv", "asf", "mov", "mng",
        "mkv", "ogg", "avi", "wav", "woff2", "woff", "ttf",
        "css", "vtt", "srt", "ts", "gif"
    )

    private fun isBlockedTrackerUrl(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return blockedTrackerHosts.any { blocked ->
            host == blocked || host.endsWith(".$blocked")
        }
    }

    companion object {
        var webViewUserAgent: String? = null
        val CONTENT_TYPE_REGEX = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")

        @JvmName("getWebViewUserAgent1")
        fun getWebViewUserAgent(): String? {
            return webViewUserAgent ?: context?.let { ctx ->
                runBlocking {
                    mainWork {
                        WebView(ctx).settings.userAgentString.also { userAgent ->
                            webViewUserAgent = userAgent
                        }
                    }
                }
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = resolveUsingWebView(request).first
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> {
        return resolveUsingWebView(
            requestCreator(method, url, referer = referer), requestCallBack
        )
    }

    /**
     * @param requestCallBack asynchronously return matched requests by either interceptUrl or additionalUrls. If true, destroy WebView.
     * @return the final request (by interceptUrl) and all the collected urls (by additionalUrls).
     * */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean = { false }
    ): Pair<Request?, List<Request>> {
        val url = request.url.toString()
        val headers = request.headers
        println("Initial web-view request: $url")

        val deferredResponse = CompletableDeferred<Pair<Request?, List<Request>>>()
        var webView: WebView? = null
        val extraRequestList = mutableListOf<Request>()
        var fixedRequest: Request? = null

        fun destroyWebView() {
            main {
                webView?.stopLoading()
                webView?.destroy()
                webView = null
                println("Destroyed webview")
            }
        }

        main {
            // Useful for debugging
            WebView.setWebContentsDebuggingEnabled(true)
            try {
                webView = WebView(
                    context
                        ?: throw RuntimeException("No base context in WebViewResolver")
                ).apply {
                    // Bare minimum to bypass captcha
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    webViewUserAgent = settings.userAgentString
                    // Don't set user agent, setting user agent will make cloudflare break.
                    if (userAgent != null) {
                        settings.userAgentString = userAgent
                    }
                    // Blocks unnecessary images, remove if captcha fucks.
                    //settings.blockNetworkImage = true
                }

                webView?.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? = runBlocking {
                        val webViewUrl = request.url.toString()
                        if (isBlockedTrackerUrl(webViewUrl)) {
                            return@runBlocking WebResourceResponse(
                                "text/plain",
                                "utf-8",
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }

                        println("Loading WebView URL: $webViewUrl")

                        if (interceptUrl.containsMatchIn(webViewUrl)) {
                            fixedRequest = request.toRequest().also {
                                requestCallBack(it)
                            }
                            println("Web-view request finished: $webViewUrl")
                            deferredResponse.complete(fixedRequest to extraRequestList)
                            return@runBlocking null
                        }

                        if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                            val req = request.toRequest()
                            extraRequestList.add(req)
                            if (requestCallBack(req)) {
                                deferredResponse.complete(fixedRequest to extraRequestList)
                            }
                        }

                        val path = runCatching { URI(webViewUrl).path }.getOrNull() ?: ""
                        val extension = path.substringAfterLast('.', "").lowercase()

                        return@runBlocking try {
                            when {
                                blacklistedExtensions.contains(extension) ||
                                        webViewUrl.endsWith("/favicon.ico") ||
                                        webViewUrl.startsWith("wss://") -> WebResourceResponse(
                                    "image/png",
                                    null,
                                    null
                                )
                                webViewUrl.contains("recaptcha") || webViewUrl.contains("/cdn-cgi/") -> super.shouldInterceptRequest(
                                    view,
                                    request
                                )

                                useOkhttp && request.method == "GET" -> app.get(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                useOkhttp && request.method == "POST" -> app.post(
                                    webViewUrl,
                                    headers = request.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                else -> super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed() // Ignore ssl issues
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url == null || url.contains("cdn-cgi") || url.contains("recaptcha")) return

                        val script = """
                            (function() {
                                if (window.wasClicked) return;
                    
                                function tryClick() {
                                    var isCloudflarePage = document.querySelector('#challenge-form') || 
                                                           document.querySelector('#challenge-running') ||
                                                           document.querySelector('#cf-challenge-running');
                    
                                    if (!isCloudflarePage) {
                                        return; 
                                    }
                    
                                    var cfToken = document.querySelector('[name="cf-turnstile-response"]')?.value 
                                                  || document.querySelector('#cf-chl-widget-multi-token')?.value;
                    
                                    var submitButton = document.querySelector('#challenge-form button[type="submit"]') 
                                                       || document.querySelector('#challenge-form input[type="submit"]');
                    
                                    if (cfToken && submitButton) {
                                        window.wasClicked = true;
                                        submitButton.click();
                                    } else {
                                        if (!window.retryCount) window.retryCount = 0;
                                        if (window.retryCount < 15) { 
                                            window.retryCount++;
                                            setTimeout(tryClick, 1000);
                                        }
                                    }
                                }
                                tryClick();
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(script, null)
                    }
                }
                webView?.loadUrl(url, headers.toMap())
            } catch (e: Exception) {
                logError(e)
                deferredResponse.complete(null to emptyList())
            }
        }

        val result = withTimeoutOrNull(60000L) {
            deferredResponse.await()
        }

        if (result == null) {
            println("Web-view timeout after 60s")
        }

        destroyWebView()
        return result ?: (fixedRequest to extraRequestList)
    }
}

fun WebResourceRequest.toRequest(): Request {
    return requestCreator(
        this.method,
        this.url.toString(),
        this.requestHeaders,
    )
}

fun Response.toWebResourceResponse(): WebResourceResponse {
    val contentTypeValue = this.header("Content-Type")
    return if (contentTypeValue != null) {
        val found = WebViewResolver.Companion.CONTENT_TYPE_REGEX.find(contentTypeValue)
        val contentType = found?.groupValues?.getOrNull(1)?.ifBlank { null } ?: contentTypeValue
        val charset = found?.groupValues?.getOrNull(2)?.ifBlank { null }
        WebResourceResponse(contentType, charset, this.body.byteStream())
    } else {
        WebResourceResponse("application/octet-stream", null, this.body.byteStream())
    }
}