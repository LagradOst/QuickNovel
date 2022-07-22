package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ReadNovelFullProvider : MainAPI() {
    override val mainUrl = "https://readnovelfull.com"
    override val name = "ReadNovelFull"

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/search?keyword=$query", headers = mapOf("User-Agent" to USER_AGENT))

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.col-novel-main > div.list-novel > div.row")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        return headers.mapNotNull { h->
            val divs = h.select("> div > div")
            val poster = divs[0].selectFirst("> img")?.attr("src")
            val titleHeader = divs[1].selectFirst("> h3.novel-title > a")
            val href = titleHeader?.attr("href")
            val title = titleHeader?.text()
            val latestChapter = divs[2].selectFirst("> a > span")?.text()
            SearchResponse(
                title ?: return@mapNotNull null,
                fixUrl(href ?: return@mapNotNull null),
                fixUrlNull(poster),
                null,
                latestChapter,
                this.name
            )
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div#chr-content")?.html().textClean?.replace("[Updated from F r e e w e b n o v e l. c o m]", "")
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)

        val header = document.selectFirst("div.col-info-desc")
        val bookInfo = header?.selectFirst("> div.info-holder > div.books")
        val title = bookInfo?.selectFirst("> div.desc > h3.title")?.text()
        val poster = bookInfo?.selectFirst("> div.book > img")?.attr("src")
        val desc = header?.selectFirst("> div.desc")
        val rateInfo = desc?.selectFirst("> div.rate-info")
        val votes = rateInfo?.select("> div.small > em > strong > span")?.last()?.text()?.toIntOrNull()
        val rate = rateInfo?.selectFirst("> div.rate")

        val novelId = rate?.selectFirst("> div#rating")?.attr("data-novel-id")
            ?: throw Exception("novelId not found")
        val rating = rate.selectFirst("> input")?.attr("value")?.toFloatOrNull()?.times(100)
            ?.toInt()

        val syno = document.selectFirst("div.desc-text")?.text()

        val infoMetas = desc.select("> ul.info-meta > li")

        fun getData(valueId: String): Element? {
            for (i in infoMetas) {
                if (i?.selectFirst("> h3")?.text() == valueId) {
                    return i
                }
            }
            return null
        }

        val author = getData("Author:")?.selectFirst("> a")?.text()
        val tags = getData("Genre:")?.select("> a")?.map { it.text() }
        val statusText = getData("Status:")?.selectFirst("> a")?.text()
        val status = when (statusText) {
            "Ongoing" -> STATUS_ONGOING
            "Completed" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }

        val dataUrl = "$mainUrl/ajax/chapter-archive?novelId=$novelId"
        val dataResponse = app.get(dataUrl)
        val dataDocument = Jsoup.parse(dataResponse.text) ?: throw ErrorLoadingException("invalid dataDocument")
        val items =
            dataDocument.select("div.panel-body > div.row > div > ul.list-chapter > li > a").mapNotNull {
                ChapterData(it.selectFirst("> span")?.text() ?: return@mapNotNull null, fixUrl(it.attr("href")), null, null)
            }
        return LoadResponse(
            url,
            title ?: throw ErrorLoadingException("No name"),
            items,
            author,
            poster,
            rating,
            votes,
            null,
            syno,
            tags,
            status
        )
    }
}