package com.lagradost.quicknovel.network

import android.annotation.SuppressLint
import android.webkit.*
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.Coroutines.mainWork
import com.lagradost.nicehttp.requestCreator
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.USER_AGENT
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

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
            val fixedRequest = resolveUsingWebView(request).first
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    /** Overload for resolveUsingWebView using raw URL parameters. */
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

        // We use a Deferred to wait for the WebView to signal completion (success or timeout)
        val deferredResponse = CompletableDeferred<Pair<Request?, List<Request>>>()
        var webView: WebView? = null
        val extraRequestList = mutableListOf<Request>()
        var fixedRequest: Request? = null

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

        // WebView must be created and interacted with on the UI (Main) thread
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

                    // allow third-party cookies for turnstile/challenges
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }

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

                        // Check if this request matches our target URL
                        if (interceptUrl.containsMatchIn(webViewUrl)) {
                            fixedRequest = request.toRequest().also {
                                requestCallBack(it)
                            }

                            if (!webViewUrl.contains("/cdn-cgi/") && !webViewUrl.contains("cloudflare")) {
                                capturedHeaders[runCatching { URI(webViewUrl).host }.getOrNull() ?: ""] = request.requestHeaders
                            }
                            return@runBlocking null
                        }

                        // Track additional interesting URLs
                        if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                            val req = request.toRequest()
                            extraRequestList.add(req)

                            if (!webViewUrl.contains("/cdn-cgi/") && !webViewUrl.contains("cloudflare")) {
                                capturedHeaders[runCatching { URI(webViewUrl).host }.getOrNull() ?: ""] = request.requestHeaders
                            }
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

                        // Detect if we are in a Cloudflare challenge page
                        val isChallenge = finishUrl.contains("cdn-cgi") ||
                                finishUrl.contains("recaptcha") ||
                                finishUrl.contains("challenges.cloudflare.com")

                        if (!isChallenge) {
                            // DEBOUNCE logic: Wait for 3 seconds of stability.
                            // If the page reloads (cookie rotation), we cancel the old job and start over.
                            stabilityJob?.cancel()
                            stabilityJob = main {
                                delay(3000L)
                                // Persist cookies to disk before OkHttp reads them
                                CookieManager.getInstance().flush()

                                // Final check: if callback returns true, we are done
                                if (requestCallBack(requestCreator("GET", finishUrl))) {
                                    deferredResponse.complete(fixedRequest to extraRequestList)
                                }
                            }
                        }

                        /**
                         * AUTO-CLICK SCRIPT:
                         * Actively looks for the Cloudflare Turnstile widget and clicks the submit button
                         * once the human verification token is generated.
                         */
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
                webView?.loadUrl(url, headers.toMap())
            } catch (e: Exception) {
                logError(e)
                deferredResponse.complete(null to emptyList())
            }
        }

        // Wait for the WebView to finish (max 60 seconds)
        val result = withTimeoutOrNull(60000L) {
            deferredResponse.await()
        }

        destroyWebView()
        return result ?: (fixedRequest to extraRequestList)
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