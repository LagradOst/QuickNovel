package com.lagradost.quicknovel.providers
import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import org.jsoup.nodes.Document

class LuxonScansProvider:  MainAPI() {
    override val name = "Luxon Scan"
    override val mainUrl = "https://lunoxscans.com"
    override val iconId = R.drawable.icon_luxoscan
    override val iconBackgroundId = R.color.black
    override val hasMainPage = true
    override val rateLimitTime = 800L


    override val tags = listOf("All" to "",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Chaebol" to "chaebol",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Fantasy" to "fantasy",
        "Growth" to "growth",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Magic" to "magic",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Modern" to "modern",
        "Mystery" to "mystery",
        "Possession" to "possession",
        "Psychological" to "psychological",
        "Regression" to "regression",
        "Reincarnation" to "reincarnation",
        "Revenge" to "revenge",
        "Reverse" to "reverse",
        "Romance" to "romance",
        "Royalty" to "royalty",
        "s" to "s",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shounen" to "shounen",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Warrior" to "warrior",
        "Wuxia" to "wuxia",
        "x" to "x"
    )

    override val orderBys = listOf(
            "Default" to "",
            "New" to "new-manga",
            "Most Views" to "views",
            "Trending" to "trending",
            "Rating" to "rating",
            "A-Z" to "alphabet",
            "Latest" to "latest"
    )


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val order = when (tag) {
            "" -> "all-series"
            else -> "series-genre/$tag"
        }
        val url =
            "$mainUrl/$order/page/$page/${if (orderBy == "") "" else "?m_orderby=$orderBy"}"

        val document = app.get(url).document

        val returnValue = document.select("div.page-item-detail").mapNotNull { h ->
            val name = h.selectFirst("h3")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = name,
                url = h.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(h.selectFirst("img")?.attr("src"))
            }
        }

        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: throw ErrorLoadingException("Invalid Name")
        val chapters = document.select("div.lunox-chapters-container > div > a:not(.premium)").mapNotNull { li ->
            val name = li.attr("data-name") ?: return@mapNotNull null
            val url = li.attr("href") ?: return@mapNotNull null
            newChapterData(name, url)
        }.reversed()
        return newStreamResponse(title,url, chapters) {
            this.posterUrl = document.selectFirst("div.summary_image img")?.attr("src")
            this.synopsis = document.select("div.summary__content").html()

            author = document.selectFirst("div.summary-content > div.author-content")?.text()

            this.tags = document.select("div.genres-content > a").mapNotNull {
                it.text().trim().takeIf { text ->  !text.isEmpty() }
            }

            rating = ((document.selectFirst("span.score")?.text()?.toFloatOrNull()
                ?: 0f) * 200).toInt()
            peopleVoted = document.selectFirst("div.vote-details")
                ?.text()
                ?.substringAfterLast("of ")
                ?.substringBeforeLast(" total")
                ?.toIntOrNull()

            // Turn K to thousands, 9.3k -> 2 zeroes | 95K -> 3 zeroes
            val totViews = document.selectFirst("div.summary_content_wrap > div > div > div:nth-child(4) > div.summary-content")
                ?.text()
                ?.substringAfterLast("has ")
                ?.substringBeforeLast(" views")
            views = totViews
                    ?.replace("K", if (totViews.contains(".")) "00" else "000")
                    ?.replace(".", "")
                    ?.toIntOrNull() ?: 0

            related = getRelated(document)
        }
    }

    fun getRelated(dc: Document): List<SearchResponse>{
        return dc.select("div.related-manga > div.col-md-3").mapNotNull { element ->
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("h5")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val res = document.selectFirst("div.text-left")
        return res?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=${Uri.encode(query.trim()).replace("%20", "+")}&post_type=wp-manga").document
        return document.select("div.page-item-detail").mapNotNull { h ->
            val name = h.selectFirst("h3")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = name,
                url = h.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(h.selectFirst("img")?.attr("src"))
            }
        }
    }
}