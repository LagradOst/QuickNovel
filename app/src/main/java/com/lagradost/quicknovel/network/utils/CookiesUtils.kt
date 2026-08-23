package com.lagradost.quicknovel.network.utils

import android.webkit.CookieManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CookiesUtils {
    /** Parses a raw cookie string into a key-value Map */
    fun parseCookieMap(cookie: String?): Map<String, String> {
        if (cookie == null) return emptyMap()
        return cookie.split(";").associate {
            val split = it.split("=")
            (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
        }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
    }

    /** Converts a cookie Map back into a single string for HTTP headers */
    fun Map<String, String>.toCookieString(): String {
        return this.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * Retrieves cookies from the system's CookieManager for both the root domain
     * and the specific subdomain to ensure a complete session
     */
    fun getAllCookiesForUrl(url: String): Map<String, String> {
        val manager = CookieManager.getInstance()
        val uri = url.toHttpUrlOrNull() ?: return emptyMap()

        val rootDomain = uri.topPrivateDomain() ?: uri.host
        val rootCookies = parseCookieMap(manager.getCookie("${uri.scheme}://$rootDomain"))
        val subCookies = parseCookieMap(manager.getCookie(url))

        return rootCookies + subCookies
    }


    /** Removes Cloudflare clearance cookies for a host to force a clean bypass. */
    fun clearCookiesForHost(url: HttpUrl) {
        val manager = CookieManager.getInstance()
        val rootDomain = url.topPrivateDomain() ?: url.host
        manager.setCookie(url.toString(), "cf_clearance=; Max-Age=0")
        manager.setCookie("${url.scheme}://$rootDomain", "cf_clearance=; Max-Age=0")
        manager.flush()
    }
}