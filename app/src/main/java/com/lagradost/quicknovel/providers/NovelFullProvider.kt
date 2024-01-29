package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.R

class NovelFullProvider : AllNovelProvider() {
    override val name = "NovelFull"
    override val mainUrl = "https://novelfull.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_novelfull

    override val iconBackgroundId = R.color.white
}

/*
class NovelFullProvider : MainAPI() {
    override val name = "NovelFull"
    override val mainUrl = "https://novelfull.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_novelfull

    override val iconBackgroundId = R.color.white

    override val tags = listOf(
        "All" to  "All",
        "Shounen" to  "Shounen",
        "Harem" to  "Harem",
        "Comedy" to  "Comedy",
        "Martial Arts" to  "Martial Arts",
        "School Life" to  "School Life",
        "Mystery" to  "Mystery",
        "Shoujo" to  "Shoujo",
        "Romance" to  "Romance",
        "Sci-fi" to  "Sci-fi",
        "Gender Bender" to  "Gender Bender",
        "Mature" to  "Mature",
        "Fantasy" to  "Fantasy",
        "Horror" to  "Horror",
        "Drama" to  "Drama",
        "Tragedy" to  "Tragedy",
        "Supernatural" to  "Supernatural",
        "Ecchi" to  "Ecchi",
        "Xuanhuan" to  "Xuanhuan",
        "Adventure" to  "Adventure",
        "Action" to  "Action",
        "Psychological" to  "Psychological",
        "Xianxia" to  "Xianxia",
        "Wuxia" to  "Wuxia",
        "Historical" to  "Historical",
        "Slice of Life" to  "Slice of Life",
        "Seinen" to  "Seinen",
        "Lolicon" to  "Lolicon",
        "Adult" to  "Adult",
        "Josei" to  "Josei",
        "Sports" to  "Sports",
        "Smut" to  "Smut",
        "Mecha" to  "Mecha",
        "Yaoi" to  "Yaoi",
        "Shounen Ai" to  "Shounen Ai",
        "History" to  "History",
        "Reincarnation" to  "Reincarnation",
        "Martial" to  "Martial",
        "Game" to  "Game",
        "Eastern" to  "Eastern",
        "FantasyHarem" to  "FantasyHarem",
        "Yuri" to  "Yuri",
        "Magical Realism" to  "Magical Realism",
        "Isekai" to  "Isekai",
        "Supernatural Source:Explore" to  "Supernatural Source:Explore",
        "Video Games" to  "Video Games",
        "Contemporary Romance" to  "Contemporary Romance",
        "invayne" to  "invayne",
        "LitRPG" to  "LitRPG",
        "LGBT" to  "LGBT",
        Pair(
            "Comedy Drama Romance Shounen Ai Supernatural",
            "Comedy Drama Romance Shounen Ai Supernatural"
        ),
        "Shoujo Ai" to  "Shoujo Ai",
        "Supernatura" to  "Supernatura",
        "Canopy" to  "Canopy"
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
            for (i in firstdocument.select("#hot-genre-select>option")) {
                if (i.text() == tagvalue) {
                    return i?.attr("value")
                }
            }
            return null
        }

        // I cant fix this because idk how it works
        val url = "$mainUrl/ajax-search?type=hot&genre=${getId(tag)}"
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val headers = document.select("div.item")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h?.selectFirst("a") ?: continue
            val cUrl = h3.attr("href")
            val name = h3.attr("title")

            val posterUrl =
                mainUrl + h.selectFirst("img")?.attr("src")

            returnValue.add(
                SearchResponse(
                    name,
                    fixUrl(cUrl),
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
            val h3 = h?.selectFirst("h3.truyen-title > a") ?: continue
            val cUrl = mainUrl + h3.attr("href")
            val name = h3.attr("title")

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

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("h3.title")?.text() ?: return null

        val author = document.selectFirst("div.info > div:nth-child(1) > a")?.text()

        val posterUrl = document.select("div.book > img").attr("src")

        val tags = document.select("div.info > div:nth-child(3) a").map {
            it.text()
        }
        val synopsis = document.selectFirst("div.desc-text")?.text()

        val data: ArrayList<ChapterData> = ArrayList()
        val datanovelid = document.select("#rating").attr("data-novel-id")
        val chaptersData =
            app.get("https://allnovel.org/ajax-chapter-option?novelId=$datanovelid")
        val parsedchaptersData = Jsoup.parse(chaptersData.text)
        val parsed = parsedchaptersData.select("select > option")
        for (c in parsed) {

            val cUrl = mainUrl + c?.attr("value")
            val cName = if (c.text().isEmpty()) {
                "chapter $c"
            } else {
                c.text()
            }
            data.add(ChapterData(cName, cUrl, null, null))
        }


        val statusHeader0 = document.selectFirst("div.info > div:nth-child(5) > a")
        val status = when (statusHeader0?.selectFirst("a")?.text()) {
            "Ongoing" -> STATUS_ONGOING
            "Completed" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }

        var rating = 0
        var peopleVoted = 0
        try {
            rating =
                document.selectFirst(" div.small > em > strong:nth-child(1) > span")?.text()!!
                    .toInt()
            peopleVoted =
                document.selectFirst(" div.small > em > strong:nth-child(3) > span")?.text()!!
                    .toInt()
        } catch (e: Exception) {
            // NO RATING
        }

        return StreamResponse(
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
*/