package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup

class FreewebnovelProvider : MainAPI() {
    override val name = "FreeWebNovel"
    override val mainUrl = "https://freewebnovel.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_freewebnovel

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", ""),
        Pair("Action", "Action"),
        Pair("Adult", "Adult"),
        Pair("AdventCure", "AdventCure"),
        Pair("Comedy", "Comedy"),
        Pair("Drama", "Drama"),
        Pair("Ecchi", "Ecchi"),
        Pair("Fantasy", "Fantasy"),
        //Pair("Editor's choice", "Editor's choice"),
        Pair("Gender Bender", "Gender+Bender"),
        Pair("Harem", "Harem"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Josei", "Josei"),
        Pair("Game", "Game"),
        Pair("Martial Arts", "Martial+Art"),
        Pair("Mature", "Mature"),
        Pair("Mecha", "Mecha"),
        Pair("Mystery", "Mystery"),
        Pair("Psychological", "Psychological"),
        Pair("Romance", "Romance"),
        Pair("School Life", "School+Life"),
        Pair("Sci-fi", "Sci-fi"),
        Pair("Seinen", "Seinen"),
        Pair("Shoujo", "Shoujo"),
        Pair("Shounen Ai", "Shounen+Ai"),
        Pair("Shounen", "Shounen"),
        Pair("Slice of Life", "Slice+of+Life"),
        Pair("Smut", "Smut"),
        Pair("Sports", "Sports"),
        Pair("Supernatural", "Supernatural"),
        Pair("Tragedy", "Tragedy"),
        Pair("Wuxia", "Wuxia"),
        Pair("Xianxia", "Xianxia"),
        Pair("Xuanhuan", "Xuanhuan"),
        Pair("Yaoi", "Yaoi"),
        Pair("Eastern", "Eastern"),
        Pair("Reincarnation", "Reincarnation"),
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/latest-novel/tag/$page.html" else "$mainUrl/genre/$tag/$page.html"
        val response = app.get(url)

        val document = Jsoup.parse(response.text)

        val headers = document.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h?.selectFirst("h3.tit > a")
            val cUrl =  fixUrl(h3?.attr("href") ?: continue)

            val name = h3.attr("title")
            val posterUrl = h.selectFirst("div.pic > a > img")?.attr("src")

            val latestChap = h.select("div.item")[2].selectFirst("> div > a")?.text()
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
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div.txt")?.html()
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/search/",
            headers = mapOf(
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*",
                "user-agent" to USER_AGENT
            ),
            data = mapOf("searchkey" to query)
        )
        val document = Jsoup.parse(response.text)


        val headers = document.select("div.li-row")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h?.selectFirst("h3.tit > a")
            val cUrl = fixUrl(h3?.attr("href") ?: continue)

            val name = h3.attr("title") ?: continue
            val posterUrl = h.selectFirst("div.pic > a > img")?.attr("src")

            val latestChap = h.select("div.item")[2].selectFirst("> div > a")?.text()
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
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("h1.tit")?.text() ?: return null

        val author =
            document.selectFirst("span.glyphicon.glyphicon-user")?.nextElementSibling()?.text()
        val tags =
            document.selectFirst("span.glyphicon.glyphicon-th-list")?.nextElementSiblings()?.get(0)
                ?.text()
                ?.splitToSequence(", ")?.toList()

        val posterUrl = document.select(" div.pic > img").attr("src")
        val synopsis = document.selectFirst("div.inner")?.text()

        val data: ArrayList<ChapterData> = ArrayList()
        val chapternumber0 = document.select("div.m-newest1 > ul.ul-list5 > li")[1]
        val chapternumber1 = chapternumber0.selectFirst("a")?.attr("href")
        val aid = "[0-9]+s.jpg".toRegex().find(response.text)?.value?.substringBefore("s")
        val acode = "(?<=r_url\" content=\"https://freewebnovel.com/)(.*)(?=/chapter)".toRegex()
            .find(response.text)?.value
        val chaptersDataphp = app.post(
            "$mainUrl/api/chapterlist.php",
            data = mapOf(
                "acode" to acode!!,
                "aid" to aid!!
            )
        )
        val parsed = Jsoup.parse(chaptersDataphp.text.replace("""\""", "")).select("option")

        for (c in parsed) {

            val cUrl = mainUrl + c?.attr("value")
            val cName = if (c.text().isEmpty()) {
                "chapter $c"
            } else {
                c.text()
            }
            data.add(ChapterData(cName, cUrl, null, null))
        }


        val statusHeader0 = document.selectFirst("span.s1.s2")
        val statusHeader = document.selectFirst("span.s1.s3")

        val status = if (statusHeader != null) {
            when (statusHeader.selectFirst("a")?.text()) {
                "OnGoing" -> STATUS_ONGOING
                "Completed" -> STATUS_COMPLETE
                else -> STATUS_NULL
            }

        } else {
            when (statusHeader0?.selectFirst("> a")?.text()) {
                "OnGoing" -> STATUS_ONGOING
                "Completed" -> STATUS_COMPLETE
                else -> STATUS_NULL
            }
        }

        var rating = 0
        var peopleVoted = 0
        try {
            rating = (document.selectFirst("div.m-desc > div.score > p:nth-child(2)")?.text()!!
                .substringBefore("/").toFloat() * 200).toInt()

            peopleVoted = document.selectFirst("div.m-desc > div.score > p:nth-child(2)")?.text()!!
                .substringAfter("(").filter { it.isDigit() }.toInt()
        } catch (e: Exception) {
            // NO RATING
        }

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