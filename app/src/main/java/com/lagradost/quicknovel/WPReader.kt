package com.lagradost.quicknovel

import java.util.*

abstract class WPReader : MainAPI() {
    override val name = ""
    override val mainUrl = ""
    override val lang = "id"
    override val iconId = R.drawable.ic_meionovel
    override val hasMainPage = true
    override val iconBackgroundId = R.color.lightItemBackground
    override val tags = listOf(
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
    /*
    open override val orderBys: List<Pair<String, String>> = listOf(
        Pair("Latest Update", "update"),
        Pair("Most Views", "popular"),
        Pair("Rating", "rating"),
        Pair("A-Z", "title"),
        Pair("Latest Add", "latest"),
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

        val res = jConnect(url)
            ?.select(if (tag == "") ".flexbox3-content > a" else ".flexbox2-content > a")
            ?.mapNotNull {
                SearchResponse(
                    name = it?.attr("title") ?: "",
                    url = it?.attr("href") ?: "",
                    posterUrl = it?.selectFirst("img")?.attr("src") ?: "",
                    rating = if (tag == "") it?.selectFirst(".score")?.text()?.toRate() else null,
                    latestChapter = if (tag == "") it?.selectFirst("div.season")?.text()?.toChapters() else null,
                    apiName = name
                )
            }

        return HeadMainPageResponse(url, res ?: ArrayList())
    }

    override suspend fun loadHtml(url: String): String? {
        val con = jConnect(url)
        val res = con?.selectFirst(".mn-novel-chapter-content-body") ?: con?.selectFirst(".reader-area")
        return res?.let { adv ->
            adv.select("p")?.filter { it -> !it.hasText() }?.forEach { it.remove() }
            adv.outerHtml()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = mainUrl.toUrlBuilderSafe().add("s" to query)
        return jConnect(url = url.toString())
            ?.select("div.flexbox2-content > a")
            ?.mapNotNull {
                SearchResponse(
                    name = it?.attr("title") ?: "",
                    url = it?.attr("href") ?: "",
                    posterUrl = it?.selectFirst("img")?.attr("src") ?: "",
                    rating = it?.selectFirst(".score")?.text()?.toRate(),
                    latestChapter = it?.selectFirst("div.season")?.text()?.toChapters(),
                    apiName = name
                )
            } ?: ArrayList()
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = jConnect(url)
        return LoadResponse(
            url = url,
            name = doc?.selectFirst(".series-titlex > h2")?.text()?.clean() ?: "",
            data = doc?.select("div.flexch-infoz > a")
                ?.mapNotNull { dat ->
                    ChapterData(
                        name = dat.attr("title").clean() ?: "",
                        url = dat.attr("href").clean() ?: "",
                        dateOfRelease = dat.selectFirst("span.date")?.text()?.clean() ?: "",
                        views = 0,
                    )
                }?.reversed() ?: listOf(ChapterData("", "", null, null)),
            author = doc?.selectFirst("li:contains(Author)")
                ?.selectFirst("span")?.text()?.clean() ?: "",
            posterUrl = doc?.selectFirst("div.series-thumb img")
                ?.attr("src") ?: "",
            rating = doc?.selectFirst("span[itemprop=ratingValue]")?.text()?.toRate(),
            peopleVoted = 0,
            views = 0,
            synopsis = doc?.selectFirst(".series-synops")?.text()?.synopsis() ?: "",
            tags = doc?.selectFirst("div.series-genres")?.select("a")
                ?.mapNotNull { tag -> tag?.text()?.clean() },
            status = doc?.selectFirst("span.status")?.text()?.toStatus(),
        )
    }
}
