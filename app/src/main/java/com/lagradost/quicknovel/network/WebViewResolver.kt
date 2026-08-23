package com.lagradost.quicknovel.network

import android.annotation.SuppressLint
import android.webkit.*
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.Coroutines.mainWork
import com.lagradost.nicehttp.requestCreator
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.network.utils.CookiesUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * An OkHttp interceptor that uses a hidden WebView to solve Cloudflare challenges.
 * It also supports extracting dynamic content through injected scripts.
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp will try to use the okhttp client as much as possible, but this might cause some requests to fail. Disable for cloudflare.
 * @param scriptToFinish Optional JavaScript that, when injected, must call NativeAndroid.onElementFound(String).
 * */
class WebViewResolver(
    val interceptUrl: Regex? = null,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = null,
    val useOkhttp: Boolean = false,
    val scriptToFinish: String? = null
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

    /** Common binary/asset extensions to block in the WebView to save bandwidth and speed up bypass. */
    private val blacklistedExtensions = setOf(
        "jpg", "png", "webp", "mpg", "mpeg", "jpeg", "webm",
        "mp4", "mp3", "gifv", "flv", "asf", "mov", "mng",
        "mkv", "ogg", "avi", "wav", "woff2", "woff", "ttf",
        "css", "vtt", "srt", "ts", "gif"
    )

    /** Utility to check if a URL belongs to a blocked tracker host. */
    private fun isBlockedTrackerUrl(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return blockedTrackerHosts.any { blocked ->
            host == blocked || host.endsWith(".$blocked")
        }
    }

    companion object {
        /** Cache for the system WebView's default User-Agent. */
        var webViewUserAgent: String? = null

        /** Global map to store high-fidelity headers (like sec-ch-ua) captured from the WebView. */
        val capturedHeaders = ConcurrentHashMap<String, Map<String, String>>()

        /** Regex to parse Content-Type and Charset from HTTP headers. */
        val CONTENT_TYPE_REGEX = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")

        /** Lazily retrieves and caches the default User-Agent from a dummy WebView. */
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

    /**
     * Standard OkHttp Interceptor implementation.
     * When a request is intercepted, it tries to "resolve" it using the hidden WebView.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            // resolveUsingWebView returns the final request after the bypass
            val fixedRequest = resolveUsingWebView(request).first as? Request
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    /** Overload for resolveUsingWebView using raw URL parameters. */
    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
    ): String? {
        CookiesUtils.clearCookiesForHost(url.toHttpUrl())
        return resolveUsingWebView(
            request = requestCreator(method, url, referer = referer)
        ).first as? String
    }

    /**
     * Overload for intercepting requests and headers
     * @param requestCallBack asynchronously return matched requests by either interceptUrl or additionalUrls. If true, destroy WebView.
     * @return the final request (by interceptUrl) and all the collected urls (by additionalUrls).
     * */
    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> {
        val result = resolveUsingWebView(
            request = requestCreator(method, url, referer = referer),
            requestCallBack = requestCallBack
        )
        return (result.first as? Request) to result.second
    }

    /**
     * Resolves the Cloudflare challenge and optionally extracts content.
     * @param requestCallBack asynchronously return matched requests by either interceptUrl or additionalUrls. If true, destroy WebView.
     * @return the final request (by interceptUrl) and all the collected urls (by additionalUrls), or the extracted script String.
     * */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean = { false }
    ): Pair<Any?, List<Request>> {
        val url = request.url.toString()
        val headers = request.headers
        println("Initial web-view request: $url")

        // We use a Deferred to wait for the WebView to signal completion (success or timeout)
        val deferredResponse = CompletableDeferred<Pair<Any?, List<Request>>>()
        var webView: WebView? = null
        val extraRequestList = mutableListOf<Request>()
        var fixedRequest: Request? = null
        var extractedResult: String? = null

        /** Reference to a delayed job used to wait for cookie rotation/stability before closing. */
        var stabilityJob: kotlinx.coroutines.Job? = null

        /** Safely tears down the WebView on the Main thread. */
        fun destroyWebView() {
            main {
                stabilityJob?.cancel()
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
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    webViewUserAgent = settings.userAgentString
                    // Don't set user agent, setting user agent will make cloudflare break.
                    if (userAgent != null) {
                        settings.userAgentString = userAgent
                    }
                    // Blocks unnecessary images, remove if captcha fucks.
                    //settings.blockNetworkImage = true

                    // allow third-party cookies for turnstile/challenges
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }

                class MyJavaScriptInterface {
                    @JavascriptInterface
                    fun onElementFound(html: String) {
                        if (html.isNotEmpty()) {
                            extractedResult = html
                            deferredResponse.complete(extractedResult to extraRequestList)
                        }
                    }
                }
                webView?.addJavascriptInterface(MyJavaScriptInterface(), "NativeAndroid")

                webView?.webViewClient = object : WebViewClient() {
                    /**
                     * Called for every sub-resource request (scripts, images, AJAX).
                     * We use this to block garbage, capture headers, and share the OkHttp state.
                     */
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
                        val req = request.toRequest()

                        if (!webViewUrl.contains("/cdn-cgi/") && !webViewUrl.contains("cloudflare")) {
                            capturedHeaders[runCatching { URI(webViewUrl).host }.getOrNull() ?: ""] = request.requestHeaders
                        }

                        // Check if this request matches our target URL
                        if (interceptUrl?.containsMatchIn(webViewUrl) == true) {
                            fixedRequest = req
                            deferredResponse.complete(req to extraRequestList)
                            return@runBlocking null
                        }

                        // Track additional interesting URLs
                        if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                            extraRequestList.add(req)

                            // If callback returns true (e.g., "I found what I wanted"), signal completion
                            if (requestCallBack(req)) {
                                deferredResponse.complete(fixedRequest to extraRequestList)
                            }
                        }

                        val path = runCatching { URI(webViewUrl).path }.getOrNull() ?: ""
                        val extension = path.substringAfterLast('.', "").lowercase()

                        // Optionally route WebView requests through OkHttp to sync cookies/state
                        return@runBlocking try {
                            when {
                                blacklistedExtensions.contains(extension) || webViewUrl.endsWith("/favicon.ico") || webViewUrl.startsWith("wss://") ->
                                    WebResourceResponse("image/png", null, null)
                                webViewUrl.contains("recaptcha") || webViewUrl.contains("/cdn-cgi/") -> super.shouldInterceptRequest(view, request)
                                useOkhttp && request.method == "GET" -> app.get(webViewUrl, headers = request.requestHeaders).okhttpResponse.toWebResourceResponse()
                                useOkhttp && request.method == "POST" -> app.post(webViewUrl, headers = request.requestHeaders).okhttpResponse.toWebResourceResponse()
                                else -> super.shouldInterceptRequest(view, request)
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }

                    /**
                     * Triggered when a page or frame finishes loading.
                     * We use this to detect bypass success and run auto-click automation scripts.
                     */
                    override fun onPageFinished(view: WebView?, finishUrl: String?) {
                        super.onPageFinished(view, finishUrl)
                        if (finishUrl == null) return

                        if (requestCallBack(requestCreator("GET", finishUrl))) {
                            if (scriptToFinish == null) {
                                stabilityJob?.cancel()
                                stabilityJob = main {
                                    delay(5.seconds)
                                    deferredResponse.complete(fixedRequest to extraRequestList)
                                }
                            }
                        }

                        scriptToFinish?.let {
                            view?.evaluateJavascript(it, null)
                        } ?: run {
                            val script = """
                                (function() {
                                    if (window.wasClicked) return;
                                    function tryClick() {
                                        var isCloudflarePage = document.querySelector('#challenge-form') || 
                                                               document.querySelector('#challenge-running') ||
                                                               document.querySelector('#cf-challenge-running');
                                        if (!isCloudflarePage) return; 
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
                }
                webView?.loadUrl(url, headers.toMap())
            } catch (e: Exception) {
                logError(e)
                deferredResponse.complete(null to emptyList())
            }
        }

        // Wait for the WebView to finish (max 60 seconds)
        val result = withTimeoutOrNull(60.seconds) {
            deferredResponse.await()
        }

        destroyWebView()
        return result ?: ((fixedRequest ?: extractedResult) to extraRequestList)
    }
}

/** Extension to convert a WebView request into an OkHttp Request. */
fun WebResourceRequest.toRequest(): Request {
    return requestCreator(
        this.method,
        this.url.toString(),
        this.requestHeaders,
    )
}

/** Extension to convert an OkHttp Response into a WebView-compatible WebResourceResponse. */
fun Response.toWebResourceResponse(): WebResourceResponse {
    val contentTypeValue = this.header("Content-Type")
    return if (contentTypeValue != null) {
        val found = WebViewResolver.CONTENT_TYPE_REGEX.find(contentTypeValue)
        val contentType = found?.groupValues?.getOrNull(1)?.ifBlank { null } ?: contentTypeValue
        val charset = found?.groupValues?.getOrNull(2)?.ifBlank { null }
        WebResourceResponse(contentType, charset, this.body.byteStream())
    } else {
        WebResourceResponse("application/octet-stream", null, this.body.byteStream())
    }
}