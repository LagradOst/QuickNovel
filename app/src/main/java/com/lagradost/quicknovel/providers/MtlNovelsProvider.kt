package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.network.CloudflareKiller
import org.jsoup.Jsoup

class MtlNovelProvider : MainAPI() {
    override val name = "MtlNovel"
    override val mainUrl = "https://www.mtlnovel.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_mtlnovel

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", ""),
        Pair("Adult", "adult"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Erciyuan", "erciyuan"),
        Pair("Fan-Fiction", "fan-fiction"),
        Pair("Fantasy", "fantasy"),
        Pair("Game", "game"),
        Pair("Gender Bender", "Gender-Bender"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Josei", "josei"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Military", "military"),
        Pair("Mystery", "mystery"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School Life", "school-life"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen", "shounen"),
        Pair("Shounen Ai", "shounen-ai"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Smut", "smut"),
        Pair("Sports", "sports"),
        Pair("Supernatural", "supernatural"),
        Pair("Tragedy", "tragedy"),
        Pair("Two-dimensional", "two-dimensional"),
        Pair("Urban Life", "urban-life"),
        Pair("Wuxia", "wuxia"),
        Pair("Xianxia", "xianxia"),
        Pair("Xuanhuan", "xuanhuan"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
    )
    val interceptor = CloudflareKiller()

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/alltime-rank/page/$page" else "$mainUrl/genre/$tag/page/$page"


        val document = app.get(url, interceptor = interceptor).document

        val headers = document.select("div.box")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val name =
                h?.selectFirst("a")?.attr("aria-label")?.substringBeforeLast("Cover") ?: continue
            val cUrl = h.selectFirst("a")?.attr("href") ?: throw ErrorLoadingException()
            val posterUrl = h.selectFirst("amp-img amp-img")?.attr("src")
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
        return app.get(url, interceptor = interceptor).document.selectFirst("div.par")?.html()
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val response =
            SearchResults.fromJson(
                app.get(
                    "$mainUrl/wp-admin/admin-ajax.php?action=autosuggest&q=$query",
                    interceptor = interceptor
                ).text
            )
        return response.items?.first()?.results?.mapNotNull {
            SearchResponse(
                name = Jsoup.parse(it.title ?: return@mapNotNull null).text(),
                url = it.permalink ?: return@mapNotNull null,
                it.thumbnail,
                null,
                null,
                name
            )
        }!!
    }


    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val name = document.selectFirst("h1.entry-title")?.text() ?: return null

        val author = document.selectFirst("#author")?.text()

        val posterUrl = document.select("div.nov-head amp-img amp-img").attr("src")

        val tags = document.select("#currentgen > a").map {
            it.text()
        }

        val synopsis = document.selectFirst("div.desc")?.text()

        val data: ArrayList<ChapterData> = ArrayList()
        val chapters = app.get(
            "$url/chapter-list/",
            interceptor = interceptor
        ).document.select("div.ch-list a").reversed()
        for (c in chapters) {
            val href = c?.attr("href") ?: continue
            val cName = c.text()
            data.add(ChapterData(cName, href, null, null))

        }

        val rating =
            document.selectFirst("span.rating-info")?.selectFirst("strong")?.text()?.toFloat()
                ?.times(200)?.toInt()
        val peopleVoted = "\\((.+) re".toRegex()
            .find(document.selectFirst("span.rating-info")?.text().toString())?.groupValues?.last()
            ?.toInt()


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
            null,
        )
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