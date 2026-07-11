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
    override val mainUrl = "https://novelbuddy.me"
    private val apiUrl = "https://api.novelbuddy.me/titles"
    override val iconId = R.drawable.icon_novelbuddy
    override val iconBackgroundId = R.color.novelBuddyColor
    override val lang = "en"
    override val hasMainPage = true
    override val hasReviews = true
    var novelId = ""
    /*
    override val mainCategories = listOf(
        "Ongoing" to "ongoing",
        "Completed" to "completed",
        "Hiatus" to "hiatus",
        "Cancelled" to "cancelled"
    )*/
    override val tags = listOf(
        "Action" to "action",
        "ActionAdventure" to "actionadventure",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Adventurei" to "adventurei",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Eastern" to "eastern",
        "Easterni" to "easterni",
        "Ecchi" to "ecchi",
        "Ecchi Fantasy" to "ecchi fantasy",
        "Fan-Fiction" to "fan-fiction",
        "Fantasy" to "fantasy",
        "Gam" to "gam",
        "Game" to "game",
        "Games" to "games",
        "Gender Bender" to "gender bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Light Novel" to "light novel",
        "Lolicon" to "lolicon",
        "Magic" to "magic",
        "Martial Arts" to "martial arts",
        "Martial ArtsReincarnation" to "martial artsreincarnation",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Military" to "military",
        "Modern Life" to "modern life",
        "Movies" to "movies",
        "Mystery" to "mystery",
        "Psychologic" to "psychologic",
        "Psychological" to "psychological",
        "Reincarnatio" to "reincarnatio",
        "Reincarnation" to "reincarnation",
        "Romanc" to "romanc",
        "Romance" to "romance",
        "Romance.Adventure" to "romance.adventure",
        "Romance.Harem" to "romance.harem",
        "Romance.Smut" to "romance.smut",
        "RomanceAction" to "romanceaction",
        "RomanceAdventure" to "romanceadventure",
        "RomanceHarem" to "romanceharem",
        "Romancei" to "romancei",
        "Romancel" to "romancel",
        "Romancem" to "romancem",
        "School Life" to "school life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Seinen Wuxia" to "seinen wuxia",
        "Shoujo" to "shoujo",
        "Shoujo Ai" to "shoujo ai",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen ai",
        "Slice of Lif" to "slice of lif",
        "Slice Of Life" to "slice of life",
        "Slice of Lifel" to "slice of lifel",
        "Smut" to "smut",
        "Sports" to "sports",
        "Superna" to "superna",
        "Supernatural" to "supernatural",
        "System" to "system",
        "Thriller" to "thriller",
        "Tragedy" to "tragedy",
        "Urban" to "urban",
        "Urban Life" to "urban life",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri"
    )

    override val orderBys = listOf(
        "Default Order" to "",
        "Latest Updated" to "latest",
        "Most Popular" to "popular",
        "Highest Rating" to "rating",
        "Most Viewed" to "views",
        "Most Chapters" to "chapters",
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val params = mutableListOf<Pair<String, String>>()
        if (!tag.isNullOrBlank()) params.add("genres" to tag)
        if (!mainCategory.isNullOrBlank()) params.add("status" to mainCategory)
        if (!orderBy.isNullOrBlank()) params.add("sort" to orderBy)

        val queryPath = params.joinToString("") { "${it.first}=${it.second}&" }
        val url = "$apiUrl/search?${queryPath}page=$page&limit=24"
        val response = app.get(url).parsed<Root>()
        return HeadMainPageResponse(url,
            response.data.items.map { element ->
                val title = element.name
                val href = element.url
                newSearchResponse(title, href) {
                    posterUrl = element.cover
                }
            })
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url.replaceFirst("y.com","y.me")).document

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
        @JsonProperty("data") val data: Data,
    )

    data class Data(
        @JsonProperty("items") val items: List<Item>,
    )

    data class Item(
        @JsonProperty("id") val id: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("cover") val cover: String?,
        @JsonProperty("status") val status: String,
        @JsonProperty("rating") val rating: Double,
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