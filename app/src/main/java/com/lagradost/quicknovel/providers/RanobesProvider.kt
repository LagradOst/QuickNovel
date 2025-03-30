package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.network.CloudflareKiller
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import kotlin.random.Random


val mapper = jacksonObjectMapper().apply {
    propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
}

data class Chapterdatajson(
    @get:JsonProperty("book_title") val bookTitle: String? = null,

    @get:JsonProperty("book_link") val bookLink: String? = null,

    @get:JsonProperty("book_id") val bookID: Long? = null,

    val chapters: List<Chapter>? = null,

    @get:JsonProperty("pages_count") val pagesCount: Long? = null,

    @get:JsonProperty("count_all") val countAll: Long? = null,
    val cstart: Long? = null,
    val limit: Long? = null,
    val search: String? = null,
    val default: List<Any?>? = null,
    val searchTimeout: Any? = null
) {
    fun toJson() = mapper.writeValueAsString(this)

    companion object {
        fun fromJson(json: String) = mapper.readValue<Chapterdatajson>(json)
    }
}

data class Chapter(
    val id: String? = null,
    val title: String? = null,
    val date: String? = null,
    val showDate: String? = null,
    val link: String? = null
)


class RanobesProvider : MainAPI() {
    override val name = "Ranobes"
    override val mainUrl = "https://ranobes.top"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_ranobes

    override val iconBackgroundId = R.color.white

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

    val interceptor = CloudflareKiller()

    override suspend fun loadMainPage(
        page: Int, mainCategory: String?, orderBy: String?, tag: String?
    ): HeadMainPageResponse {
        val url = if (page <= 1) "$mainUrl/tags/genre/$tag/"
        else "$mainUrl/tags/genre/$tag/page/$page/"

        val response = app.get(url, headers = baseHeaders)

        val document = Jsoup.parse(response.text)

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

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url, headers = baseHeaders)
        val document = Jsoup.parse(response.text)
        delay(Random.nextLong(250, 350))
        return (document.selectFirst("#dle-content > article > div.block.story.shortstory > h1")
            ?.html() ?: return null) + document.selectFirst("#arrticle")?.html()
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
        val document = app.post(url, interceptor = interceptor).document
        val name = document.selectFirst("h1.title > span")?.text() ?: return null

        val listdata = mutableListOf<Chapterdatajson>()
        val data: ArrayList<ChapterData> = ArrayList()
        val chapretspageresponse = app.get(
            "$mainUrl/chapters/${url.substringAfterLast("/").substringBefore("-")}",
            headers = interceptor.getCookieHeaders(mainUrl).toMap()
        )
        val chapretspage = Jsoup.parse(chapretspageresponse.text)
        val cha1 = Chapterdatajson.fromJson(
            chapretspage.select("script")
                .filter { it -> it.data().contains("window.__DATA") }[0].data().substringAfter("=")
        )

        val numberofchpages =
            document.select("span.grey").filter { it -> it.text().contains("chapters") }[0].text()
                .filter { it.isDigit() }.toInt().div(25).plus(1)
        listdata.add(cha1)
        for (i in 2..numberofchpages) {
            val chapretspageresponsei = app.get(
                "$mainUrl/chapters/${
                    url.substringAfterLast("/").substringBefore("-")
                }/page/$i/", headers = baseHeaders
            )
            val chapretspagei = Jsoup.parse(chapretspageresponsei.text)
            listdata.add(
                Chapterdatajson.fromJson(
                    chapretspagei.select("script")
                        .filter { it -> it.data().contains("window.__DATA") }[0].data()
                        .substringAfter("=")
                )
            )
            if (i.rem(2) == 0) {
                delay(Random.nextInt(50, 100).toLong())
            } else {
                delay(Random.nextInt(0, 45).toLong())
            }
        }

        for (chapslist in listdata.reversed()) {
            chapslist.chapters?.reversed()?.map { it ->
                data.add(ChapterData(it.title!!, it.link!!, it.date!!, null))
            }
        }

        return newStreamResponse(url = url, name = name, data = data) {
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
                // Copy pasted from browser, hopefully does not break 💀
                document.selectFirst(".r-fullstory-spec > ul:nth-child(1) > li:nth-child(7) > span:nth-child(1) > a:nth-child(1)")
            setStatus(statusHeader?.text())
        }
    }
}
