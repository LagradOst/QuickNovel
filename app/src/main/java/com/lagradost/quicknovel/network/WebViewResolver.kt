package com.lagradost.quicknovel.network

import android.annotation.SuppressLint
import android.webkit.*
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.nicehttp.requestCreator
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.network.utils.CookiesUtils.getAllCookiesForUrl
import com.lagradost.quicknovel.network.utils.CookiesUtils.toCookieString
import com.lagradost.quicknovel.toUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * An OkHttp interceptor that uses a shared hidden WebView to solve Cloudflare challenges
 * and extract dynamic content with lazy initialization, sequential mutex execution, and idle auto-destruction.
 * */
class WebViewResolver(
    val interceptUrl: Regex? = null,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = null,
    val useOkhttp: Boolean = false,
    val selectorToFinish: String? = null,
    val scriptToFinish: String? = null,
) : Interceptor {

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
        val capturedHeaders = ConcurrentHashMap<String, Map<String, String>>()

        /** Regex to parse Content-Type and Charset from HTTP headers. */
        val CONTENT_TYPE_REGEX = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")

        // Shared WebView Singleton State
        private val mutex = Mutex()
        //This is only created when needed and is automatically destroyed after 3 minutes
        @SuppressLint("StaticFieldLeak")
        private var sharedWebView: WebView? = null
        private var idleDestructionJob: Job? = null
        private val scope = CoroutineScope(Dispatchers.Main + Job())
        @SuppressLint("SetJavaScriptEnabled")
        private fun getOrCreateWebView(ctx: android.content.Context): WebView {
            idleDestructionJob?.cancel()
            idleDestructionJob = null

            return sharedWebView ?: run {
                println("Initializing shared background WebView")
                WebView.setWebContentsDebuggingEnabled(true)
                WebView(ctx.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    sharedWebView = this
                }
            }
        }

        private fun scheduleIdleDestruction() {
            idleDestructionJob?.cancel()
            idleDestructionJob = scope.launch {
                delay(3.minutes)
                mutex.withLock {
                    main {
                        sharedWebView?.let { wv ->
                            wv.stopLoading()
                            wv.loadUrl("about:blank")
                            wv.destroy()
                            println("Destroyed shared WebView due to inactivity timeout")
                        }
                        sharedWebView = null
                    }
                }
            }
        }

        private fun putWebViewToRest() {
            main {
                sharedWebView?.let { wv ->
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.webViewClient = WebViewClient()
                    wv.removeJavascriptInterface("NativeAndroid")
                }
            }
            scheduleIdleDestruction()
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = getWellFormedRequest(request)
            return@runBlocking chain.proceed(fixedRequest)
        }
    }

    private fun buildWellFormedRequest(baseRequest: Request?, targetUrl: String): Request {
        val cookies = getAllCookiesForUrl(targetUrl)
        val host = targetUrl.toUrl()?.host ?: ""
        val captured = capturedHeaders[host] ?: emptyMap()
        val headerMap = mutableMapOf<String, String>()

        baseRequest?.headers?.forEach { (k, v) ->
            if (!k.equals("Cookie", ignoreCase = true)) {
                headerMap[k] = v
            }
        }

        captured.forEach { (k, v) ->
            headerMap[k] = v
        }

        if (cookies.isNotEmpty()) {
            headerMap["Cookie"] = cookies.toCookieString()
        }

        val reqUrl = baseRequest?.url?.toString() ?: targetUrl
        val reqMethod = baseRequest?.method ?: "GET"

        return requestCreator(reqMethod, reqUrl, headerMap)
    }

    suspend fun getWellFormedRequest(
        request: Request,
        requestCallBack: (Request) -> Boolean = { false }
    ): Request {
        val url = request.url.toString()
        val result = resolveUsingWebView(request, requestCallBack)
        val baseReq = (result.first as? Request) ?: requestCreator(request.method, url, request.headers.toMap())
        return buildWellFormedRequest(baseReq, url)
    }

    suspend fun getHtml(
        url: String,
        referer: String? = null,
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): String? {
        val result = resolveUsingWebView(
            request = requestCreator(method, url, referer = referer),
            requestCallBack = requestCallBack
        )
        return result.first as? String
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

        return mutex.withLock {
            val deferredResponse = CompletableDeferred<Pair<Any?, List<Request>>>()

            val ctx = context ?: throw RuntimeException("No base context in WebViewResolver")
            val extraRequestList = mutableListOf<Request>()
            var fixedRequest: Request? = null
            var stabilityJob: Job? = null

            main {
                try {
                    val webView = getOrCreateWebView(ctx)

                    if (userAgent != null) {
                        webView.settings.userAgentString = userAgent
                    }

                    class MyJavaScriptInterface {
                        @JavascriptInterface
                        fun onElementFound(html: String) {
                            //println("---------------------------------")
                            //println(html)
                            //println("---------------------------------")

                            CookieManager.getInstance().flush()
                            if (deferredResponse.isActive) {
                                deferredResponse.complete(html to extraRequestList)
                            }
                        }
                    }
                    webView.addJavascriptInterface(MyJavaScriptInterface(), "NativeAndroid")

                    webView.webViewClient = object : WebViewClient() {
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

                            if (webViewUrl.contains(url, ignoreCase = true) || url.contains(webViewUrl, ignoreCase = true)) {
                                fixedRequest = req
                            }

                            // Check if this request matches our target URL
                            if (interceptUrl?.containsMatchIn(webViewUrl) == true) {
                                fixedRequest = req
                                if (deferredResponse.isActive) {
                                    deferredResponse.complete(req to extraRequestList)
                                }
                                return@runBlocking null
                            }

                            // Track additional interesting URLs
                            if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                                extraRequestList.add(req)

                                // If callback returns true (e.g., "I found what I wanted"), signal completion
                                if (requestCallBack(req)) {
                                    if (deferredResponse.isActive) {
                                        deferredResponse.complete(fixedRequest to extraRequestList)
                                    }
                                }
                            }

                            val path = webViewUrl.toUrl()?.path ?: ""
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
                            if (finishUrl.isNullOrBlank() || (!finishUrl.startsWith("http://") && !finishUrl.startsWith("https://"))) return
                            if (requestCallBack(requestCreator("GET", finishUrl))) {
                                if (scriptToFinish == null && selectorToFinish == null) {
                                    stabilityJob?.cancel()
                                    stabilityJob = scope.launch {
                                        delay(5.seconds)
                                        if (deferredResponse.isActive) {
                                            deferredResponse.complete(fixedRequest to extraRequestList)
                                        }
                                    }
                                }
                            }
                            selectorToFinish?.let { sel ->
                                val escaped = sel.replace("\\", "\\\\").replace("'", "\\'")
                                view?.evaluateJavascript("""
                                    (function() {
                                     clearInterval(checkClassInterval);
                                     var checkClassInterval = setInterval(function() {
                                            var el = document.querySelector('$escaped');
                                            if (el) {
                                                clearInterval(checkClassInterval);
                                                NativeAndroid.onElementFound(document.body.outerHTML);
                                            }
                                     }, 1000);
                                     setTimeout(function() { clearInterval(checkClassInterval); }, 30000);
                                 })();
                            """.trimIndent(), null)
                            }
                            view?.evaluateJavascript(scriptToFinish ?: """
                                    (function() {
                                        if (window.wasClicked) return;
                                        function tryClick() {
                                            var isCloudflarePage = document.querySelector('#challenge-form') || 
                                                                   document.querySelector('#challenge-running') ||
                                                                   document.querySelector('#cf-challenge-running');
                                            if (!isCloudflarePage) return; 
                                            var cfToken = document.querySelector('[name="cf-turnstile-response"]')?.value 
                                                          || document.querySelector('#cf-chl-widget-multi-token')?.value;
                                            var submitButton = document.querySelector('#challenge-form button[type="submit"], .antibot-btn-success') 
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
                                """.trimIndent(), null)
                        }
                    }
                    webView.loadUrl(url, headers.toMap())
                } catch (e: Exception) {
                    logError(e)
                    if (deferredResponse.isActive) {
                        deferredResponse.complete(null to emptyList())
                    }
                }
            }

            // Wait for the WebView to finish (max 60 seconds)
            val result = withTimeoutOrNull(60.seconds) {
                deferredResponse.await()
            }

            stabilityJob?.cancel()
            putWebViewToRest()

            result ?: (fixedRequest to extraRequestList)
        }
    }
}

/** Extension to convert a WebView request into an OkHttp Request. */
fun WebResourceRequest.toRequest(): Request {
    return requestCreator(
        this.method,
        this.url.toString(),
        this.requestHeaders,
        this.url.toString()
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