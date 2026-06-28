package com.lagradost.quicknovel.providers
import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.util.AppUtils.parseJson

class NovelBuddyProvider : MainAPI() {
    override val name = "Novel Buddy"
    override val mainUrl = "https://novelbuddy.com"
    private val apiUrl = "https://api.novelbuddy.com/titles"
    override val iconId = R.drawable.icon_novelbuddy
    override val iconBackgroundId = R.color.novelBuddyColor
    override val lang = "en"
    override val hasMainPage = true

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
        val props: NextProps
    )

    data class NextProps(
        val pageProps: NextPageProps
    )

    data class NextPageProps(
        val initialManga: InitialManga
    )

    data class InitialManga(
        val id: String,
        val name: String,
        val cover: String,
        val summary: String,
        val status: String,
        val authors: List<Author>,
        val genres: List<Genre>
    )

    data class Author(val name: String)
    data class Genre(val name: String)


    data class Root(
        val success: Boolean,
        val data: Data,
        val message: String,
    )

    data class Data(
        val items: List<Item>,
        val pagination: Pagination,
    )

    data class Item(
        val id: String,
        val url: String,
        val name: String,
        @JsonProperty("alt_name")
        val altName: String,
        val slug: String,
        val cover: String,
        val status: String,
        val rating: Double,
        val stats: Stats,
        @JsonProperty("updated_at")
        val updatedAt: String,
        val cv: Long,
        @JsonProperty("latest_chapters")
        val latestChapters: List<LatestChapter>,
    )

    data class Stats(
        val views: Long,
        @JsonProperty("bookmarks_count")
        val bookmarksCount: Long,
        @JsonProperty("comments_count")
        val commentsCount: Long,
        @JsonProperty("chapters_count")
        val chaptersCount: Long,
        @JsonProperty("ratings_count")
        val ratingsCount: Long,
        @JsonProperty("reviews_count")
        val reviewsCount: Long,
    )

    data class LatestChapter(
        val id: String,
        val url: String,
        val name: String,
        val slug: String,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        val cv: Long,
    )

    data class Pagination(
        val total: Long,
        val page: Long,
        val limit: Long,
        @JsonProperty("total_pages")
        val totalPages: Long,
        @JsonProperty("has_next")
        val hasNext: Boolean,
        @JsonProperty("has_previous")
        val hasPrevious: Boolean,
    )

    data class ChaptersApiResponse(
        val success: Boolean,
        val data: ChaptersData,
        val message: String,
    )

    data class ChaptersData(
        val chapters: List<Chapter>,
    )

    data class Chapter(
        val id: String,
        val url: String,
        val name: String,
        val slug: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        val cv: Long,
    )
}