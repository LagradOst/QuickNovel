package com.lagradost.quicknovel.network.utils

import android.webkit.CookieManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CookiesUtils {
    /** Parses a raw cookie string into a key-value Map */
    fun parseCookieMap(cookie: String?): Map<String, String> {
        if (cookie.isNullOrBlank()) return emptyMap()
        return cookie.split(";").mapNotNull { pair ->
            val split = pair.split("=", limit = 2)
            val key = split.getOrNull(0)?.trim() ?: ""
            val value = split.getOrNull(1)?.trim() ?: ""
            if (key.isNotBlank() && value.isNotBlank()) {
                key to value
            } else {
                null
            }
        }.toMap()
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

        return (rootCookies + subCookies).filter { it.value.isNotBlank() }
    }


    /** Removes Cloudflare clearance cookies for a host to force a clean bypass. */
    fun clearCookiesForHost(url: HttpUrl) {
        val manager = CookieManager.getInstance()
        val uri = url.toString()
        val host = url.host
        val rootDomain = url.topPrivateDomain() ?: host

        val keys = mutableSetOf<String>()
        val rawCookies = (manager.getCookie(uri) ?: "") + "; " + (manager.getCookie("${url.scheme}://$rootDomain") ?: "")

        rawCookies.split(";").forEach {
            val name = it.split("=").firstOrNull()?.trim()
            if (!name.isNullOrBlank()) keys.add(name)
        }

        keys.forEach { name ->
            val deleteSuffix = "=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/"

            manager.setCookie(uri, "$name$deleteSuffix")
            manager.setCookie(host, "$name$deleteSuffix")
            manager.setCookie(rootDomain, "$name$deleteSuffix")
            manager.setCookie(".$rootDomain", "$name$deleteSuffix")
        }
        manager.flush()
    }
}