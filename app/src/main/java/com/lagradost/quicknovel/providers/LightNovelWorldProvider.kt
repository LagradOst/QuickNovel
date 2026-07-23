package com.lagradost.quicknovel.providers

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.providers.NovelFireProvider.PostsResponse
import com.lagradost.quicknovel.providers.NovelFireProvider.RelatedResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap

class LightNovelWorldProvider : MainAPI() {
    override val name = "LightNovelWorld"
    override val mainUrl = "https://lightnovelworld.org"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_lightnovelworld
    override val iconBackgroundId = R.color.colorPrimaryWhite
    override val hasReviews = true
    val novelsIdRequired = ConcurrentHashMap<String, String>()
    override val mainCategories = listOf(
        "All" to "all",
        "Ongoing" to "ongoing",
        "Completed" to "completed"
    )

    override val tags = listOf(
        "All" to "all",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Anime" to "anime",
        "Arts" to "arts",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Eastern" to "eastern",
        "Ecchi" to "ecchi",
        "Fan-Fiction" to "fan-fiction",
        "Fantasy" to "fantasy",
        "Game" to "game",
        "Gender-Bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Lgbt+" to "lgbt+",
        "Magic" to "magic",
        "Magical-Realism" to "magical-realism",
        "Manhua" to "manhua",
        "Martial-Arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Military" to "military",
        "Modern-Life" to "modern-life",
        "Movies" to "movies",
        "Mystery" to "mystery",
        "Other" to "other",
        "Psychological" to "psychological",
        "Realistic-Fiction" to "realistic-fiction",
        "Reincarnation" to "reincarnation",
        "Romance" to "romance",
        "School-Life" to "school-life",
        "Sci-Fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shoujo-Ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen-Ai" to "shounen-ai",
        "Slice-Of-Life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "System" to "system",
        "Tragedy" to "tragedy",
        "Urban" to "urban",
        "Urban-Life" to "urban-life",
        "Video-Games" to "video-games",
        "War" to "war",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri"
    )

    override val orderBys = listOf(
        "New" to "new",
        "Popular" to "popular",
        "Updates" to "updates"
    )

    data class SearchNovel(
        @JsonProperty("title") val title: String?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("cover_path") val coverPath: String?
    )

    data class SearchResponseDto(
        @JsonProperty("novels") val novels: List<SearchNovel>?
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/genre-$tag/?page=$page&order=$orderBy&status$mainCategory"
        val document = app.get(url).document

        val novels = document.select("div.recommendations-grid > div.recommendation-card").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("h3")?.text() ?: return@mapNotNull null

            newSearchResponse(name = title, url = href) {
                posterUrl = fixUrlNull(
                    card.selectFirst("img")?.attr("src")
                )
            }
        }

        return HeadMainPageResponse(url, novels)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/search/?q=${Uri.encode(query)}"
        val response = app.get(url).parsed<SearchResponseDto>()

        return response.novels?.mapNotNull { novel ->
            val title = novel.title?.trim() ?: return@mapNotNull null
            val slug = novel.slug?.trim() ?: return@mapNotNull null

            newSearchResponse(name = title, url = "/novel/$slug/") {
                posterUrl = fixUrlNull(novel.coverPath)
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        novelsIdRequired[url] = document.selectFirst("meta[name=novel-id]")?.attr("content") ?: ""
        val title = document.selectFirst("h1.novel-title, meta[property=og:title]")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.trim() ?: return null

        val chapters = getChapters(url)

        return newStreamResponse(name = title, url = url, data = chapters) {
            author = document.selectFirst("p.novel-author > a")?.text()?.trim()
            posterUrl = fixUrlNull(
                document.selectFirst("meta[property=og:image]")?.attr("content")
                    ?: document.selectFirst("img.novel-cover")?.attr("src")
            )
            synopsis = document.selectFirst("div.summary-content")?.text()?.trim()
            tags = document.select("div.genre-tags > span.genre-tag").map {
                it.text().trim().lowercase().replaceFirstChar { char -> char.uppercase() }
            }
            setStatus(document.selectFirst("span.status-badge")?.text()?.trim())
            related = getRelated()
        }
    }
    suspend fun getRelated(): List<SearchResponse> {
        val url = "$mainUrl/api/recommendations/"
        val document = app.get(url).parsed<RelatedResponse>()
        return document.novels.map { element ->
            val href = fixUrl(element.slug)
            val title = element.title
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(element.coverPath)
            }
        }
    }

