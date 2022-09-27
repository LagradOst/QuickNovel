package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app

class WattpadProvider : MainAPI() {
    override val mainUrl = "https://www.wattpad.com"
    override val name = "wattpad"
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "https://www.wattpad.com/search/$query"
        val document = app.get(url).document
        return document.select(".story-card").mapNotNull { element ->
            val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null
            val img =
                fixUrlNull(element.selectFirst(".story-card-data > .cover > img")?.attr("src"))
            val info =
                element.selectFirst(".story-card-data > .story-info") ?: return@mapNotNull null
            val title = info.selectFirst(".sr-only")?.text() ?: return@mapNotNull null
            //val description = info.selectFirst(".description")?.text()
            SearchResponse(name = title, url = href, posterUrl = img, apiName = name)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val description = document.selectFirst(".description-text")?.text()
        val tags = document.select("ul.tag-items > li > a").map { element ->
            element.text()
        }
        val author = document.selectFirst(".author-info__username > a")
            ?.text() //?: throw ErrorLoadingException("No author")
        val toc = document.select(".story-parts > ul > li > a").mapNotNull { a ->
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val name = (a.selectFirst("div") ?: a.selectFirst(".part__label"))?.text()
                ?: return@mapNotNull null
            ChapterData(url = href, name = name)
        }

        val title = document.selectFirst(".story-info > .sr-only")?.text() ?: document.selectFirst(".item-title")?.text()
            ?: throw ErrorLoadingException("No title")
        val poster = document.selectFirst(".story-cover > img")?.attr("src")

        return LoadResponse(url, title, toc, author, poster, synopsis = description, tags = tags)
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        return document.selectFirst("pre")
            ?.apply { removeClass("trinityAudioPlaceholder"); removeClass("comment-marker") }
            ?.html()
    }
}