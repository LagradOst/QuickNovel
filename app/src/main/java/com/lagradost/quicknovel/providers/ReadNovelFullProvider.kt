package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/*class ReadNovelFullProvider : AllNovelProvider() { // todo check
    override val mainUrl = "https://readnovelfull.com"
    override val name = "ReadNovelFull"
    override val ajaxUrl = "ajax/chapter-archive"
}*/

class ReadNovelFullProvider : MainAPI() {
    override val mainUrl = "https://readnovelfull.com"
    override val name = "ReadNovelFull"

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get(
            "$mainUrl/novel-list/search?keyword=$query",
            headers = mapOf("User-Agent" to USER_AGENT)
        ).document

        val headers = document.select("div.col-novel-main > div.list-novel > div.row")

        return headers.mapNotNull { h ->
            val divs = h.select("> div > div")
            val poster = divs[0].selectFirst("> img")?.attr("src")?.replace("t-200x89", "t-300x439")
            val titleHeader = divs[1].selectFirst("> h3.novel-title > a")
            val href = titleHeader?.attr("href")
            val title = titleHeader?.text()
            //val latestChapter = divs[2].selectFirst("> a > span")?.text()
            newSearchResponse(
                name = title ?: return@mapNotNull null,
                url = href ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(poster)
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div#chr-content")
            ?.html().textClean?.replace("[Updated from F r e e w e b n o v e l. c o m]", "")
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)

        val header = document.selectFirst("div.col-info-desc")
        val bookInfo = header?.selectFirst("> div.info-holder > div.books")
        val title = bookInfo?.selectFirst("> div.desc > h3.title")?.text()

        val desc = header?.selectFirst("> div.desc")
        val rateInfo = desc?.selectFirst("> div.rate-info")

        val rate = rateInfo?.selectFirst("> div.rate")

        val novelId = rate?.selectFirst("> div#rating")?.attr("data-novel-id")
            ?: throw Exception("novelId not found")


        val infoMetas = desc.select("> ul.info-meta > li")

        fun getData(valueId: String): Element? {
            for (i in infoMetas) {
                if (i?.selectFirst("> h3")?.text() == valueId) {
                    return i
                }
            }
            return null
        }

        val dataUrl = "$mainUrl/ajax/chapter-archive?novelId=$novelId"
        val dataResponse = app.get(dataUrl)
        val dataDocument =
            Jsoup.parse(dataResponse.text) ?: throw ErrorLoadingException("invalid dataDocument")
        val items =
            dataDocument.select("div.panel-body > div.row > div > ul.list-chapter > li > a")
                .mapNotNull {
                    newChapterData(
                        name = it.selectFirst("> span")?.text() ?: return@mapNotNull null,
                        url = it.attr("href") ?: return@mapNotNull null,
                    )
                }
        return newStreamResponse(
            name = title ?: throw ErrorLoadingException("No name"),
            url = url,
            data = items
        ) {
            author = getData("Author:")?.selectFirst("> a")?.text()
            tags = getData("Genre:")?.select("> a")?.map { it.text() }
            val statusText = getData("Status:")?.selectFirst("> a")?.text()
            status = when (statusText) {
                "Ongoing" -> STATUS_ONGOING
                "Completed" -> STATUS_COMPLETE
                else -> STATUS_NULL
            }
            synopsis = document.selectFirst("div.desc-text")?.text()
            rating = rate.selectFirst("> input")?.attr("value")?.toFloatOrNull()?.times(100)
                ?.toInt()
            peopleVoted =
                rateInfo.select("> div.small > em > strong > span")?.last()?.text()?.toIntOrNull()
            posterUrl = bookInfo.selectFirst("> div.book > img")?.attr("src")
        }
    }
}
