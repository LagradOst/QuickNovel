package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.DataStore.toKotlinObject
import com.lagradost.quicknovel.MainActivity.Companion.app
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

class LightNovelPubProvider : MainAPI() {
    override val name = "LightNovelPub"
    override val mainUrl = "https://www.lightnovelpub.com"
    override val rateLimitTime: Long = 5000

    data class SearchRoot(
        @JsonProperty("\$id")
        val id: String,
        val success: Boolean,
        val resultview: String,
    )

    override suspend fun loadHtml(url: String): String? { // THEY RATE LIMIT THE FUCK ON THIS PROVIDER
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val items = document.selectFirst("div#chapter-container") ?: return null
        // THEY HAVE SHIT LIKE " <p class="kyzywl">The source of this content is lightnovelpub[.]com</p> " random class, no normal text has a class
        for (i in items.allElements) {
            if (i.tagName() == "p" && i.classNames().size > 0 && i.text()
                    .contains("lightnovelpub")
            ) {
                i.remove()
            }
        }
        return items.html()
    }

    private fun getChaps(document: Document): List<OrderedChapterData> {
        return document.select("ul.chapter-list > li").mapNotNull { parseChap(it) }
    }

    data class OrderedChapterData(
        val name: String,
        val url: String,
        val dateOfRelease: String?,
        val orderno: Int,
    )

    private fun parseChap(element: Element): OrderedChapterData? {
        val orderNum = element.attr("data-orderno").toInt()
        val a = element.selectFirst("> a")
        val href = fixUrl(a?.attr("href") ?: return null)
        val title = a.selectFirst("> strong.chapter-title")?.text()
        val time = a.selectFirst("> time.chapter-update")?.text() // attr datetime
        return OrderedChapterData(title ?: return null, href, time, orderNum)
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val poster = document.selectFirst("div.fixed-img > figure.cover > img")?.attr("data-src")
        val novelInfo = document.selectFirst("div.header-body > div.novel-info")
        val mainHead = novelInfo?.selectFirst("> div.main-head")

        val title = mainHead?.selectFirst("> h1.novel-title")?.text() ?: return null
        val author = mainHead.selectFirst("> div.author > a > span")?.text()
        val rating =
            mainHead.selectFirst("> div.rating > div.rating-star > p > strong")?.text()
                ?.toFloatOrNull()?.times(200)
                ?.toInt()

        val headerStats = novelInfo.select("> div.header-stats > span > strong")
        val viewsText = headerStats.get(1)?.text()?.lowercase(Locale.getDefault())
        val views = if (viewsText == null) null else {
            val times =
                when {
                    viewsText.contains("m") -> {
                        1000000
                    }
                    viewsText.contains("k") -> {
                        1000
                    }
                    else -> 1
                }
            (viewsText.replace("m", "").replace("k", "").toFloat() * times).toInt()
        }

        val status = when (headerStats.get(3)?.text()) {
            "Completed" -> 2
            "Ongoing" -> 1
            else -> 0
        }

        val genres =
            ArrayList(novelInfo.select("> div.categories > ul > li > a")?.map { it.text() }
                ?: listOf())
        val tags = ArrayList(document.select("> div.tags > ul.content > li > a")?.map { it.text() }
            ?: listOf())
        genres.addAll(tags)
        val synopsis = document.selectFirst("div.summary > div.content")?.text()

        val chapsDocument = Jsoup.parse(app.get("$url/chapters").text)

        val chaps = ArrayList(getChaps(chapsDocument))

        val pgs = chapsDocument.select("ul.pagination > li > a")
        val pages = pgs.mapNotNull {
            "(.*?page-)([0-9]*)".toRegex().find(it.attr("href"))
        }

        var highestPage = 0
        var link = ""
        for (p in pages) {
            val pageNumber = p.groupValues[2].toInt()
            if (pageNumber > highestPage) {
                highestPage = pageNumber
                link = p.groupValues[1]
            }
        }
        if (highestPage > 2) {
            link = fixUrl(link)
            val list = ArrayList<Pair<Int, String>>()
            for (i in 2..highestPage) {
                list.add(Pair(i - 2, link + i))
            }

            val dataList =
                list.map { // CANT PMAP DUE TO : This operation is rate limited.
                    delay(1000)
                    val localResponse = app.get(it.second)
                    val localDocument = Jsoup.parse(localResponse.text)
                    val localChaps = getChaps(localDocument)
                    if (localChaps.isEmpty()) {
                        throw Exception("No Chapters")
                    }
                    localChaps
                }

            for (i in dataList) {
                chaps.addAll(i)
            }

        }
        val data = chaps.sortedBy { it.orderno }
            .map { ChapterData(it.name, it.url, it.dateOfRelease, null) }

        return LoadResponse(
            url,
            title,
            data,
            author,
            poster,
            rating,
            null,
            views,
            synopsis,
            genres,
            status
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchRequest = app.get("$mainUrl/search")
        val searchToken = Jsoup.parse(searchRequest.text)
            .select("input[name='__LNRequestVerifyToken']").`val`()

        val url = "$mainUrl/lnsearchlive"
        // This fuckery because sometimes khttp fails to get cookies as it only uses lowercase
        val cookie = Regex("""lncoreantifrg=.*?;""").find(searchRequest.headers.toString())?.groupValues?.get(0)
        val response = app.post(
            url,
            data = mapOf("inputContent" to query),
            headers = mapOf(
                "LNRequestVerifyToken" to searchToken!!,
                // Using cookies = searchRequest.cookies doesn't work
                "cookie" to cookie!!
            )
        )
        val parse = response.text.toKotlinObject<SearchRoot>()
        val text = parse.resultview.replace("\\", "")

        val document = Jsoup.parse(text)
        val items = document.select("li.novel-item > a")
        if (items.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (item in items) {
            val title = item?.attr("title") ?: continue
            val href = item.attr("href") ?: continue
            val poster = item.selectFirst("> div.cover-wrap > figure > img")?.attr("src")
            val latestChap =
                "Chapter " + item.select("> div.item-body > div.novel-stats > span").last()?.text()
                    ?.replace("Chapters", "")

            returnValue.add(
                SearchResponse(
                    title,
                    fixUrl(href),
                    poster,
                    null,
                    latestChap,
                    this.name
                )
            )
        }

        return returnValue
    }
}