    override suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean
    ): List<UserReview> {
        val realUrl = "$mainUrl/api/comments/?comment_type=novel&commentable_id=${novelsIdRequired[url]}&sort=newest&page=$page&parent_only=true"
        val res = app.get(realUrl).parsedSafe<PostsResponse>()
        val dataList = res?.comments ?: return emptyList()

        return dataList.map { item ->
            val content = if (item.isSpoiler == true && !showSpoilers) {
                "Spoiler content hidden"
            } else {
                item.content ?: ""
            }

            // ¿2025-10-18T23:39:33..." -> "2025-10-18 23:39:33"
            val cleanDate = item.createdAt?.replace("T", " ")

            UserReview(
                review = content,
                username = item.author?.username ?: "User",
                reviewDate = cleanDate,
                avatarUrl = fixUrlNull(item.author?.profileImageUrl),
            )
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val chapterRoot = document.selectFirst("div.chapter-text") ?: return null

        chapterRoot.select("script, style, .ads, .ad, .advertisement, .chapter-nav").remove()

        val paragraphs = chapterRoot.select("> p")
        return if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("") { it.outerHtml() }
        } else {
            chapterRoot.html().takeIf { it.isNotBlank() }
        }
    }

    private suspend fun getChapters(url: String): List<ChapterData> {
        val cleanUrl = url.removeSuffix("/")
        val firstPageUrl = "$cleanUrl/chapters/?page=1"
        val document = app.get(firstPageUrl).document

        val pagination = document.selectFirst("select#pageSelect")
        if (pagination != null) {
            val lastPageNumber = pagination.select("option").lastOrNull()?.text()?.toIntOrNull() ?: 1

            // Intentamos obtener el número total del último capítulo para generar URLs numéricas
            val lastPageUrl = "$cleanUrl/chapters/?page=$lastPageNumber"
            val lastPageDoc = app.get(lastPageUrl).document
            val totalChapters = lastPageDoc.select("div.chapters-grid > div.chapter-card")
                .lastOrNull()
                ?.selectFirst("div.chapter-number")
                ?.text()
                ?.toIntOrNull()

            if (totalChapters != null) {
                return (1..totalChapters).map { num ->
                    newChapterData("Chapter $num", "$cleanUrl/chapter/$num")
                }
            }
        }

        return document.select("div.chapters-grid > div.chapter-card").mapNotNull { li ->
            val name = li.selectFirst("h3")?.text() ?: return@mapNotNull null
            val onClick = li.attr("onClick")
            val chapterUrl = fixUrlNull(onClick.substringAfter("location.href='", "").substringBefore("'", "")) ?: ""

            newChapterData(name, chapterUrl) {
                dateOfRelease = li.selectFirst("p.chapter-time")?.text()
            }
        }
    }
    data class RelatedResponse(
        @JsonProperty("novels")
        val novels:List<Novel>
    )
    data class Novel(
        @JsonProperty("title")
        val title:String,
        @JsonProperty("slug")
        val slug:String,
        @JsonProperty("cover_path")
        val coverPath:String
    )
    data class PostsResponse(
        @JsonProperty("comments") val comments: List<Comment>? = null,        @JsonProperty("pagination") val pagination: Pagination? = null
    )

    data class Pagination(
        @JsonProperty("current_page") val currentPage: Int? = null,
        @JsonProperty("total_pages") val totalPages: Int? = null,
        @JsonProperty("has_next") val hasNext: Boolean? = null
    )

    data class Comment(
        @JsonProperty("content") val content: String? = null,
        @JsonProperty("created_at") val createdAt: String? = null,
        @JsonProperty("author") val author: Author? = null,
        @JsonProperty("is_spoiler") val isSpoiler: Boolean? = null
    )

    data class Author(
        @JsonProperty("username") val username: String? = null,
        @JsonProperty("profile_image_url") val profileImageUrl: String? = null
    )

}