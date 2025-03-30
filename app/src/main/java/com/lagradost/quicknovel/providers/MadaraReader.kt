package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.add
import com.lagradost.quicknovel.addPath
import com.lagradost.quicknovel.clean
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.ifCase
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.synopsis
import com.lagradost.quicknovel.toRate
import com.lagradost.quicknovel.toUrlBuilderSafe
import com.lagradost.quicknovel.toVote

/*
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
        "All" to "",
        "Novel Tamat" to "tamat",
        "Novel Korea" to "novel-korea",
        "Novel China" to "novel-china",
        "Novel Jepang" to "novel-jepang",
        "Novel HTL (Human Translate)" to "htl",
    )

    override val tags: List<Pair<String, String>> = listOf(
        "All" to "",
        "Action" to "action",
        "Adventure" to "adventure",
        "Romance" to "romance",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Shounen" to "shounen",
        "School Life" to "school-life",
        "Shoujo" to "shoujo",
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
        "One shot" to "one-shot",
        "Psychological" to "psychological",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo Ai" to "shoujo-ai",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
    )

    override val orderBys: List<Pair<String, String>> = listOf(
        "Nothing" to "",
        "New" to "new-manga",
        "Most Views" to "views",
        "Trending" to "trending",
        "Rating" to "rating",
        "A-Z" to "alphabet",
        "Latest" to "latest",
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
            .ifCase(orderBy !in cek) { add("m_orderby", "$orderBy") }
            .toString()

        val headers = app.get(url).document.select("div.page-item-detail")

        val returnValue = headers
            .mapNotNull {
                val imageHeader = it.selectFirst("div.item-thumb > a")
                val cName = imageHeader?.attr("title") ?: return@mapNotNull null
                val cUrl = imageHeader.attr("href") ?: return@mapNotNull null

                newSearchResponse(name = cName, url = cUrl) {
                    val sum = it.selectFirst("div.item-summary")

                    rating = sum?.selectFirst("> div.rating > div.post-total-rating > span.score")
                        ?.text()
                        ?.toRate()
                    posterUrl = fixUrlNull(imageHeader.selectFirst("> img")?.attr(covelAttr))
                    latestChapter =
                        sum?.selectFirst("> div.list-chapter > div.chapter-item > span > a")?.text()
                }
            }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val res = app.get(url).document.selectFirst("div.text-left")
        if (res == null || res.html() == "") return null
        return res.let { adv ->
            adv.select("p:has(a)").forEach { it.remove() }
            adv.html()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val headers = app.get("$mainUrl/?s=$query&post_type=wp-manga").document
            .select("div.c-tabs-item__content")

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
        val doc = app.get(url).document
        val data = app.post("${url}ajax/chapters/").document
            .select(".wp-manga-chapter > a[href]")
            .mapNotNull {
                ChapterData(
                    name = it?.selectFirst("a")?.text()?.clean() ?: "",
                    url = it?.selectFirst("a")?.attr("href") ?: "",
                    dateOfRelease = it.selectFirst("span > i")?.text(),
                    views = 0
                )
            }
            .reversed()
        return newStreamResponse(
            url = url, name = doc.selectFirst("div.post-title > h1")
                ?.text()?.clean() ?: throw ErrorLoadingException("Bad name"), data = data
        ) {
            author = doc.selectFirst(".author-content > a")?.text()
            posterUrl = doc.select("div.summary_image > a > img").attr(covelAttr)
            tags = doc.select("div.genres-content > a").mapNotNull { it?.text()?.clean() }
            synopsis = doc.select("div.summary__content").text().synopsis()

            rating = doc.selectFirst("span#averagerate")?.text()?.toRate()
            peopleVoted = doc.selectFirst("span#countrate")?.text()?.toVote()
            status = doc.select(".post-content_item:contains(Status) > .summary-content")
                .text().toStatus()
        }
    }
}

*/