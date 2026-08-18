package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newReview
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup
import java.net.URLEncoder

class ScrollersPubProvider : MainAPI() {
    override val name = "ScrollersPub"
    override val mainUrl = "https://www.scrollerspub.com"
    override val iconId = R.drawable.icon_scrollerspub
    override val iconBackgroundId = R.color.black
    override val hasMainPage = true
    override val hasReviews = true

    override val mainCategories = listOf(
        "All" to "",
        "Ongoing" to "ongoing",
        "Completed" to "completed"
    )

    override val orderBys = listOf(
        "Most Popular" to "popular",
        "Latest Updates" to "updated",
        "Newest" to "new",
        "Rating" to "rating"
    )

    override val tags = listOf(
        "All" to "",
        "Fantasy" to "fantasy",
        "Action" to "action",
        "Romance" to "romance",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Transmigration" to "transmigration",
        "System" to "system",
        "Reincarnation" to "reincarnation",
        "Magic" to "magic",
        "Cultivation" to "cultivation",
        "Slice Of Life" to "slice-of-life",
        "Drama" to "drama",
        "Mystery" to "mystery",
        "Supernatural" to "supernatural",
        "Revenge" to "revenge",
        "Martial Arts" to "martial-arts",
        "Sci-Fi" to "sci-fi",
        "Survival" to "survival",
        "Academy" to "academy",
        "Harem" to "harem",
        "Historical" to "historical",
        "Psychological" to "psychological",
        "Eastern" to "eastern",
        "Superpowers" to "superpowers",
        "Monsters" to "monsters"
    )

    private fun fixPoster(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return path
        return "$mainUrl/static/${path.removePrefix("/")}"
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val limit = 20
        val offset = (page - 1) * limit

        val apiUrl =
            "$mainUrl/api/novels?search=&offset=$offset&limit=$limit&domain=&tag=$tag&sort_by=$orderBy${if (mainCategory.isNullOrEmpty()) "" else "&status=$mainCategory"}"

        val response = app.get(apiUrl).parsed<NovelListResponse>()
        val novels = response.items?.map { item ->
            newSearchResponse(
                name = item.title,
                url = "$mainUrl/novel/${item.slug}"
            ) {
                posterUrl = fixPoster(item.coverFile)
                latestChapter = item.chapterCount?.let { "$it Chapters" }
            }
        } ?: emptyList()

        return HeadMainPageResponse(apiUrl, novels)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/novels?search=${
            URLEncoder.encode(
                query,
                "UTF-8"
            )
        }&offset=0&limit=20&domain="
        val document = app.get(url).parsed<NovelListResponse>()
        return document.items?.map { item ->
            newSearchResponse(
                name = item.title,
                url = "$mainUrl/novel/${item.slug}"
            ) {
                posterUrl = fixPoster(item.coverFile)
                latestChapter = item.chapterCount?.let { "$it Chapters" }
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = url.trimEnd('/').substringAfterLast("/")
        val detailsApiUrl = "$mainUrl/api/novel/$slug"
        val details = app.get(detailsApiUrl).parsed<NovelDetailsResponse>()

        val chapterCount = details.chapterCount ?: 0
        val chapters = mutableListOf<ChapterData>()
        if (chapterCount > 0) {
            val chaptersApiUrl = "$mainUrl/api/novel/${details.id}/chapters?limit=$chapterCount"
            val chaptersResponse = app.get(chaptersApiUrl).parsed<ChapterListResponse>()

            chaptersResponse.items?.mapNotNull { item ->
                val chTitle = item.title
                val serial = item.serial ?: 0
                newChapterData(
                    name = chTitle.ifBlank { "Chapter $serial" },
                    url = "$mainUrl/read/${item.id}"
                )
            }?.let {
                chapters.addAll(it)
            }
        }

        return newStreamResponse(details.title, url, chapters) {
            posterUrl = fixPoster(details.coverFile)
            author = details.authors
            synopsis = details.synopsis?.let { Jsoup.parse(it).text() }
            setStatus(details.status)
            related = getRelated(details.id)
            reviewData = details.id
        }
    }

    private suspend fun getRelated(novelId: String): List<SearchResponse> {
        return try {
            val url = "$mainUrl/api/novel/$novelId/similar?limit=10"
            val response = app.get(url).parsed<Array<RelatedResponse>>()
            println("respuesta: ${response.contentToString()}")
            response.map { item ->
                newSearchResponse(
                    name = item.title,
                    url = "$mainUrl/novel/${item.slug}"
                ) {
                    posterUrl = fixPoster(item.coverFile)
                }
            }
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val content = document.selectFirst("#chapter-content, article, .chapter-content")
            ?: return null

        content.select(".ads, .ads-holder, script, style, #frame, [id*='ads'], [class*='ads'], div[align='center'], nav, .pagination")
            .remove()

        return content.html()
    }

    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val novelId = data ?: return emptyList()
        val limit = 20
        val offset = (page - 1) * limit
        val apiUrl = "$mainUrl/api/novel/$novelId/comments?offset=$offset&limit=$limit"

        val response = app.get(apiUrl).parsed<CommentListResponse>()
        return response.items?.mapNotNull { item ->
            newReview(
                Jsoup.parse(item.content ?: return@mapNotNull null).text().trim()
            ) {
                username = item.userName ?: "Anonymous"
                rating = item.authorScore?.times(200)
                date = item.createdAt?.toString()
            }
        } ?: emptyList()
    }

    data class NovelItem(
        @JsonProperty("slug") val slug: String,
        @JsonProperty("cover_file") val coverFile: String?,
        @JsonProperty("title") val title: String,
        @JsonProperty("chapter_count") val chapterCount: Int?
    )

    data class NovelListResponse(
        @JsonProperty("items") val items: List<NovelItem>?
    )

    data class NovelDetailsResponse(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("authors") val authors: String?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("chapter_count") val chapterCount: Int?,
        @JsonProperty("cover_file") val coverFile: String?
    )

    data class ChapterItem(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("serial") val serial: Int?
    )

    data class ChapterListResponse(
        @JsonProperty("items") val items: List<ChapterItem>?
    )

    data class RelatedResponse(
        @JsonProperty("cover_file") val coverFile: String?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("title") val title: String,
    )

    data class CommentItem(
        @JsonProperty("user_name") val userName: String?,
        @JsonProperty("content") val content: String?,
        @JsonProperty("author_score") val authorScore: Int?,
        @JsonProperty("created_at") val createdAt: Long?
    )

    data class CommentListResponse(
        @JsonProperty("items") val items: List<CommentItem>?
    )
}
