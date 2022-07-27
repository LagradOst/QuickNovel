package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup

class EngNovelProvider : MainAPI() {
    override val name = "EngNovel"
    override val mainUrl = "https://engnovel.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_engnovel

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", ""),
        Pair("Action","action-novels"),
        Pair("Adult","adult-novels"),
        Pair("Adventure","adventure-novels"),
        Pair("Comedy","comedy-novels"),
        Pair("Drama","drama-novels"),
        Pair("Eastern","eastern-novels"),
        Pair("Ecchi","ecchi-novels"),
        Pair("Fantasy","fantasy-novels"),
        Pair("Game","game-novels"),
        Pair("Gender Bender","gender-bender-novels"),
        Pair("Harem","harem-novels"),
        Pair("Historical","historical-novels"),
        Pair("Horror","horror-novels"),
        Pair("Josei","josei-novels"),
        Pair("Lolicon","lolicon-novels"),
        Pair("Martial Arts","martial-arts-novels"),
        Pair("Mature","mature-novels"),
        Pair("Mecha","mecha-novels"),
        Pair("Modern Life","modern-life-novels"),
        Pair("Mystery","mystery-novels"),
        Pair("Psychological","psychological-novels"),
        Pair("Reincarnation","reincarnation-novels"),
        Pair("Romance","romance-novels"),
        Pair("School Life","school-life-novels"),
        Pair("Sci-fi","sci-fi-novels"),
        Pair("Seinen","seinen-novels"),
        Pair("Shoujo","shoujo-novels"),
        Pair("Shounen Ai","shounen-ai-novels"),
        Pair("Shounen","shounen-novels"),
        Pair("Slice of Life","slice-of-life-novels"),
        Pair("Smut","smut-novels"),
        Pair("Sports","sports-novels"),
        Pair("Supernatural","supernatural-novels"),
        Pair("Tragedy","tragedy-novels"),
        Pair("Wuxia","wuxia-novels"),
        Pair("Xianxia","xianxia-novels"),
        Pair("Xuanhuan","xuanhuan-novels"),
        Pair("Yaoi","yaoi-novels"),
        Pair("Yuri","yuri-novels")
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/latest-novels/page/$page" else "$mainUrl/$tag/page/$page"
        val response = app.get(url)

        val document = Jsoup.parse(response.text)

        val headers = document.select("div.home-truyendecu")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {

            val name = h.selectFirst("a")!!.attr("title")
            val posterUrl = h.selectFirst("img")?.attr("src")
            val cUrl = h.selectFirst("a")!!.attr("href")
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
        return document.selectFirst("div.chapter-content")?.html()
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document


        val headers = document.select("div.home-truyendecu")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {

            val name = h.selectFirst("a")!!.attr("title")
            val posterUrl = h.selectFirst("img")?.attr("src")
            val cUrl = h.selectFirst("a")!!.attr("href")
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
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("h3.title")?.text() ?: return null

        val infos = document.selectFirst("div.info")
        val author = infos?.getElementsByAttributeValue("itemprop", "author")?.joinToString(", ") { it.text() }
        val tags = infos?.getElementsByAttributeValue("itemprop","genre")?.map { it.text() }

        val posterUrl = document.select("div.book > img").attr("src")
        val synopsis = document.selectFirst("div.desc-text")?.text()

        val data: ArrayList<ChapterData> = ArrayList()
        val chapid= document.selectFirst("input#id_post")?.attr("value")
        val chaptersDataphp = app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            data = mapOf(
                "action" to "tw_ajax",
                "type" to "list_chap",
                "id" to chapid!!
            )
        )
        val parsed = Jsoup.parse(chaptersDataphp.text).select("option")

        for (c in parsed) {

            val cUrl = c?.attr("value")!!
            val cName = if (c.text().isEmpty()) {
                "chapter $c"
            } else {
                c.text()
            }
            data.add(ChapterData(cName, cUrl, null, null))
        }


        val statusdata = document.selectFirst("div.glyphicon.glyphicon-time")?.child(1)?.text()

        val status = when (statusdata) {
            "OnGoing" -> STATUS_ONGOING
            "Completed" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }

        var rating = document.getElementsByAttributeValue("itemprop", "ratingValue").text().toRate()
        var peopleVoted = document.getElementsByAttributeValue("itemprop", "ratingCount").text().toInt()


        return LoadResponse(
            url,
            name,
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