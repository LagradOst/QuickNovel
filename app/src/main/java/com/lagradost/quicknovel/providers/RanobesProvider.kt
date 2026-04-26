package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.util.AppUtils.parseJson
import com.lagradost.quicknovel.util.AppUtils.toJson
import com.lagradost.quicknovel.util.AppUtils.tryParseJson
import org.json.JSONObject
import org.jsoup.nodes.Document


class RanobesProvider : MainAPI() {
    override val name = "Ranobes"
    override val mainUrl = "https://ranobes.net"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_ranobes
    override val iconBackgroundId = R.color.white
    override val usesCloudFlareKiller = true
    override val rateLimitTime = 500L
    override val tags = listOf(
        "Action" to "Action",
        "Adult" to "Adult",
        "Adventure" to "Adventure",
        "Comedy" to "Comedy",
        "Drama" to "Drama",
        "Ecchi" to "Ecchi",
        "Fantasy" to "Fantasy",
        "Game" to "Game",
        "Gender Bender" to "Gender%20Bender",
        "Harem" to "Harem",
        "Josei" to "Josei",
        "Historical" to "Historical",
        "Horror" to "Horror",
        "Martial Arts" to "Martial%20Arts",
        "Mature" to "Mature",
        "Mecha" to "Mecha",
        "Mystery" to "Mystery",
        "Psychological" to "Psychological",
        "Romance" to "Romance",
        "School Life" to "School%20Life",
        "Sci-fi" to "Sci-fi",
        "Seinen" to "Seinen",
        "Shoujo" to "Shoujo",
        "Shounen" to "Shounen",
        "Slice of Life" to "Slice%20of%20Life",
        "Shounen Ai" to "Shounen%20Ai",
        "Sports" to "Sports",
        "Supernatural" to "Supernatural",
        "Smut" to "Smut",
        "Tragedy" to "Tragedy",
        "Xianxia" to "Xianxia",
        "Xuanhuan" to "Xuanhuan",
        "Wuxia" to "Wuxia",
        "Yaoi" to "Yaoi",
    )

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:101.0) Gecko/20100101 Firefox/101.0",
    )


    override suspend fun loadMainPage(
        page: Int, mainCategory: String?, orderBy: String?, tag: String?
    ): HeadMainPageResponse {
        val url = if (page <= 1) "$mainUrl/tags/genre/$tag/"
        else "$mainUrl/tags/genre/$tag/page/$page/"

        val document = app.get(url, headers = baseHeaders).document

        val returnValue = document.select("div.short-cont").mapNotNull { h ->
            val h2 = h.selectFirst("h2.title > a") ?: return@mapNotNull null
            //val latestChap =
            //    mainUrl + (h.nextElementSibling()?.selectFirst("div > a")?.attr("href")
            //        ?: return@mapNotNull null)
            newSearchResponse(name = h2.text(), url = h2.attr("href") ?: return@mapNotNull null) {
                posterUrl = fixUrlNull(
                    h.selectFirst("div.cont.showcont > div > a > figure")?.attr("style")
                        ?.replace("""background-image: url(""", "")?.replace(");", "")
                )
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }
    private fun getChapters(document: Document):List<ChapterData>{
        val chapterListUrl = fixUrlNull(document.selectFirst("a.read-continue")?.attr("href"))
        val totalChapters = document.selectFirst("li[title=\"Glossary + illustrations + division of chapters, etc.\"] span")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0
        if (totalChapters > 25) {
            return (0..< totalChapters).map { chapterNumber ->
                val chapterUrl = "$chapterListUrl-------$chapterNumber-------$totalChapters"
                newChapterData("Chapter ${chapterNumber + 1}", chapterUrl)
            }

        }

        return document.select("ul.chapters-scroll-list li").reversed().mapIndexedNotNull { index, li ->
            val name = li.selectFirst("span.title")?.text() ?: "Chapter $index"
            val url = li.selectFirst("a")?.attr("href") ?: ""
            newChapterData(name, url)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "$mainUrl/index.php?do=search/", headers = mapOf(
                "referer" to mainUrl,
                "content-type" to "application/x-www-form-urlencoded",
            ), data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "search_start" to "0",
                "full_search" to "0",
                "result_from" to "1",
                "story" to query,
                "dosearch" to "Start search",
                """dofullsearch\""" to "Advanced"
            )
        ).document


        return document.select("div.short-cont").mapNotNull { h ->
            val h2 = h.selectFirst("h2.title > a")
            val cUrl = h2?.attr("href") ?: return@mapNotNull null
            val name = h2.text() ?: return@mapNotNull null
            newSearchResponse(name = name, url = cUrl) {
                posterUrl = fixUrlNull(
                    h.selectFirst("div.cont.showcont > div > a > figure")?.attr("style")
                        ?.replace("""background-image: url(""", "")?.replace(");", "")
                )
                //latestChapter = h.nextElementSibling()?.selectFirst("div > a")?.attr("href")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val name = document.selectFirst("h1.title")?.ownText() ?: return null
        val chapters = getChapters(document)
        return newStreamResponse(url = url, name = name, data = chapters) {
            author = document.select("h1.title > span").last()?.text()
            tags = document.select("#mc-fs-genre > div > a").map {
                it.text()
            }
            try {
                rating = (document.selectFirst("#rate_b > div > div > div > span.bold")?.text()
                    ?.substringBefore("/")?.toFloatOrNull()?.times(200))?.toInt()!!

                peopleVoted = document.selectFirst("div.rate-stat-num > span.small.grey")?.text()!!
                    .filter { it.isDigit() }.toInt()
            } catch (_: Throwable) {
            }
            synopsis = document.selectFirst("div.moreless")?.text()
            views =
                document.selectFirst("#fs-info > div.r-fullstory-spec > ul:nth-child(2) > li:nth-child(1) > span")
                    ?.text()?.filter { it.isDigit() }?.toIntOrNull()
            posterUrl =
                fixUrl(document.select("div.poster > a > img").attr("src").substringAfter("/"))
            val statusHeader =
                document.selectFirst("li[title=Original status in: Chinese, Japanese, English, etc.] > span")
            setStatus(statusHeader?.text())
        }
    }


    private fun getChapter(document: Document): List< String> {
        val script = document.selectFirst("script:containsData(window.__DATA__)")
            ?.data() ?: return emptyList()
        val jsonString = script
            .substringAfter("window.__DATA__ =")
            .substringBeforeLast("}")
            .trim() + "}"
        return parseJson<Root>(jsonString).chapters.map { it.link }.reversed()
    }

    override suspend fun loadHtml(url: String): String? {
        val chapterData = url.split("-------")
        if (chapterData.size < 3) {
            val dc = app.get(url, headers = baseHeaders).document
            return (dc.selectFirst("#dle-content > article > div.block.story.shortstory > h1")
                ?.html() ?: "") + (dc.selectFirst("#arrticle")?.html() ?: return null)
        }

        val baseUrl = chapterData[0].removeSuffix("/")
        val chapterBigIndex = chapterData[1].toInt()
        val totalChapters = chapterData[2].toInt()
        val itemsPerPage = 25

        val totalPages = (totalChapters + itemsPerPage - 1) / itemsPerPage

        val page = (totalChapters - 1 - chapterBigIndex) / itemsPerPage + 1

        val chaptersInLastPage = totalChapters % itemsPerPage.let { if (it == 0) itemsPerPage else it }

        val index = if (page == totalPages) {
            chapterBigIndex % itemsPerPage
        } else {
            (chapterBigIndex - chaptersInLastPage) % itemsPerPage
        }

        val pageUrl = if (page <= 1) "$baseUrl/" else "$baseUrl/page/$page/"
        val document = app.get(pageUrl, headers = baseHeaders).document
        val chaptersInPage = getChapter(document)

        val chapterUrl = chaptersInPage.getOrNull(index) ?: return null

        val dc = app.get(chapterUrl, headers = baseHeaders).document
        val title = dc.selectFirst("#dle-content > article > div.block.story.shortstory > h1")?.html() ?: ""
        val content = dc.selectFirst("#arrticle") ?: return null
        content.select("img").forEach { img ->
            val src = img.attr("src")
            if(src.isNotBlank()){
                val fixedSrc = fixUrlNull(src)
                if(fixedSrc != null){
                    img.attr("src", fixedSrc)
                }
            }
        }
        return title + content.html()
    }


    data class Root(
        @JsonProperty("book_title")
        val bookTitle: String,
        @JsonProperty("book_link")
        val bookLink: String,
        @JsonProperty("book_id")
        val bookId: Long,
        @JsonProperty("chapters")
        val chapters: List<Chapter>,
        @JsonProperty("pages_count")
        val pagesCount: Long,
        @JsonProperty("count_all")
        val countAll: Long,
        @JsonProperty("cstart")
        val cstart: Long,
        @JsonProperty("limit")
        val limit: Long,
        @JsonProperty("search")
        val search: String,
        @JsonProperty("default")
        val default: List<Any?>,
        @JsonProperty("search_timeout")
        val searchTimeout: Any?,
    )

    data class Chapter(
        @JsonProperty("id")
        val id: String,
        @JsonProperty("title")
        val title: String,
        @JsonProperty("date")
        val date: String,
        @JsonProperty("comm_num")
        val commNum: String,
        @JsonProperty("show_date")
        val showDate: String?,
        @JsonProperty("link")
        val link: String,
    )
}
