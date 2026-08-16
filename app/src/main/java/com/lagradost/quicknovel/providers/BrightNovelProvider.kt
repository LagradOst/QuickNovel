package com.lagradost.quicknovel.providers

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
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
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.util.AppUtils.parseJson

class BrightNovelProvider: MainAPI() {
    override val name = "Bright Novel"
    override val mainUrl = "https://brightnovels.com"
    override val iconId = R.drawable.icon_brightnovel
    override val iconBackgroundId = R.color.black
    override val hasMainPage = true
    override val rateLimitTime = 1000L
    override val hasReviews = true

    override val mainCategories = listOf(
        "All" to "",
        "Ongoing" to "ongoing",
        "Completed" to "completed",
        "Hiatus" to "hiatus",
        "Cancelled" to "cancelled"
    )
    override val orderBys =
        listOf(
            "Descending" to "desc",
            "Ascending" to "asc",
        )
    override val tags = listOf(
        "All" to "",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Drama" to "drama",
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
        val url = "$mainUrl/series?page=$page&order=$orderBy&status=$mainCategory&genres=$tag"
        val document = app.get(url).document
        val dataPage = document.selectFirst("div#app")?.attr("data-page")
            ?: throw Exception("Data page not found")

        val response = parseJson<BrightNovelMainPageResponse>(dataPage)

        val novels = response.props.seriesList.data.map { it.toSearchResponse() }
        return HeadMainPageResponse(url, novels)
    }

    private fun Serie.toSearchResponse(): SearchResponse {
        val novel = this
        return newSearchResponse(
            name = novel.title,
            url = "$mainUrl/series/${novel.slug}"
        ) {
            posterUrl = fixUrlNull(novel.cover?.url)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url)
        val document = res.document
        val dataPage = document.selectFirst("div#app")?.attr("data-page")
            ?: throw Exception("Data page not found")

        val pageResponse = parseJson<SeriesPageResponse>(dataPage)
        val series = pageResponse.props.series
        val title = series.title

        val chaptersUrl = "$mainUrl/series/${series.slug}/chapters/free?loaded=0&sort_order=desc"
        val chaptersResponse = app.get(chaptersUrl).parsed<ChaptersApiResponse>()

        val chapters = chaptersResponse.chapters.map { ch ->
            newChapterData(ch.name, "$mainUrl/series/${series.slug}/${ch.slug}") {
                dateOfRelease = ch.updatedAt.split("T").firstOrNull()
            }
        }.reversed()

        return newStreamResponse(title, url, chapters) {
            this.author = series.user?.username
            this.synopsis = series.description
            this.posterUrl = fixUrlNull(series.cover?.url)
            this.tags = series.genres?.map { it.name }
            setStatus(series.storyState)
            reviewData = series.id.toString()
            related = pageResponse.props.recommended?.map { it.toSearchResponse() }
        }
    }


    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val libraryId = data ?: return emptyList()

        val realUrl = "$mainUrl/comments/series/$libraryId?page=$page"
        val res = app.get(realUrl).parsed<CommentsResponse>()
        return res.data.map { comment ->
            newReview(comment.content) {
                username = comment.user.username
                date = comment.createdAt.split("T").firstOrNull()
                avatarUrl = comment.user.avatar
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val dataPage = document.selectFirst("div#app")?.attr("data-page")
            ?: return null

        val pageResponse = parseJson<ChapterPageResponse>(dataPage)
        return pageResponse.props.chapter.content
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/search?query=${Uri.encode(query)}"
        val response = app.get(url).parsed<SearchApiResponse>()
        return response.data.series.map { it.toSearchResponse() }
    }

    data class SearchApiResponse(
        @JsonProperty("data")
        val data: SearchApiData,
    )

    data class SearchApiData(
        @JsonProperty("series")
        val series: List<Serie>,
    )

    data class BrightNovelMainPageResponse(
        @JsonProperty("props")
        val props: Props,
    )

    data class SeriesPageResponse(
        @JsonProperty("props")
        val props: SeriesPageProps,
    )

    data class SeriesPageProps(
        @JsonProperty("series")
        val series: Serie,
        @JsonProperty("recommended")
        val recommended: List<Serie>? = null
    )

    data class Props(
        @JsonProperty("seriesList")
        val seriesList: Series,
    )

    data class Series(
        @JsonProperty("data")
        val data: List<Serie>
    )

    data class Serie(
        @JsonProperty("id")
        val id: Int? = null,
        @JsonProperty("title")
        val title: String,
        @JsonProperty("slug")
        val slug: String,
        @JsonProperty("description")
        val description: String? = null,
        @JsonProperty("story_state")
        val storyState: String? = null,
        @JsonProperty("cover")
        val cover: Cover? = null,
        @JsonProperty("genres")
        val genres: List<Genre>? = null,
        @JsonProperty("user")
        val user: User? = null,
    )

    data class User(
        @JsonProperty("username")
        val username: String? = null,
    )

    data class Genre(
        @JsonProperty("name")
        val name: String,
    )

    data class Cover(
        @JsonProperty("url")
        val url: String,
    )

    data class ChaptersApiResponse(
        @JsonProperty("chapters")
        val chapters: List<ChapterDataApi>,
    )

    data class ChapterDataApi(
        @JsonProperty("name")
        val name: String,
        @JsonProperty("slug")
        val slug: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        @JsonProperty("is_premium")
        val isPremium: Boolean,
    )

    data class CommentsResponse(
        @JsonProperty("data")
        val data: List<CommentData>
    )

    data class CommentData(
        @JsonProperty("content")
        val content: String,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("user")
        val user: CommentUser
    )

    data class CommentUser(
        @JsonProperty("username")
        val username: String,
        @JsonProperty("avatar")
        val avatar: String?
    )

    data class ChapterPageResponse(
        @JsonProperty("props")
        val props: ChapterPageProps,
    )

    data class ChapterPageProps(
        @JsonProperty("chapter")
        val chapter: ChapterDetail,
    )

    data class ChapterDetail(
        @JsonProperty("content")
        val content: String,
    )
}