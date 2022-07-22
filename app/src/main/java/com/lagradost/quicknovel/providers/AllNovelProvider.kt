package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import java.lang.Exception
import kotlin.collections.ArrayList

class AllNovelProvider : MainAPI() {
    override val name = "AllNovel"
    override val mainUrl = "https://allnovel.org"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_allnovel

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", "All"),
        Pair("Shounen", "Shounen"),
        Pair("Harem", "Harem"),
        Pair("Comedy", "Comedy"),
        Pair("Martial Arts", "Martial Arts"),
        Pair("School Life", "School Life"),
        Pair("Mystery", "Mystery"),
        Pair("Shoujo", "Shoujo"),
        Pair("Romance", "Romance"),
        Pair("Sci-fi", "Sci-fi"),
        Pair("Gender Bender", "Gender Bender"),
        Pair("Mature", "Mature"),
        Pair("Fantasy", "Fantasy"),
        Pair("Horror", "Horror"),
        Pair("Drama", "Drama"),
        Pair("Tragedy", "Tragedy"),
        Pair("Supernatural", "Supernatural"),
        Pair("Ecchi", "Ecchi"),
        Pair("Xuanhuan", "Xuanhuan"),
        Pair("Adventure", "Adventure"),
        Pair("Action", "Action"),
        Pair("Psychological", "Psychological"),
        Pair("Xianxia", "Xianxia"),
        Pair("Wuxia", "Wuxia"),
        Pair("Historical", "Historical"),
        Pair("Slice of Life", "Slice of Life"),
        Pair("Seinen", "Seinen"),
        Pair("Lolicon", "Lolicon"),
        Pair("Adult", "Adult"),
        Pair("Josei", "Josei"),
        Pair("Sports", "Sports"),
        Pair("Smut", "Smut"),
        Pair("Mecha", "Mecha"),
        Pair("Yaoi", "Yaoi"),
        Pair("Shounen Ai", "Shounen Ai"),
        Pair("History", "History"),
        Pair("Reincarnation", "Reincarnation"),
        Pair("Martial", "Martial"),
        Pair("Game", "Game"),
        Pair("Eastern", "Eastern"),
        Pair("FantasyHarem", "FantasyHarem"),
        Pair("Yuri", "Yuri"),
        Pair("Magical Realism", "Magical Realism"),
        Pair("Isekai", "Isekai"),
        Pair("Supernatural Source:Explore", "Supernatural Source:Explore"),
        Pair("Video Games", "Video Games"),
        Pair("Contemporary Romance", "Contemporary Romance"),
        Pair("invayne", "invayne"),
        Pair("LitRPG", "LitRPG"),
        Pair("LGBT", "LGBT"),
        Pair(
            "Comedy Drama Romance Shounen Ai Supernatural",
            "Comedy Drama Romance Shounen Ai Supernatural"
        ),
        Pair("Shoujo Ai", "Shoujo Ai"),
        Pair("Supernatura", "Supernatura"),
        Pair("Canopy", "Canopy")
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val firstresponse = app.get(mainUrl)
        val firstdocument = Jsoup.parse(firstresponse.text)
        fun getId(tagvalue: String?): String? {
            return firstdocument.select("#hot-genre-select>option")
                .firstOrNull { it.text() == tagvalue }?.attr("value")
        }

        // I cant fix this because idk how it works
        val url = "$mainUrl/ajax-search?type=hot&genre=${getId(tag)}"
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val headers = document.select("div.item")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h?.selectFirst("a")
            val cUrl = mainUrl + h3?.attr("href")
            val name = h3?.attr("title") ?: throw ErrorLoadingException("Invalid name")

            val posterUrl =
                mainUrl + h.selectFirst("img")?.attr("src")

            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrlNull(posterUrl),
                    null,
                    null,
                    this.name
                )
            )
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("#chapter-content")?.html()?.replace(
            " If you find any errors ( broken links, non-standard content, etc.. ), Please let us know &lt; report chapter &gt; so we can fix it as soon as possible.",
            " "
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response =
            app.get("$mainUrl/search?keyword=$query") // AJAX, MIGHT ADD QUICK SEARCH

        val document = Jsoup.parse(response.text)


        val headers =
            document.select("#list-page > div.col-xs-12.col-sm-12.col-md-9.col-truyen-main.archive > div > div.row")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h?.selectFirst("h3.truyen-title > a")
            val cUrl = mainUrl + h3?.attr("href")
            val name = h3?.attr("title") ?: throw ErrorLoadingException("Invalid name")

            val posterUrl =
                mainUrl + Jsoup.parse(app.get(cUrl).text).select("div.book > img").attr("src")
            /*
            mainUrl+h?.selectFirst("div.col-xs-3 > div > img")?.attr("src")

             */

            val latestChap = h.selectFirst("div.col-xs-2.text-info > div > a")?.attr("title")
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

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("h3.title")?.text()

        val author = document.selectFirst("div.info > div:nth-child(1) > a")?.text()

        val posterUrl = document.select("div.book > img").attr("src")

        val tags = document.select("div.info > div:nth-child(3) a").map {
            it.text()
        }
        val synopsis = document.selectFirst("div.desc-text")?.text()

        val datanovelid = document.select("#rating").attr("data-novel-id")
        val chaptersData =
            app.get("https://allnovel.org/ajax-chapter-option?novelId=$datanovelid")
        val parsedchaptersData = Jsoup.parse(chaptersData.text)
        val parsed = parsedchaptersData.select("select > option")

        val data = parsed.map { c ->
            val cUrl = mainUrl + c?.attr("value")
            val cName = if (c.text().isEmpty()) {
                "chapter $c"
            } else {
                c.text()
            }
            ChapterData(cName, cUrl, null, null)
        }

        val statusHeader0 = document.selectFirst("div.info > div:nth-child(5) > a")
        val status = when (statusHeader0?.selectFirst("a")?.text()) {
            "Ongoing" -> STATUS_ONGOING
            "Completed" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }


        val rating = document.selectFirst(" div.small > em > strong:nth-child(1) > span")?.text()
            ?.toIntOrNull() ?: 0
        val peopleVoted =
            document.selectFirst(" div.small > em > strong:nth-child(3) > span")?.text()
                ?.toIntOrNull() ?: 0

        return LoadResponse(
            url,
            name ?: throw ErrorLoadingException("invalid name"),
            data,
            author,
            fixUrlNull(posterUrl),
            rating,
            peopleVoted,
            null,
            synopsis,
            tags,
            status
        )
    }
}
