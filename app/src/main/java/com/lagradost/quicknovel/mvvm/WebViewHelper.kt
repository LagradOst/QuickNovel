package com.lagradost.quicknovel.utils

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object SessionCookieProvider {
    private const val LOGIN_URL = "https://m.webnovel.com"

    suspend fun getValidCookie(context: Context): String = withContext(Dispatchers.Main) {
        suspendCoroutine { cont ->
            var isResumed = false

            val webView = WebView(context)
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (isResumed) return
                    isResumed = true

                    val cookies = cookieManager.getCookie(LOGIN_URL)
                    cont.resume(cookies ?: "")
                    webView.destroy() // Cleanup
                }
            }

            webView.loadUrl(LOGIN_URL)
        }
    }
}

