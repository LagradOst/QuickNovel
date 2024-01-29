package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ReadLightNovelProvider : MainAPI() {
    override val name = "ReadLightNovel"
    override val mainUrl = "https://www.readlightnovel.me"
    override val iconId = R.drawable.big_icon_readlightnovel
    override val hasMainPage = true

    override val iconBackgroundId = R.color.readLightNovelColor

    override val orderBys: List<Pair<String, String>>
        get() = listOf(
            "Top Rated" to "top-rated",
            "Most Viewed" to "most-viewed"
        )

    override val tags = listOf(
        "All" to "",
        "Action" to "action",
        "Adventure" to "adventure",
        "Celebrity" to "celebrity",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Fantasy" to "fantasy",
        "Gender Bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shotacon" to "shotacon",
        "Shoujo" to "shoujo",
        "Shoujo Ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            "$mainUrl/${if (tag == "") "top-novels" else "genre/$tag"}/$orderBy/$page"
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.top-novel-block")
        val returnValue = headers.mapNotNull {
            val content = it.selectFirst("> div.top-novel-content")
            val nameHeader = it.selectFirst("div.top-novel-header > h2 > a")
            val cUrl = nameHeader?.attr("href") ?: return@mapNotNull null
            val name = nameHeader.text() ?: return@mapNotNull null
            /* val tags = ArrayList(
                 content.select("> div.top-novel-body > div.novel-item > div.content")
                     .last().select("> ul > li > a").map { t -> t.text() })*/
            newSearchResponse(name = name, url = cUrl) {
                posterUrl =
                    fixUrlNull(content?.selectFirst("> div.top-novel-cover > a > img")?.attr("src"))
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val content = document.selectFirst("div.chapter-content3 > div.desc") ?: return null
        //content.select("div").remove()
        content.select("div.alert").remove()
        content.select("#podium-spot").remove()
        content.select("iframe").remove()
        content.select("small.ads-title").remove()
        content.select("script").remove()
        content.select("div").remove()
        content.select("p.hid").remove()

        for (i in content.allElements) {
            if (i.tagName() == "p" && (i.text().contains("lightnovelpub") || i.text()
                    .contains("readlightnovel"))
            ) {
                i.remove()
            } else if (i.classNames().contains("hidden") || i.classNames().contains("hid")) {
                i.remove()
            }
        }

        return content.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/search/autocomplete",
            headers = mapOf(
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*",
                "user-agent" to USER_AGENT
            ),
            data = mapOf("q" to query)
        )
        val document = Jsoup.parse(response.text)
        return document.select("li > a").mapNotNull { h ->
            val spans = h.select("> span")

            val name = spans[1].text()
            val url = h?.attr("href") ?: return@mapNotNull null

            newSearchResponse(name = name, url = url) {
                posterUrl = spans[0].selectFirst("> img")?.attr("src")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url.replace("http://", "https://")).document

        val info =
            document.select("div.novel-detail-body") //div.novel-details > div.novel-detail-item >
        val names = document.select("div.novel-detail-header").map { t -> t.text() }

        // 0 = Type (ex Web Novel)
        // 1 = Genre
        // 2 = Tags
        // 3 = Language
        // 4 = Author(s)
        // 5 = Artist(s)
        // 6 = Year
        // 7 = Status
        // 8 = Description
        // 9 = Alternative Names
        // 10 = You May Also Like
        // 11 = Total Views
        // 12 = Rating (10 POINT SYSTEM)
        // 13 = Latest Chapters
        fun getIndex(name: String): Element { // Bruh, has to do this because sometimes it varies between 14 and 15 elements
            return info[names.indexOf(name)]
        }

        val name = document.selectFirst("div.block-title")?.text() ?: return null


        val data: ArrayList<ChapterData> = ArrayList()
        val panels = document.select("div.panel")
        for (p in panels) {
            var pName = p.select("> div.panel-heading > h4.panel-title > a").text()
            pName = if (pName == "Chapters") "" else "$pName • "

            val chapterHeaders =
                p.select("> div.panel-collapse > div.panel-body > div.tab-content > div.tab-pane > ul.chapter-chs > li > a")
            for (c in chapterHeaders) {
                val cName = c.text()
                val cUrl = c?.attr("href") ?: continue
                var rName = cName
                    .replace("CH ([0-9]*)".toRegex(), "Chapter $1")
                    .replace("CH ", "")

                rName = when (rName) {
                    "Pr" -> "Prologue"
                    "Ep" -> "Epilogue"
                    else -> rName
                }

                data.add(ChapterData(pName + rName, fixUrl(cUrl), null, null))
            }
        }

        return newStreamResponse(url = url, name = name, data = data) {
            posterUrl = fixUrlNull(document.selectFirst("div.novel-cover > a > img")?.attr("src"))
            status = when (getIndex("Status").text()) {
                "Ongoing" -> STATUS_ONGOING
                "Completed" -> STATUS_COMPLETE
                else -> STATUS_NULL
            }
            synopsis =
                getIndex("Description").select("> p").joinToString(separator = "\n\n") { it.text() }
            rating = (getIndex("Rating").text().toFloat() * 100).toInt()
            author = getIndex("Author(s)").selectFirst("> ul > li")?.text()
            if (author == "N/A") author = null
            tags = getIndex("Genre").select("ul > li > a").map { it.text() }
            views = getIndex("Total Views").text().replace(",", "").replace(".", "").toInt()
        }
    }
}