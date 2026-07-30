package com.lagradost.quicknovel.providers

import android.net.Uri
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

class AsianNovel  : MainAPI() {

    override val name = "Asian Novel"
    override val mainUrl = "https://www.asianovel.net"
    override val iconId = R.drawable.icon_asianovel
    override val lang = "en"
    override val hasMainPage = true
    override val orderBys =
        listOf(
            "Updated" to "modified",
            "Published" to "date",
            "Title" to "title",
            "Comments" to "comment",
            "Words" to "words",
        )
    override val tags = listOf(
        "All" to "",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "BL" to "boylove",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Fantasy" to "fantasy",
        "Gender Bender" to "gender-bender",
        "GL&Lesbian" to "girllove",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Post-Editing" to "post-editing",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
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
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    )


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val range = if (page == 1) {
            1..2
        } else {
            val actualPage = page + 1
            actualPage..actualPage
        }
        var url = ""
        val novels = mutableListOf<SearchResponse>()
        range.forEach{ r->
            url = if(tag.isNullOrEmpty())
                "$mainUrl/stories/page/$r/?order=desc&orderby=$orderBy"
            else "$mainUrl/genre/xuanhuan/page/$r/"
            val document = app.get(url).document
            document.select("script, style, iframe, svg, noscript, link, meta, head, div.asian-ads-bottom-content").remove()
            novels.addAll(document.select("section > ul > li").mapNotNull { card ->
                val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val title = card.selectFirst("h3")?.text() ?: return@mapNotNull null

                newSearchResponse(title, href) {
                    posterUrl = card.selectFirst("img")?.attr("src")
                }
            })
        }

        return HeadMainPageResponse(url, novels)
    }
    private fun getRelated(document: Document): List<SearchResponse>? {
        return document.select("div.yarpp-thumbnails-horizontal > a.yarpp-thumbnail").mapNotNull { element ->
            val href = element.attr("href")
            val title = element.selectFirst("div.yarpp-thumbnail-title")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(
                    element.selectFirst("img")?.attr("src")
                )
            }
        }
    }
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        document.select("script, style, iframe, svg, noscript, link, meta, head").remove()
        val title = document.selectFirst("h1.story__identity-title")?.text() ?: throw ErrorLoadingException("Title not found")
        val chapters = document.select("div.chapter-group > ol > li").mapNotNull { li ->
            val a = li.selectFirst("a.chapter-group__list-item-link") ?: return@mapNotNull null
            newChapterData(a.text(), a.attr("href")) {
                dateOfRelease = li.selectFirst("div.pseudo-separator")?.text()
            }
        }
        return newStreamResponse(title, url, chapters) {
            this.posterUrl = document.selectFirst("img.wp-post-image")?.attr("src")
            this.synopsis = document.selectFirst("section.story__summary")?.html()
            this.author = document.selectFirst("div.story__identity-meta > a.author")?.text()
            this.tags = document.select("div#edit-genre > a")?.map{ it.text() }
            related = getRelated(document)
        }
    }


    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        document.select("script, style, iframe, svg, noscript, link, meta, head, div.asian-ads-bottom-content").remove()
        return document.selectFirst("section#chapter-content > div")?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${Uri.encode(query.trim()).replace("%20", "+")}&post_type=any&sentence=0&orderby=modified&order=desc&age_rating=Any&story_status=Any&miw=0&maw=0&genres=&tags=&author_name=&ex_genres=&ex_tags="
        val document = app.get(url).document

        return document.select("section.search-results__content > ul > li.card").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("h3")?.text() ?: return@mapNotNull null

            newSearchResponse(title, href) {
                posterUrl = card.selectFirst("img")?.attr("src")
            }
        }
    }
}