package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup

open class MNovelFreeProvider : MainAPI() {
    override val name = "MNovelFree"
    override val mainUrl = "https://mnovelfree.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_mnovel

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", ""),
        Pair("Action", "action"),
        Pair("Drama", "drama"),
        Pair("Harem", "harem"),
        Pair("Mature", "mature"),
        Pair("Tragedy", "tragedy"),
        Pair("Comedy", "comedy"),
        Pair("Supernatural", "supernatural"),
        Pair("Josei", "josei"),
        Pair("Shoujo", "shoujo"),
        Pair("Mystery", "mystery"),
        Pair("Slice Of Life", "slice-of-life"),
        Pair("Psychological", "psychological"),
        Pair("Social Science", "social-science"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Adventure", "adventure"),
        Pair("Fantasy", "fantasy"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Romance", "romance"),
        Pair("Xuanhuan", "xuanhuan"),
        Pair("Xianxia", "xianxia"),
        Pair("Historical", "historical"),
        Pair("Wuxia", "wuxia"),
        Pair("Horror", "horror"),
        Pair("School Life", "school-life"),
        Pair("Seinen", "seinen"),
        Pair("Politics", "politics"),
        Pair("Lolicon", "lolicon"),
        Pair("Shounen", "shounen"),
        Pair("Other Books", "other-books")
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/lists/new-novels?page=$page" else "$mainUrl/genres/$tag?page=$page"

        val response = MainActivity.app.get(url)

        val document = Jsoup.parse(response.text)


        val headers = document.select("div.col-xs-4")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {

            val name = h.selectFirst("a")?.attr("title")
            val cUrl = h.selectFirst("a")?.attr("href")
            val posterUrl = h.selectFirst("img")?.attr("src")

            returnValue.add(
                SearchResponse(
                    name!!,
                    cUrl!!,
                    fixUrl(posterUrl!!),
                    null,
                    null,
                    this.name
                )
            )
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = MainActivity.app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div.chapter-content")!!.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response =
            MainActivity.app.get("$mainUrl/search?q=$query") // AJAX, MIGHT ADD QUICK SEARCH

        val document = Jsoup.parse(response.text)


        val headers = document.select("div.col-xs-4")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {

            val name = h.selectFirst("a")?.attr("title")
            val cUrl = h.selectFirst("a")?.attr("href")
            val posterUrl = h.selectFirst("img")?.attr("src")

            returnValue.add(
                SearchResponse(
                    name!!,
                    cUrl!!,
                    fixUrl(posterUrl!!),
                    null,
                    null,
                    this.name
                )
            )
        }
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse {
        val response = MainActivity.app.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("h3")?.text()

        val infos = Jsoup.parse(document.select("div.info").html())
        val author = infos.getElementsByAttributeValue("itemprop","author")?.map { it.text() }?.joinToString(",")
        val tags = infos.getElementsByAttributeValue("itemprop","genre")?.map { it.text() }

        val posterUrl = document.select("div.book > img").attr("src")
        val synopsis = document.selectFirst("div.desc-text > p")?.text()?.substringBefore("***")

        val data: ArrayList<ChapterData> = ArrayList()
        val document2 = Jsoup.parse(document.selectFirst("li.item-chapter > a") ?.let { MainActivity.app.get(it.attr("href")).text })
        val chapters = document2.selectFirst("select.goToChapter")?.select("option")
        if (chapters != null) {
            for (c in chapters) {
                if (c.attr("href").contains("category").not()) {
                    val cUrl =  c.attr("value")
                    val cName = c.text()
                    data.add(ChapterData(cName, cUrl, null, null))
                }
            }
        }

        val rating =
            document.getElementsByAttributeValue("itemprop","ratingValue").text()?.toFloat()?.times(100)?.toInt()
        val peopleVoted = document.getElementsByAttributeValue("itemprop","ratingCount")?.text()?.toInt()

        return LoadResponse(
            url,
            name!!,
            data,
            author,
            fixUrl(posterUrl),
            rating,
            peopleVoted,
            null,
            synopsis,
            tags,
            null,
        )
    }
}