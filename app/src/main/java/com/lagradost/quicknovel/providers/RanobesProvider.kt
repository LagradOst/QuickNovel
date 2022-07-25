package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import kotlin.random.Random


val mapper = jacksonObjectMapper().apply {
    propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
}

data class Chapterdatajson(
    @get:JsonProperty("book_title")
    val bookTitle: String? = null,

    @get:JsonProperty("book_link")
    val bookLink: String? = null,

    @get:JsonProperty("book_id")
    val bookID: Long? = null,

    val chapters: List<Chapter>? = null,

    @get:JsonProperty("pages_count")
    val pagesCount: Long? = null,

    @get:JsonProperty("count_all")
    val countAll: Long? = null,
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
    override val mainUrl = "https://ranobes.net"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_ranobes

    override val iconBackgroundId = R.color.white

    override val tags = listOf(
        Pair("Action", "Action"),
        Pair("Adult", "Adult"),
        Pair("Adventure", "Adventure"),
        Pair("Comedy", "Comedy"),
        Pair("Drama", "Drama"),
        Pair("Ecchi", "Ecchi"),
        Pair("Fantasy", "Fantasy"),
        Pair("Game", "Game"),
        Pair("Gender Bender", "Gender%20Bender"),
        Pair("Harem", "Harem"),
        Pair("Josei", "Josei"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Martial Arts", "Martial%20Arts"),
        Pair("Mature", "Mature"),
        Pair("Mecha", "Mecha"),
        Pair("Mystery", "Mystery"),
        Pair("Psychological", "Psychological"),
        Pair("Romance", "Romance"),
        Pair("School Life", "School%20Life"),
        Pair("Sci-fi", "Sci-fi"),
        Pair("Seinen", "Seinen"),
        Pair("Shoujo", "Shoujo"),
        Pair("Shounen", "Shounen"),
        Pair("Slice of Life", "Slice%20of%20Life"),
        Pair("Shounen Ai", "Shounen%20Ai"),
        Pair("Sports", "Sports"),
        Pair("Supernatural", "Supernatural"),
        Pair("Smut", "Smut"),
        Pair("Tragedy", "Tragedy"),
        Pair("Xianxia", "Xianxia"),
        Pair("Xuanhuan", "Xuanhuan"),
        Pair("Wuxia", "Wuxia"),
        Pair("Yaoi", "Yaoi"),
    )

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0",
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (page <= 1)
                "$mainUrl/tags/genre/$tag/"
            else
                "$mainUrl/tags/genre/$tag/page/$page/"

        val response = app.get(url, headers = baseHeaders)

        val document = Jsoup.parse(response.text)

        val headers = document.select("div.short-cont")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h2 = h?.selectFirst("h2.title > a")
            val cUrl = h2?.attr("href") ?: continue

            val name = h2.text()
            val posterUrl = h.selectFirst("div.cont.showcont > div > a > figure")?.attr("style")
                ?.replace("""background-image: url(""", "")?.replace(");", "")

            val latestChap =
                mainUrl + (h.nextElementSibling()?.selectFirst("div > a")?.attr("href")
                    ?: continue)
            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrlNull(posterUrl),
                    null,
                    latestChap,
                    this.name
                )
            )
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
        val response = app.post(
            "$mainUrl/index.php?do=search/",
            headers = mapOf(
                "referer" to mainUrl,
                "content-type" to "application/x-www-form-urlencoded",
            ),
            data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "search_start" to "0",
                "full_search" to "0",
                "result_from" to "1",
                "story" to query,
                "dosearch" to "Start search",
                """dofullsearch\""" to "Advanced"
            )
        )
        val document = Jsoup.parse(response.text)

        val headers = document.select("div.short-cont")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h2 = h?.selectFirst("h2.title > a")
            val cUrl = h2?.attr("href") ?: continue

            val name = h2.text() ?: continue
            val posterUrl = h.selectFirst("div.cont.showcont > div > a > figure")?.attr("style")
                ?.replace("""background-image: url(""", "")?.replace(");", "")

            val latestChap = mainUrl + h.nextElementSibling()?.selectFirst("div > a")?.attr("href")
            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrlNull(posterUrl),
                    null,
                    latestChap,
                    this.name
                )
            )
        }


        return returnValue
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url, headers = baseHeaders)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("div.r-fullstory-s1 > h1")?.text() ?: return null
        val author = document.selectFirst("span.tag_list")?.text()
        val tags = document.select("#mc-fs-genre > div > a").map {
            it.text()
        }

        val posterUrl = document.select("div.poster > a > img").attr("src")?.substringAfter("/")
        val synopsis = document.selectFirst("div.moreless__full")?.text()
        val listdata = mutableListOf<Chapterdatajson>()
        val data: ArrayList<ChapterData> = ArrayList()
        val chapretspageresponse =
            app.get(
                "$mainUrl/chapters/${url.substringAfterLast("/").substringBefore("-")}",
                headers = baseHeaders
            )
        val chapretspage = Jsoup.parse(chapretspageresponse.text)
        val cha1 = Chapterdatajson.fromJson(
            chapretspage.select("script")
                .filter { it -> it.data().contains("window.__DATA") }[0].data()
                .substringAfter("=")
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

        val statusHeader =
            // Copy pasted from browser, hopefully does not break 💀
            document.selectFirst(".r-fullstory-spec > ul:nth-child(1) > li:nth-child(7) > span:nth-child(1) > a:nth-child(1)")

        val status = when (statusHeader?.text()) {
            "Ongoing" -> STATUS_ONGOING
            "Completed" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }
        val views =
            document.selectFirst("#fs-info > div.r-fullstory-spec > ul:nth-child(2) > li:nth-child(1) > span")
                ?.text()?.filter { it.isDigit() }?.toIntOrNull()
        var rating = 0
        var peopleVoted = 0
        try {
            rating = (document.selectFirst("#rate_b > div > div > div > span.bold")?.text()
                ?.substringBefore("/")?.toFloatOrNull()?.times(200))?.toInt()!!

            peopleVoted =
                document.selectFirst("#rate_b > div > div > div > span.small.grey > span")?.text()!!
                    .filter { it.isDigit() }.toInt()
        } catch (e: Exception) {
            // NO RATING
        }
        return LoadResponse(
            url,
            name,
            data,
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