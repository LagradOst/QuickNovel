package com.lagradost.quicknovel.providers

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newReview
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.util.AppUtils.parseJson
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class AsianNovelProvider : MainAPI() {

    override val name = "Asian Novel"
    override val mainUrl = "https://www.asianovel.net"
    override val iconId = R.drawable.icon_asianovel
    override val lang = "en"
    override val hasMainPage = true
    override val hasReviews = true

    //idk, this solves out of memory
    override val rateLimitTime = 3000L

    override val orderBys = listOf(
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

    //I use this before Jsoup to avoid out of memory
    private fun String.cleanRawHtml(): String {
        return this
            .replace(Regex("<script.*?>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style.*?>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<svg.*?>[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<iframe.*?>[\\s\\S]*?</iframe>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<noscript.*?>[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), "")
    }

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
        range.forEach { r ->
            url = if (tag.isNullOrEmpty())
                "$mainUrl/stories/page/$r/?order=desc&orderby=$orderBy"
            else "$mainUrl/genre/xuanhuan/page/$r/"
            val res = app.get(url).text.cleanRawHtml()
            val document = Jsoup.parseBodyFragment(res)

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

    private fun getRelated(document: Document): List<SearchResponse> {
        return document.select("div.yarpp-related > div > a").mapNotNull { element ->
            newSearchResponse(
                name = element.attr("title"),
                url = element.attr("href")
            ) {
                posterUrl = fixUrlNull(
                    element.selectFirst("img")?.attr("src")
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url).text.cleanRawHtml()
        val document = Jsoup.parseBodyFragment(res)

        val postId = document.selectFirst("link[rel=shortlink]")?.attr("href")?.substringAfter("p=")
            ?: document.selectFirst("div#fictioneer-story-data")?.attr("data-post-id")
            ?: ""

        val title = document.selectFirst("h1.story__identity-title")?.text()
            ?: throw ErrorLoadingException("Title not found")

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
            this.tags = document.select("div#edit-genre > a").map { it.text() }
            this.related = getRelated(document)
            this.reviewData = postId
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val res = app.get(url).text
        // Extract content div before parsing to save memory
        val contentRegex = Regex(
            "<section id=\"chapter-content\"[^>]*>([\\s\\S]*?)</section>",
            RegexOption.IGNORE_CASE
        )
        val match = contentRegex.find(res)
        val htmlToParse = (match?.groupValues?.get(0) ?: res).cleanRawHtml()

        val document = Jsoup.parseBodyFragment(htmlToParse)
        val html = document.selectFirst("section#chapter-content > div")?.html()
            ?: document.selectFirst("section#chapter-content")?.html()

        return html
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${
            Uri.encode(query.trim()).replace("%20", "+")
        }&post_type=any&sentence=0&orderby=modified&order=desc&age_rating=Any&story_status=Any&miw=0&maw=0&genres=&tags=&author_name=&ex_genres=&ex_tags="
        val res = app.get(url).text.cleanRawHtml()
        val document = Jsoup.parseBodyFragment(res)

        val result =
            document.select("section.search-results__content > ul > li.card").mapNotNull { card ->
                val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val title = card.selectFirst("h3")?.text() ?: return@mapNotNull null

                newSearchResponse(title, href) {
                    posterUrl = card.selectFirst("img")?.attr("src")
                }
            }
        return result
    }

    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val postId = data ?: return emptyList()
        val apiUrl =
            "$mainUrl/wp-json/fictioneer/v1/get_story_comments?nonce=50aeb0e8c2&post_id=$postId&page=$page"
        val res = app.get(apiUrl).text.cleanRawHtml()
        val cleanHtml = parseJson<CommentResponse>(res).data.html ?: return emptyList()
        val document = Jsoup.parseBodyFragment(cleanHtml)

        val reviews = document.select("li.fictioneer-comment").mapNotNull { element ->
            val body =
                element.selectFirst(".fictioneer-comment__body")?.html() ?: return@mapNotNull null

            newReview(body) {
                username = element.selectFirst(".fictioneer-comment__author")?.text()
                date = element.selectFirst(".fictioneer-comment__date")?.text()
            }
        }
        return reviews
    }

    data class CommentResponse(
        @JsonProperty("data") val data: CommentData,
        @JsonProperty("success") val success: Boolean? = null
    )

    data class CommentData(
        @JsonProperty("html") val html: String?
    )
}