package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.addPath
import com.lagradost.quicknovel.clean
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.ifCase
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.synopsis
import com.lagradost.quicknovel.toChapters
import com.lagradost.quicknovel.toRate
import com.lagradost.quicknovel.toUrlBuilderSafe

abstract class WPReader : MainAPI() {
    override val name = ""
    override val mainUrl = ""
    override val lang = "id"
    override val iconId = R.drawable.ic_meionovel
    override val hasMainPage = true
    override val iconBackgroundId = R.color.lightItemBackground
    override val tags = listOf(
        "All" to "",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "China" to "china",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Fantasy" to "fantasy",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Jepang" to "jepang",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mystery" to "mystery",
        "Original (Inggris)" to "original-inggris",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Seinen Xuanhuan" to "seinen-xuanhuan",
        "Shounen" to "shounen",
        "Slice of Life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
    )
    /*
    open override val orderBys: List<Pair<String, String>> = listOf(
        "Latest Update" to  "update",
        "Most Views" to  "popular",
        "Rating" to  "rating",
        "A-Z" to  "title",
        "Latest Add" to  "latest",
    )
    */
    // open val country: List<String> = listOf("jepang", "china", "korea", "unknown",)

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url = mainUrl
            .toUrlBuilderSafe()
            .ifCase(tag != "") { addPath("genre", "$tag") }
            .ifCase(page > 1) { addPath("page", page.toString()) }
            .toString()

        val res = app.get(url).document
            .select(if (tag == "") ".flexbox3-content > a" else ".flexbox2-content > a")
            .mapNotNull { element ->
                newSearchResponse(
                    name = element.attr("title") ?: return@mapNotNull null,
                    url = element.attr("href")
                ) {
                    posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
                    rating = if (tag == "") element.selectFirst(".score")?.text()
                        ?.toRate() else null
                    latestChapter = if (tag == "") element.selectFirst("div.season")?.text()
                        ?.toChapters() else null
                }
            }

        return HeadMainPageResponse(url, res)
    }

    override suspend fun loadHtml(url: String): String? {
        val con = app.get(url).document
        val res =
            con.selectFirst("#content") ?: con.selectFirst(".mn-novel-chapter-content-body") ?: con.selectFirst(".reader-area")
        return res.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"

        return app.get(url).document
            .select("div.flexbox2-content > a")
            .mapNotNull { element ->
                newSearchResponse(
                    name = element.attr("title") ?: return@mapNotNull null,
                    url = element.attr("href") ?: return@mapNotNull null
                ) {
                    posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
                    rating = element.selectFirst(".score")?.text()?.toRate()
                    latestChapter = element.selectFirst("div.season")?.text()?.toChapters()
                }
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val data = doc.select("div.flexch-infoz > a")
            .mapNotNull { dat ->
                newChapterData(name = dat.attr("title").clean(), url = dat.attr("href").clean()) {
                    dateOfRelease = dat.selectFirst("span.date")?.text()?.clean() ?: ""
                }
            }.reversed()

        return newStreamResponse(
            url = url,
            name = doc.selectFirst(".series-titlex > h2")?.text()?.clean()
                ?: throw ErrorLoadingException("No name"),
            data = data
        ) {
            author = doc.selectFirst("li:contains(Author)")
                ?.selectFirst("span")?.text()?.clean()
            posterUrl = doc.selectFirst("div.series-thumb img")
                ?.attr("src")
            rating = doc.selectFirst("span[itemprop=ratingValue]")?.text()?.toRate()

            synopsis = doc.selectFirst(".series-synops")?.text()?.synopsis() ?: ""
            tags = doc.selectFirst("div.series-genres")?.select("a")
                ?.mapNotNull { tag -> tag.text().clean() }
            setStatus(doc.selectFirst("span.status")?.text())
        }
    }
}
