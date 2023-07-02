package com.lagradost.quicknovel.extractors

import androidx.annotation.WorkerThread
import com.lagradost.quicknovel.DownloadExtractLink
import com.lagradost.quicknovel.DownloadLink
import com.lagradost.quicknovel.DownloadLinkType
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.apmap

fun ExtractorApi.fixUrl(url: String): String {
    if (url.startsWith("http") ||
        // Do not fix JSON objects when passed as urls.
        url.startsWith("{\"")
    ) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return mainUrl + url
        }
        return "$mainUrl/$url"
    }
}

fun ExtractorApi.fixUrlNull(url: String?): String? {
    return url?.let { fixUrl(url) }
}

abstract class ExtractorApi {
    companion object {
        private val extractors: List<ExtractorApi> = listOf(LibgenLi())

        fun removePrefixHttp(url : String) : String {
            return url.removePrefix("https://").removePrefix("http://")
        }

        /** recursive extraction of links */
        suspend fun extract(links: List<DownloadLinkType>, depth: Int = 5): List<DownloadLink> {
            return links.apmap { link ->
                when (link) {
                    is DownloadLink -> {
                        listOf(link)
                    }
                    is DownloadExtractLink -> {
                        if (depth <= 0) {
                            return@apmap emptyList()
                        }
                        val cmp = removePrefixHttp(link.url)
                        val newLinks = extractors.find { cmp.startsWith(it.mainUrlNoHttp) }
                            ?.getSafeUrl(link) ?: return@apmap emptyList()

                        extract(newLinks, depth - 1)
                    }
                    else -> {
                        emptyList()
                    }
                }
            }.flatten()
        }
    }

    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    val mainUrlNoHttp by lazy {
        removePrefixHttp(mainUrl)
    }

    @WorkerThread
    suspend fun getSafeUrl(
        link: DownloadExtractLink
    ): List<DownloadLinkType> {
        return try {
            getUrl(link) ?: emptyList()
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }

    /**
     * Will throw errors, use getSafeUrl if you don't want to handle the exception yourself
     */
    @WorkerThread
    open suspend fun getUrl(link: DownloadExtractLink): List<DownloadLinkType>? {
        return emptyList()
    }
}