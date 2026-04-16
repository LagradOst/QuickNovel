package com.lagradost.quicknovel.providers

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class NovelLightProvider:  MainAPI() {
    override val name = "Novel Light"
    override val mainUrl = "https://novelight.net"
    val ajaxUrl = "$mainUrl/book/ajax/read-chapter"
    override val iconId = R.drawable.icon_novelight
    override val iconBackgroundId = R.color.novelightColor
    override val hasMainPage = true

    fun baseHeaders(url:String = "") =
        if(url.isNotEmpty())
            mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to url,
                "Accept" to "application/json, text/javascript, */*; q=0.01"
            )
    else emptyMap()
    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/catalog/?page=$page"
        val document = app.get(url).document

        val returnValue = document.select("div.manga-grid-list > a").mapNotNull { card ->
            val href = fixUrlNull(card?.attr("href")) ?: return@mapNotNull null
            val title = card.selectFirst("div.title")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(card.selectFirst("img")?.attr("src"))
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }

    suspend fun getChapters(document: Document): List<ChapterData> {
        val csrfToken = document.selectFirst("script:containsData(window.CSRF_TOKEN)")
            ?.data()
            ?.substringAfter("window.CSRF_TOKEN = \"")
            ?.substringBefore("\";") ?: return emptyList()

        val bookId = document.selectFirst("script:containsData(const BOOK_ID)")
            ?.data()
            ?.substringAfter("const BOOK_ID = \"")
            ?.substringBefore("\"") ?: return emptyList()
        val url = "$mainUrl/book/ajax/chapter-pagination?csrfmiddlewaretoken=$csrfToken&book_id=$bookId&page=1&pagination=0"
        val response = app.get(url, headers = baseHeaders(url)).parsed<ChapterResponse>()
        val document = Jsoup.parse(response.html)
       return document.select("a").mapNotNull { li ->
            if(li.selectFirst("span.cost") != null) return@mapNotNull null
            val name = li.selectFirst("div.title")?.text() ?: ""
            val href = fixUrlNull(li.attr("href")) ?: return@mapNotNull null
            newChapterData(name, href) {
                dateOfRelease = li.selectFirst("div.chapter-info > span.date")?.text()
            }
        }.reversed()
    }

    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document
        val infoDiv = document.select("div.container > div.flex-content")
        val title = document.selectFirst("header h1")?.text() ?: throw ErrorLoadingException("Title not found")

        val chapters = getChapters(document)
        return newStreamResponse(title,fixUrl(url), chapters) {
            this.posterUrl = fixUrlNull(infoDiv.selectFirst("div.poster > img")?.attr("src"))
            this.synopsis = document.select("section.text-info.section > p")?.joinToString("\n") { it.text() }

            document.select("div.block.mini-info > a").forEach { a ->
                if(a.text().contains("Author")){
                    this.author = a.selectFirst("div.info")?.text() ?: ""
                }
                else if(a.text().contains("Status"))
                    setStatus(a.selectFirst("div.info")?.text())
            }

            this.tags = infoDiv.selectFirst("div.block.mini-info > div > div.info")?.select("> a")?.mapNotNull {
                it.text().trim().takeIf { text ->  !text.isEmpty() }
            }
        }
    }

    override suspend fun loadHtml(url: String): String {
        val jsonResponse = app.get(
            url = ajaxUrl + "/${url.substringAfterLast("/book/chapter/")}",
            headers = baseHeaders(url),
        ).parsed<LoadHtmlResponse>()
        return jsonResponse.content
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/catalog/?search=${Uri.encode(query.trim()).replace("%20","+")}"
        val document = app.get(url).document

        return document.select("div.manga-grid-list > a").mapNotNull { card ->
            val href = fixUrlNull(card?.attr("href")) ?: return@mapNotNull null
            val title = card.selectFirst("div.title")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(card.selectFirst("img")?.attr("src"))
            }
        }
    }

    data class LoadHtmlResponse(
        @JsonProperty("class")
        val classContent: String,
        @JsonProperty("content")
        val content: String,
    )
    data class ChapterResponse(
        @JsonProperty("html")
        val html: String,
    )
}

