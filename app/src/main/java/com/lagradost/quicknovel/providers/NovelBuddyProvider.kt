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
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.providers.NovelFireProvider.PostsResponse
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.util.AppUtils.parseJson
import org.jsoup.Jsoup

class NovelBuddyProvider : MainAPI() {
    override val name = "Novel Buddy"
    override val mainUrl = "https://novelbuddy.com"
    private val apiUrl = "https://api.novelbuddy.com/titles"
    override val iconId = R.drawable.icon_novelbuddy
    override val iconBackgroundId = R.color.novelBuddyColor
    override val lang = "en"
    override val hasMainPage = true
    override val hasReviews = true
    var novelId = ""
    override val mainCategories = listOf(
        "Popular (Month)" to "month",
        "Popular (All Time)" to "all-time",
        "Newest" to "latest-release",
        "Completed" to "completed"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$apiUrl/search?top_views=$mainCategory&page=$page&limit=24"
        val document = app.get(url).parsed<Root>()

        return HeadMainPageResponse(url,
            document.data.items.map { element ->
                val title = element.name
                val href = element.url
                newSearchResponse(title, href) {
                    posterUrl = element.cover
                }
            })
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val jsonData = document.selectFirst("script#__NEXT_DATA__")?.data()
            ?: throw Exception("Invalid data")

        val nextData = parseJson<NextData>(jsonData)
        val bookId = nextData.props.pageProps.initialManga.id
        novelId = bookId
        val title = nextData.props.pageProps.initialManga.name

        val api = "$apiUrl/$bookId/chapters"
        val apiDoc = app.get(api).parsed<ChaptersApiResponse>()

        val chapters = apiDoc.data.chapters.map { li ->
            val name = li.name
            val url = li.url
            newChapterData(name, url)
        }.reversed()


        return newStreamResponse(title, url, chapters) {
            this.posterUrl = nextData.props.pageProps.initialManga.cover
            this.synopsis = nextData.props.pageProps.initialManga.summary

            this.author = nextData.props.pageProps.initialManga.authors.joinToString(", ") { it.name }
            this.tags = nextData.props.pageProps.initialManga.genres.map { it.name }
            setStatus(nextData.props.pageProps.initialManga.status)
        }
    }

    //https://api.novelbuddy.com/comments/title/WAY3xDyr?page=1&limit=10&sort=newest&skip_reactions=1
    override suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean
    ): List<UserReview> {
        if (novelId.isEmpty()) return emptyList()
        val realUrl = "${apiUrl.replace("/titles", "")}/comments/title/$novelId?page=$page&limit=10&sort=newest&skip_reactions=1"
        val res = app.get(realUrl).parsedSafe<CommentResponse>()
        val data = res?.data ?: return emptyList()
        return data.items?.map { item ->
            val reviewTxt = item.content ?: ""

            val date = item.createdAt?.replace("T", " ")

            UserReview(
                review = reviewTxt,
                username = item.user?.name ?: "Guest",
                reviewDate = date,
                avatarUrl = fixUrlNull(item.user?.avatar),
            )
        } ?: emptyList()
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val content = document.selectFirst("div.novel-tts-content") ?: return null
        content.select(".ads, .hidden, script").remove()
        return content.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$apiUrl/search?page=1&limit=7&q=${Uri.encode(query)}"
        val document = app.get(url).parsed<Root>()

        return document.data.items.map { element ->
            val title = element.name
            val href = element.url
            newSearchResponse(title, href) {
                posterUrl = element.cover
            }
        }
    }

    data class NextData(
        @JsonProperty("props") val props: NextProps
    )

    data class NextProps(
        @JsonProperty("pageProps") val pageProps: NextPageProps
    )

    data class NextPageProps(
        @JsonProperty("initialManga") val initialManga: InitialManga
    )

    data class InitialManga(
        @JsonProperty("id") val id: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("cover") val cover: String,
        @JsonProperty("summary") val summary: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("authors") val authors: List<Author>,
        @JsonProperty("genres") val genres: List<Genre>
    )

    data class Author(
        @JsonProperty("name") val name: String
    )

    data class Genre(
        @JsonProperty("name") val name: String
    )

    data class Root(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("data") val data: Data,
        @JsonProperty("message") val message: String,
    )

    data class Data(
        @JsonProperty("items") val items: List<Item>,
        @JsonProperty("pagination") val pagination: Pagination,
    )

    data class Item(
        @JsonProperty("id") val id: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("alt_name") val altName: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("cover") val cover: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("rating") val rating: Double,
        @JsonProperty("stats") val stats: Stats,
        @JsonProperty("updated_at") val updatedAt: String,
        @JsonProperty("cv") val cv: Long,
        @JsonProperty("latest_chapters") val latestChapters: List<LatestChapter>,
    )

    data class Stats(
        @JsonProperty("views") val views: Long,
        @JsonProperty("bookmarks_count") val bookmarksCount: Long,
        @JsonProperty("comments_count") val commentsCount: Long,
        @JsonProperty("chapters_count") val chaptersCount: Long,
        @JsonProperty("ratings_count") val ratingsCount: Long,
        @JsonProperty("reviews_count") val reviewsCount: Long,
    )

    data class LatestChapter(
        @JsonProperty("id") val id: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("created_at") val createdAt: String,
        @JsonProperty("updated_at") val updatedAt: String,
        @JsonProperty("cv") val cv: Long,
    )

    data class Pagination(
        @JsonProperty("total") val total: Long,
        @JsonProperty("page") val page: Long,
        @JsonProperty("limit") val limit: Long,
        @JsonProperty("total_pages") val totalPages: Long,
        @JsonProperty("has_next") val hasNext: Boolean,
        @JsonProperty("has_previous") val hasPrevious: Boolean,
    )

    data class ChaptersApiResponse(
        @JsonProperty("success") val success: Boolean,
        @JsonProperty("data") val data: ChaptersData,
        @JsonProperty("message") val message: String,
    )

    data class ChaptersData(
        @JsonProperty("chapters") val chapters: List<Chapter>,
    )

    data class Chapter(
        @JsonProperty("id") val id: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("updated_at") val updatedAt: String,
        @JsonProperty("cv") val cv: Long,
    )

    data class CommentResponse(
        @JsonProperty("success") val success: Boolean? = null,
        @JsonProperty("data") val data: CommentData? = null
    )

    data class CommentData(
        @JsonProperty("items") val items: List<CommentItem>? = null,
        @JsonProperty("next_cursor") val nextCursor: String? = null,
        @JsonProperty("has_more") val hasMore: Boolean? = null
    )

    data class CommentItem(
        @JsonProperty("content") val content: String? = null,
        @JsonProperty("user") val user: CommentUser? = null,
        @JsonProperty("created_at") val createdAt: String? = null,
    )

    data class CommentUser(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("avatar") val avatar: String? = null
    )
}