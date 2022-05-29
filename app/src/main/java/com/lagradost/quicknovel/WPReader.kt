package com.lagradost.quicknovel

import java.util.*

abstract class WPReader : MainAPI() {
    open override val name = ""
    open override val mainUrl = ""
    open override val lang = "id"
    open override val iconId = R.drawable.big_icon_boxnovel
    open override val hasMainPage = true
    open override val iconBackgroundId = R.color.boxNovelColor
    open override val tags = listOf(
        Pair("All", ""),
        Pair("Action", "action"),
        Pair("Adult", "adult"),
        Pair("Adventure", "adventure"),
        Pair("China", "china"),
        Pair("Comedy", "comedy"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Jepang", "jepang"),
        Pair("Josei", "josei"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mystery", "mystery"),
        Pair("Original (Inggris)", "original-inggris"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School Life", "school-life"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Seinen Xuanhuan", "seinen-xuanhuan"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Smut", "smut"),
        Pair("Sports", "sports"),
        Pair("Supernatural", "supernatural"),
        Pair("Tragedy", "tragedy"),
        Pair("Xianxia", "xianxia"),
        Pair("Xuanhuan", "xuanhuan"),
    )

    open override val orderBys: List<Pair<String, String>> = listOf(
        Pair("Latest Update", "update"),
        Pair("Most Views", "popular"),
        Pair("Rating", "rating"),
        Pair("A-Z", "title"),
        Pair("Latest Add", "latest"),
    )

    open fun getUrl(
        page: Int = 1,
        title: String = "",
        genre: String = "",
        order: String = "popular"
    ): String {
        return mainUrl.toUrlBuilderSafe()
            .ifCase(genre != "") { addPath("genre", genre) }
            .ifCase(genre == "") { addPath("advanced-search") }
            .ifCase(page < 1) { addPath("page", page.toString()) }
            .add(
                "title" to title,
                "author" to "",
                "yearx" to "",
                "status" to "",
                "type" to "",
                "order" to order,
                "country[]" to "china&country[]=jepang&country[]=korea&country[]=unknown",
            )
            .toString()
    }

    open fun getSeriesList(url: String): List<SearchResponse>? {
        return jConnect(url = url)
            ?.select("div.flexbox2-content")
            ?.mapNotNull {
                SearchResponse(
                    name = it?.selectFirst("a")?.attr("title") ?: "",
                    url = it?.selectFirst("a")?.attr("href") ?: "",
                    posterUrl = it?.selectFirst("a > img")?.attr("src") ?: "",
                    rating = it?.selectFirst(".score")?.text()?.toRate(),
                    latestChapter = it?.selectFirst("div.season")?.text()?.toChapters(),
                    apiName = name
                )
            }
    }

    open override fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url = getUrl(
            page = page,
            genre = if (tag == null) "" else tag,
            order = if (orderBy == null) "" else orderBy

        )

        return HeadMainPageResponse(url, getSeriesList(url) ?: ArrayList())
    }

    open override fun loadHtml(url: String): String? {
        val res = jConnect(url)?.selectFirst("div.reader-area")
        // if (res.html() == "") return null
        // res.select("div:has(script)")?.forEach { it.remove() }
        return res?.let { adv ->
            adv?.select("div:has(script)")?.forEach { it.remove() }
            adv.html()
        }
    }

    open override fun search(query: String): List<SearchResponse> {
        return getSeriesList(getUrl(title = query)) ?: ArrayList()
    }

    open override fun load(url: String): LoadResponse {
        val doc = jConnect(url)
        return LoadResponse(
                url = url,
                name = doc?.selectFirst(".series-titlex > h2")?.text()?.clean() ?: "",
                data = doc?.select("div.flexch-infoz > a")?.mapNotNull { dat ->
                    ChapterData(
                        name = dat.attr("title")?.clean() ?: "",
                        url = dat.attr("href")?.clean() ?: "",
                        dateOfRelease = dat.selectFirst("span.date")?.text()?.clean() ?: "",
                        views = 0,
                    )
                }?.reversed(),
                author = doc?.selectFirst("li:contains(Author)")
                    ?.selectFirst("span")?.text()?.clean() ?: "",
                posterUrl = doc?.selectFirst("div.series-thumb > a")
                    ?.attr("src") ?: "",
                rating = doc?.selectFirst("span[itemprop=ratingValue]")?.text()?.toRate(),
                peopleVoted = 0,
                views = 0,
                synopsis = doc?.selectFirst(".series-synops")?.text().?synopsis() ?: "",
                tags = doc?.selectFirst("div.series-genres")?.select("a")
                    .mapNotNull { tag -> tag?.text()?.clean() },
                status = doc?.selectFirst("span.status")?.text()?.toStatus(),
            )
        
    }
}
