package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import java.util.*

class BestLightNovelProvider : MainAPI() {
    override val name: String get() = "BestLightNovel"
    override val mainUrl: String get() = "https://bestlightnovel.com"

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val res = document.selectFirst("div.vung_doc")
        return res?.html().textClean?.replace("[Updated from F r e e w e b n o v e l. c o m]", "")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/search_novels/${query.replace(' ', '_')}")

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.danh_sach > div.list_category")
        if (headers.size <= 0) return ArrayList()
        return headers.mapNotNull {
            val head = it.selectFirst("> a")
            val name = head?.attr("title") ?: return@mapNotNull null
            val url = head.attr("href") ?: return@mapNotNull null

            val posterUrl = head.selectFirst("> img")?.attr("src")

            val rating = null
            val latestChapter = it.selectFirst("> a.chapter")?.text()
            SearchResponse(name, url, posterUrl, rating, latestChapter, this.name)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val infoHeaders = document.select("ul.truyen_info_right > li")

        val name = infoHeaders[0].selectFirst("> h1")?.text() ?: return null
        val authors = infoHeaders[1].select("> a")
        var author = ""
        for (a in authors) {
            val href = a?.attr("href")
            if (a.hasText() && (href?.length
                    ?: continue) > "$mainUrl/search_author/".length && href.startsWith("$mainUrl/search_author/")
            ) {
                author = a.text()
                break
            }
        }

        val posterUrl = document.select("span.info_image > img").attr("src")

        val tags: ArrayList<String> = ArrayList()
        val tagsHeader = infoHeaders[2].select("> a")
        for (t in tagsHeader) {
            tags.add(t.text())
        }
        val synopsis = document.select("div.entry-header > div")[1].text().textClean

        val chapterHeaders = document.select("div.chapter-list > div").mapNotNull {
            val spans = it.select("> span")
            val text = spans[0].selectFirst("> a")
            val cUrl = text?.attr("href") ?: return@mapNotNull null
            val cName = text.text() ?: return@mapNotNull null
            val added = spans[1].text()
            val views = null
            ChapterData(cName, cUrl, added, views)
        }.reversed()

        var rating = 0
        var peopleVoted = 0
        try {
            val ratingHeader = infoHeaders[9].selectFirst("> em > em")?.select("> em")
            rating = (ratingHeader?.get(1)?.selectFirst("> em > em")?.text()?.toFloat()
                ?.times(200))?.toInt() ?: 0

            peopleVoted = ratingHeader?.get(2)?.text()?.replace(",", "")?.toInt() ?: 0
        } catch (e: Exception) {
            // NO RATING
        }

        val views = infoHeaders[6].text()
            .replace(",", "")
            .replace("\"", "").substring("View : ".length).toInt()

        val status =
            when (infoHeaders[3].selectFirst("> a")?.text()?.lowercase()) {
                "ongoing" -> STATUS_ONGOING
                "completed" -> STATUS_COMPLETE
                else -> STATUS_NULL
            }

        return LoadResponse(
            url,
            name,
            chapterHeaders,
            author,
            posterUrl,
            rating,
            peopleVoted,
            views,
            synopsis,
            tags,
            status
        )
    }
}