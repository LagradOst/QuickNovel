package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ReadNovelFullProvider : MainAPI() {
    override val mainUrl = "https://readnovelfull.com"
    override val name = "ReadNovelFull"

    override fun search(query: String): List<SearchResponse> {
        val response = khttp.get("$mainUrl/search?keyword=$query")

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.col-novel-main > div.list-novel > div.row")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val divs = h.select("> div > div")
            val poster = divs[0].selectFirst("> img").attr("src")
            val titleHeader = divs[1].selectFirst("> h3.novel-title > a")
            val href = titleHeader.attr("href")
            val title = titleHeader.text()
            val latestChapter = divs[2].selectFirst("> a > span").text()
            returnValue.add(
                SearchResponse(
                    title,
                    fixUrl(href),
                    fixUrl(poster),
                    null,
                    latestChapter,
                    this.name
                )
            )
        }
        return returnValue
    }

    override fun loadHtml(url: String): String? {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div#chr-content").html().textClean
    }

    override fun load(url: String): LoadResponse {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)

        val header = document.selectFirst("div.col-info-desc")
        val bookInfo = header.selectFirst("> div.info-holder > div.books")
        val title = bookInfo.selectFirst("> div.desc > h3.title").text()
        val poster = bookInfo.selectFirst("> div.book > img").attr("src")
        val desc = header.selectFirst("> div.desc")
        val rateInfo = desc.selectFirst("> div.rate-info")
        val votes = rateInfo.select("> div.small > em > strong > span").last().text().toIntOrNull()
        val rate = rateInfo.selectFirst("> div.rate")

        val novelId = rate.selectFirst("> div#rating").attr("data-novel-id")
            ?: throw Exception("novelId not found")
        val rating = rate.selectFirst("> input").attr("value")?.toFloatOrNull()?.times(100)
            ?.toInt()

        val syno = document.selectFirst("div.desc-text").text()

        val infoMetas = desc.select("> ul.info-meta > li")

        fun getData(valueId: String): Element? {
            for (i in infoMetas) {
                if (i.selectFirst("> h3")?.text() == valueId) {
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
        val dataResponse = khttp.get(dataUrl)
        val dataDocument = Jsoup.parse(dataResponse.text) ?: throw Exception("invalid dataDocument")
        val items =
            dataDocument.select("div.panel-body > div.row > div > ul.list-chapter > li > a").map {
                ChapterData(it.selectFirst("> span").text(), fixUrl(it.attr("href")), null, null)
            }
        return LoadResponse(
            url,
            title,
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