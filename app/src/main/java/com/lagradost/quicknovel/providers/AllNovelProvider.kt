package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup

open class AllNovelProvider : MainAPI() {
    override val name = "AllNovel"
    override val mainUrl = "https://allnovel.org"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_allnovel

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        "All" to "All",
        "Shounen" to "Shounen",
        "Harem" to "Harem",
        "Comedy" to "Comedy",
        "Martial Arts" to "Martial Arts",
        "School Life" to "School Life",
        "Mystery" to "Mystery",
        "Shoujo" to "Shoujo",
        "Romance" to "Romance",
        "Sci-fi" to "Sci-fi",
        "Gender Bender" to "Gender Bender",
        "Mature" to "Mature",
        "Fantasy" to "Fantasy",
        "Horror" to "Horror",
        "Drama" to "Drama",
        "Tragedy" to "Tragedy",
        "Supernatural" to "Supernatural",
        "Ecchi" to "Ecchi",
        "Xuanhuan" to "Xuanhuan",
        "Adventure" to "Adventure",
        "Action" to "Action",
        "Psychological" to "Psychological",
        "Xianxia" to "Xianxia",
        "Wuxia" to "Wuxia",
        "Historical" to "Historical",
        "Slice of Life" to "Slice of Life",
        "Seinen" to "Seinen",
        "Lolicon" to "Lolicon",
        "Adult" to "Adult",
        "Josei" to "Josei",
        "Sports" to "Sports",
        "Smut" to "Smut",
        "Mecha" to "Mecha",
        "Yaoi" to "Yaoi",
        "Shounen Ai" to "Shounen Ai",
        "History" to "History",
        "Reincarnation" to "Reincarnation",
        "Martial" to "Martial",
        "Game" to "Game",
        "Eastern" to "Eastern",
        "FantasyHarem" to "FantasyHarem",
        "Yuri" to "Yuri",
        "Magical Realism" to "Magical Realism",
        "Isekai" to "Isekai",
        "Supernatural Source:Explore" to "Supernatural Source:Explore",
        "Video Games" to "Video Games",
        "Contemporary Romance" to "Contemporary Romance",
        "invayne" to "invayne",
        "LitRPG" to "LitRPG",
        "LGBT" to "LGBT",
        "Comedy" to "Comedy",
        "Drama" to "Drama",
        "Shounen+Ai" to "Shounen+Ai",
        "Supernatural" to "Supernatural",
        "Shoujo Ai" to "Shoujo Ai",
        "Supernatura" to "Supernatura",
        "Canopy" to "Canopy"
    )

    override val orderBys = listOf(
        "Genre" to "",
        "Latest Release" to "latest-release-novel",
        "Hot Novel" to "hot-novel",
        "Completed Novel" to "completed-novel",
        "Most Popular" to "most-popular",
    )


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (orderBy == "" && tag != "All") "$mainUrl/genre/$tag?page=$page" else "$mainUrl/${if (orderBy.isNullOrBlank()) "hot-novel" else orderBy}?page=$page"
        val document = app.get(url).document

        return HeadMainPageResponse(
            url,
            list = document.select("div.list>div.row").mapNotNull { element ->
                val a =
                    element.selectFirst("div > div > h3.truyen-title > a") ?: return@mapNotNull null
                SearchResponse(
                    name = a.text(),
                    url = fixUrlNull(a.attr("href")) ?: return@mapNotNull null,
                    fixUrlNull(element.selectFirst("div > div > img")?.attr("src")),
                    null,
                    null,
                    this.name
                )
            })
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        return document.selectFirst("#chapter-content")?.html()?.replace(
            " If you find any errors ( broken links, non-standard content, etc.. ), Please let us know &lt; report chapter &gt; so we can fix it as soon as possible.",
            " "
        )?.replace("[Updated from F r e e w e b n o v e l. c o m]", "")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document =
            app.get("$mainUrl/search?keyword=$query").document // AJAX, MIGHT ADD QUICK SEARCH

        return document.select("#list-page>.archive>.list>.row").mapNotNull { h ->
            val title = h.selectFirst(">div>div>.truyen-title>a") ?: return@mapNotNull null
            newSearchResponse(title.text(), title.attr("href") ?: return@mapNotNull null) {
                posterUrl = fixUrlNull(h.selectFirst(">div>div>img")?.attr("href"))
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val name =
            document.selectFirst("h3.title")?.text() ?: throw ErrorLoadingException("invalid name")

        val dataNovelId = document.select("#rating").attr("data-novel-id")

        val chapterData = app.get("$mainUrl/ajax-chapter-option?novelId=$dataNovelId").document
        val parsed = chapterData.select("select > option")

        val data = parsed.mapNotNull { c ->
            val cUrl = c?.attr("value") ?: return@mapNotNull null
            val cName = c.text().ifEmpty {
                "chapter $c"
            }
            newChapterData(cName, cUrl)
        }

        return newStreamResponse(name, url, data) {
            tags = document.select("div.info > div:nth-child(3) a").map {
                it.text()
            }
            author = document.selectFirst("div.info > div:nth-child(1) > a")?.text()
            posterUrl = fixUrlNull(document.select("div.book > img").attr("src"))
            synopsis = document.selectFirst("div.desc-text")?.text()
            peopleVoted =
                document.selectFirst(" div.small > em > strong:nth-child(3) > span")?.text()
                    ?.toIntOrNull() ?: 0
            rating = document.selectFirst(" div.small > em > strong:nth-child(1) > span")?.text()
                ?.toIntOrNull() ?: 0

            this.status =
                when (document.selectFirst("div.info > div:nth-child(5) > a")?.selectFirst("a")
                    ?.text()) {
                    "Ongoing" -> STATUS_ONGOING
                    "Completed" -> STATUS_COMPLETE
                    else -> STATUS_NULL
                }
        }
    }
}
