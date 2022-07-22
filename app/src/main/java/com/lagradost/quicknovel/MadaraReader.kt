package com.lagradost.quicknovel

import java.util.*

abstract class MadaraReader : MainAPI() {
    override val name = ""
    override val mainUrl = ""
    override val iconId = R.drawable.ic_meionovel
    override val lang = "id"
    override val hasMainPage = true
    override val iconBackgroundId = R.color.lightItemBackground
    open val novelGenre: String = "novel-genre"
    open val novelTag: String = "novel-tag"
    open val covelAttr: String = "data-src"
    open val novelPath: String = "novel"

    override val mainCategories: List<Pair<String, String>> = listOf(
        Pair("All", ""),
        Pair("Novel Tamat", "tamat"),
        Pair("Novel Korea", "novel-korea"),
        Pair("Novel China", "novel-china"),
        Pair("Novel Jepang", "novel-jepang"),
        Pair("Novel HTL (Human Translate)", "htl"),
    )

    override val tags: List<Pair<String, String>> = listOf(
        Pair("All", ""),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Romance", "romance"),
        Pair("Comedy", "comedy"),
        Pair("Drama", "drama"),
        Pair("Shounen", "shounen"),
        Pair("School Life", "school-life"),
        Pair("Shoujo", "shoujo"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Josei", "josei"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Mystery", "mystery"),
        Pair("One shot", "one-shot"),
        Pair("Psychological", "psychological"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen Ai", "shounen-ai"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Smut", "smut"),
        Pair("Sports", "sports"),
        Pair("Supernatural", "supernatural"),
    )

    override val orderBys: List<Pair<String, String>> = listOf(
        Pair("Nothing", ""),
        Pair("New", "new-manga"),
        Pair("Most Views", "views"),
        Pair("Trending", "trending"),
        Pair("Rating", "rating"),
        Pair("A-Z", "alphabet"),
        Pair("Latest", "latest"),
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val cek: Set<String?> = setOf(null, "")
        val order: String = when {
            mainCategory !in cek -> "$novelTag/$mainCategory"
            tag !in cek -> "$novelGenre/$tag"
            else -> novelPath
        }

        val url = mainUrl.toUrlBuilderSafe()
            .addPath(order)
            .ifCase(page > 1) { addPath("page", page.toString()) }
            ?.ifCase(orderBy !in cek) { add("m_orderby", "$orderBy") }
            .toString()

        val headers = jConnect(url)?.select("div.page-item-detail")
        if (headers == null || headers.size <= 0) {
            return HeadMainPageResponse(url, listOf())
        }

        val returnValue = headers
            .mapNotNull {
                val imageHeader = it.selectFirst("div.item-thumb > a")
                val cName = imageHeader?.attr("title") ?: return@mapNotNull null
                val cUrl = imageHeader.attr("href") ?: return@mapNotNull null
                val posterUrl = imageHeader.selectFirst("> img")?.attr(covelAttr) ?: ""
                val sum = it.selectFirst("div.item-summary")
                val rating = sum?.selectFirst("> div.rating > div.post-total-rating > span.score")
                    ?.text()
                    ?.toRate()
                val latestChap =
                    sum?.selectFirst("> div.list-chapter > div.chapter-item > span > a")?.text()
                SearchResponse(cName, cUrl, posterUrl, rating, latestChap, this.name)
            }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val res = jConnect(url)!!.selectFirst("div.text-left")
        if (res == null || res.html() == "") return null
        return res.let { adv ->
            adv.select("p:has(a)").forEach { it.remove() }
            adv.html()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val headers = jConnect("$mainUrl/?s=$query&post_type=wp-manga")
            ?.select("div.c-tabs-item__content")
        if (headers == null || headers.size <= 0) {
            return listOf()
        }
        return headers
            .mapNotNull {
                // val head = it.selectFirst("> div > div.tab-summary")
                val title = it.selectFirst("div.post-title > h3 > a")
                val name = title?.text() ?: return@mapNotNull null
                val url = title.attr("href") ?: return@mapNotNull null
                val posterUrl = it.selectFirst("div.tab-thumb > a > img")?.attr(covelAttr) ?: ""
                val meta = it.selectFirst("div.tab-meta")
                val rating =
                    meta?.selectFirst("div.rating > div.post-total-rating > span.total_votes")
                        ?.text()?.toRate()
                val latestChapter = meta?.selectFirst("div.latest-chap > span.chapter > a")?.text()
                SearchResponse(name, url, posterUrl, rating, latestChapter, this.name)
            }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = jConnect(url)
        return LoadResponse(
            url = url,
            name = doc?.selectFirst("div.post-title > h1")
                ?.text()?.clean() ?: "",
            author = doc?.selectFirst(".author-content > a")?.text() ?: "",
            posterUrl = doc?.select("div.summary_image > a > img")?.attr(covelAttr) ?: "",
            tags = doc?.select("div.genres-content > a")?.mapNotNull { it?.text()?.clean() },
            synopsis = doc?.select("div.summary__content")?.text()?.synopsis() ?: "",
            data = jConnect("${url}ajax/chapters/", method = "POST")
                ?.select(".wp-manga-chapter > a[href]")
                ?.mapNotNull {
                    ChapterData(
                        name = it?.selectFirst("a")?.text()?.clean() ?: "",
                        url = it?.selectFirst("a")?.attr("href") ?: "",
                        dateOfRelease = it.selectFirst("span > i")?.text(),
                        views = 0
                    )
                }
                ?.reversed() ?: listOf(),
            rating = doc?.selectFirst("span#averagerate")?.text()?.toRate(),
            peopleVoted = doc?.selectFirst("span#countrate")?.text()?.toVote(),
            views = null,
            status = doc?.select(".post-content_item:contains(Status) > .summary-content")
                ?.text()?.toStatus(),
        )
    }
}
