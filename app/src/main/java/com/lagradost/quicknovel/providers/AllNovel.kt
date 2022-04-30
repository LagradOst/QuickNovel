package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.DataStore.toKotlinObject
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class AllnovelsProvider : MainAPI() {
    override val name = "AllNovels"
    override val mainUrl = " https://allnovel.org/"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_wuxiaworldonline

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("Most Popular", ""),
        Pair("Chinese", "Chinese"),
        Pair("Action", "Action"),
        Pair("Adventure", "Adventure"),
        Pair("Fantasy", "Fantasy"),
        Pair("Martial Arts", "Martial Arts"),
        Pair("Romance", "Romance"),
        Pair("Xianxia", "Xianxia"),
        //Pair("Editor's choice", "Editor's choice"),
        Pair("Original", "Original"),
        Pair("Korean", "Korean"),
        Pair("Comedy", "Comedy"),
        Pair("Japanese", "Japanese"),
        Pair("Xuanhuan", "Xuanhuan"),
        Pair("Mystery", "Mystery"),
        Pair("Supernatural", "Supernatural"),
        Pair("Drama", "Drama"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Sci-Fi", "Sci-Fi"),
        Pair("Thriller", "Thriller"),
        Pair("Futuristic", "Futuristic"),
        Pair("Academy", "Academy"),
        Pair("Completed", "Completed"),
        Pair("Harem", "Harem"),
        Pair("School Life", "Schoollife"),
        Pair("Martial Arts", "Martialarts"),
        Pair("Slice of Life", "Sliceoflife"),
        Pair("English", "English"),
        Pair("Reincarnation", "Reincarnation"),
        Pair("Psychological", "Psychological"),
        Pair("Sci-fi", "Scifi"),
        Pair("Mature", "Mature"),
        Pair("Ghosts", "Ghosts"),
        Pair("Demons", "Demons"),
        Pair("Gods", "Gods"),
        Pair("Cultivation", "Cultivation"),
    )

    override fun loadMainPage(page: Int, mainCategory: String?, orderBy: String?, tag: String?): HeadMainPageResponse {
        val url = if (tag != "") {mainUrl+"genre/$tag"} else{"https://allnovel.org/most-popular"}
        val response = khttp.get(url)

        val document = Jsoup.parse(response.text)

        val headers = document.select("div.row")
        if (headers.size <= 1) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers.take(headers.size-12).takeLast(headers.size-13)) {
            val h3 = h.selectFirst("h3.truyen-title > a")
            val cUrl = mainUrl.substringBeforeLast("/")+h3.attr("href")

            val name = h3.attr("title")
            val posterUrl =  h.selectFirst("div.col-xs-3 > div > img").attr("src")

            val latestChap = h.selectFirst("div.col-xs-2.text-info > div > a").attr("title")
            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrl(posterUrl),
                    null,
                    latestChap,
                    this.name
                )
            )
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override fun loadHtml(url: String): String? {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("#chapter-content").html()
    }


    override fun search(query: String): List<SearchResponse> {
        val response =
            khttp.get("https://allnovel.org/search?keyword=$query") // AJAX, MIGHT ADD QUICK SEARCH

        val document = Jsoup.parse(response.text)


        val headers = document.select("div.row")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h.selectFirst(" iv.col-xs-7 > div > h3 > a")
            val cUrl = mainUrl.substringBeforeLast("/")+h3.attr("href")

            val name = h3.attr("title")
            val posterUrl =  mainUrl.substringBeforeLast("/")+h.selectFirst("div.col-xs-3 > div > img").attr("src")

            val latestChap = h.selectFirst("div.col-xs-2.text-info > div > a").attr("title")
            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrl(posterUrl),
                    null,
                    latestChap,
                    this.name
                )
            )
        }
        return returnValue
    }

    override fun load(url: String): LoadResponse {
        val response = khttp.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("h3.title").text()

        var author = document.selectFirst("div.info > div:nth-child(1) > a").text()

        val posterUrl = document.select("div.book > img").attr("src")

        val tags = document.select("div.info > div:nth-child(3) a").map{
            it.text()
        }
        val synopsis = document.selectFirst("div.desc-text").text()

        val data: ArrayList<ChapterData> = ArrayList()
        val datanovelid = document.select("#rating").attr("data-novel-id")
        val chaptersData = khttp.get("https://allnovel.org/ajax-chapter-option?novelId=$datanovelid")
        val parsedchaptersData = Jsoup.parse(chaptersData.text)
        val parsed = parsedchaptersData.select("select > option")
        for (c in parsed) {

            val cUrl = mainUrl.substringBeforeLast("/")+c.attr("value")
            val cName = if (c.text().isEmpty()){ "chapter $c"}
            else{c.text()}
            data.add(ChapterData(cName, cUrl, null, null))
        }


        val statusHeader0 = document.selectFirst("div.info > div:nth-child(5) > a")
        val status = when (statusHeader0.selectFirst("> a").text()) {
            "Ongoing" -> STATUS_ONGOING
            "Completed" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }

        var rating = 0
        var peopleVoted = 0
        try {
            rating = document.selectFirst(" div.small > em > strong:nth-child(1) > span").text().toInt()
            peopleVoted = document.selectFirst(" div.small > em > strong:nth-child(3) > span").text().toInt()
        } catch (e: Exception) {
            // NO RATING
        }

        return LoadResponse(
            url,
            name,
            data,
            author,
            fixUrl(posterUrl),
            rating,
            peopleVoted,
            null,
            synopsis,
            tags,
            status
        )
    }
}