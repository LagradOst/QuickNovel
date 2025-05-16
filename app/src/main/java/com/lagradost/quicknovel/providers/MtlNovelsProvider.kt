package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import org.jsoup.Jsoup

class MtlNovelProvider : MainAPI() {
    override val name = "MtlNovel"
    override val mainUrl = "https://www.mtlnovels.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_mtlnovel

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    fun fixImage(url: String?): String? {
        return url?.replace(
            "https://www.mtlnovel.net/",
            "https://www.mtlnovels.com/wp-content/uploads/"
        )
    }

    override val tags = listOf(
        "All" to "",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Erciyuan" to "erciyuan",
        "Fan-Fiction" to "fan-fiction",
        "Fantasy" to "fantasy",
        "Game" to "game",
        "Gender Bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Military" to "military",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shoujo Ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Two-dimensional" to "two-dimensional-novel",
        "Urban Life" to "urban-fiction",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/alltime-rank/page/$page" else "$mainUrl/genre/$tag/page/$page"
        val document = app.get(url).document
        val headers = document.select("div.box")

        val returnValue = headers.mapNotNull { h ->
            val name =
                h.selectFirst("a")?.attr("aria-label")?.substringBeforeLast("Cover")
                    ?: return@mapNotNull null
            val cUrl = h.selectFirst("a")?.attr("href") ?: throw ErrorLoadingException()
            newSearchResponse(
                name = name,
                url = cUrl,
            ) {
                posterUrl = fixImage(fixUrlNull(h.selectFirst("amp-img amp-img")?.attr("src")))
            }
        }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        return app.get(url).document.selectFirst("div.par")?.html()
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val response =
            SearchResults.fromJson(
                app.get(
                    "$mainUrl/wp-admin/admin-ajax.php?action=autosuggest&q=$query"
                ).text
            )
        return response.items?.first()?.results?.mapNotNull {
            newSearchResponse(
                name = Jsoup.parse(it.title ?: return@mapNotNull null).text(),
                url = it.permalink ?: return@mapNotNull null
            ) {
                posterUrl = fixImage(fixUrlNull(it.thumbnail))
            }
        }!!
    }


    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val name = document.selectFirst("h1.entry-title")?.text() ?: return null
        val data = app.get(
            "$url/chapter-list/"
        ).document.select("div.ch-list a").reversed().mapNotNull { c ->
            val href = c.attr("href") ?: return@mapNotNull null
            val cName = c.text()
            newChapterData(name = cName, url = href)
        }

        return newStreamResponse(url = url, name = name, data = data) {
            author = document.selectFirst("#author")?.text()
            posterUrl = fixImage(fixUrlNull(document.select("div.nov-head amp-img amp-img").attr("src")))
            tags = document.select("#currentgen > a").map {
                it.text()
            }
            synopsis = document.selectFirst("div.desc")?.text()
            peopleVoted = "\\((.+) re".toRegex()
                .find(
                    document.selectFirst("span.rating-info")?.text().toString()
                )?.groupValues?.last()
                ?.toInt()
            rating =
                document.selectFirst("span.rating-info")?.selectFirst("strong")?.text()?.toFloat()
                    ?.times(200)?.toInt()
        }
    }
}


private data class SearchResults(
    @get:JsonProperty("items") val items: List<Item>? = null
) {
    companion object {
        fun fromJson(json: String) = mapper.readValue<SearchResults>(json)
    }
}

private data class Item(
    @get:JsonProperty("query") val query: String? = null,
    @get:JsonProperty("results") val results: List<Result>? = null
)

private data class Result(
    @get:JsonProperty("title") val title: String? = null,
    @get:JsonProperty("permalink") val permalink: String? = null,
    @get:JsonProperty("thumbnail") val thumbnail: String? = null,
    @get:JsonProperty("shortname") val shortname: String? = null,
    @get:JsonProperty("cn") val cn: String? = null
)