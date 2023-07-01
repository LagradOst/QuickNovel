package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.DownloadExtractLink
import com.lagradost.quicknovel.DownloadLink
import com.lagradost.quicknovel.DownloadLinkType
import com.lagradost.quicknovel.EpubResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull

class AnnasArchive : MainAPI() {
    override val hasMainPage = false
    override val hasReviews = false
    override val lang = "en"
    override val name = "Annas Archive"
    override val mainUrl = "https://annas-archive.org"

    //open val searchTags = "lang=en&content=book_fiction&ext=epub&sort=&"

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?lang=&content=&ext=epub&sort=&q=$query"

        val document = app.get(url).document
        return document.select("div.mb-4 > div > a").mapNotNull { element ->
            val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(element.selectFirst("div.flex-none > div > img")?.attr("src"))
            val name = element.selectFirst("div.relative > h3")?.text() ?: return@mapNotNull null
            SearchResponse(name = name, url = href, posterUrl = poster, apiName = this.name)
        }
    }

    private fun extract(url: String, name: String): DownloadLinkType {
        return if (url.contains(".epub")) {
            DownloadLink(
                url = url,
                name = name,
                kbPerSec = 2
            )
        } else {
            DownloadExtractLink(
                url = url,
                name = name
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).document

        return EpubResponse(
            url = url,
            posterUrl = response.selectFirst("main > div > img")?.attr("src"),
            author = response.selectFirst("main > div > div.italic")?.ownText(),
            synopsis = response.selectFirst("main > div > div.js-md5-top-box-description")?.text(),
            name = response.selectFirst("main > div > div.text-3xl")?.ownText()!!,
            links = response.select("div.mb-6 > ul.mb-4 > li > a").mapNotNull { element ->
                val link = fixUrlNull(element.attr("href")) ?: return@mapNotNull null
                extract(link, element.text())
            }
        )
    }
}