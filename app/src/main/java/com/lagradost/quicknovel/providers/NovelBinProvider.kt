package com.lagradost.quicknovel.providers

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
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
import com.lagradost.quicknovel.setStatus
import org.jsoup.nodes.Document


open class NovelBinProvider : MainAPI() {
    override val name = "NovelBin"
    override val mainUrl = "https://novelarrow.com"
    override val hasMainPage = true
    override val usesCloudFlareKiller = true
    override val iconId = R.drawable.icon_novelbin
    override val hasReviews = true

    override val mainCategories = listOf(
        "Popular this week" to "POPULAR",
        "Last updated" to "",
        "Recently added" to "NEW",
        "Top (most viewed)" to "ALL_TIME",
        "Top rated" to "RATING",
        "Most chapters" to "CHAPTERS"
    )
    override val tags = listOf(
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Adventurei" to "adventurei",
        "Anime & Comics" to "anime & comics",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Eastern" to "eastern",
        "Ecchi" to "ecchi",
        "Fan-fiction" to "fan-fiction",
        "Fantasy" to "fantasy",
        "Game" to "game",
        "Gender Bender" to "gender bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "LGBT+" to "lgbt+",
        "LitRPG" to "litrpg",
        "Magic" to "magic",
        "Magical Realism" to "magical realism",
        "Martial Arts" to "martial arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Military" to "military",
        "Modern Life" to "modern life",
        "Mystery" to "mystery",
        "Other" to "other",
        "Psychological" to "psychological",
        "Realistic" to "realistic",
        "Reincarnation" to "reincarnation",
        "Romance" to "romance",
        "Romancel" to "romancel",
        "School Life" to "school life",
        "Sci-Fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shoujo Ai" to "shoujo ai",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen ai",
        "Slice of Life" to "slice of life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "System" to "system",
        "Thriller" to "thriller",
        "Tragedy" to "tragedy",
        "Urban" to "urban",
        "Video Games" to "video games",
        "War" to "war",
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
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/genre/$tag?sort=$mainCategory&page=$page"
        val document = app.get(url).document

        val returnValue = document.select("article.site-panel.group.flex").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("h2")?.text() ?: return@mapNotNull null

            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(
                    card.selectFirst("img")?.attr("src")
                )
            }

        }
        return HeadMainPageResponse(url, returnValue)
    }

    suspend fun getChapters(slug: String): List<ChapterData> {
        val response = app.get("$mainUrl/api-web/novels/$slug/chapters?sort=asc")

        val chapters = try {
            response.parsed<ChaptersResponse>().items
        } catch (_: Exception) {
            response.parsed<ChaptersResponseNested>().items.flatten()
        }

        return chapters.mapNotNull { ch ->
            if (ch.premium || ch.platinum) return@mapNotNull null

            newChapterData(
                name = ch.chapterName,
                url = "$mainUrl/api-web/novels/$slug/chapters/${ch.chapterId}"
            )
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val realUrl = url.replace("https://novelbin.com/b/", mainUrl + "/novel/")
        val document = app.get(realUrl).document
        val title = document.selectFirst("title")?.text() ?: throw ErrorLoadingException("No title")

        val chapters = getChapters(realUrl.substringAfter("/novel/"))
        return newStreamResponse(title, realUrl, chapters) {
            this.posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
            this.synopsis = document.selectFirst("meta[name=description]")?.attr("content")
            this.author = document.selectFirst("meta[name=author]")?.attr("content")
            this.tags =
                document.selectFirst("meta[name=category]")?.attr("content")?.let { listOf(it.trim().lowercase().replaceFirstChar { char -> char.uppercase() }) }
            setStatus(document.selectFirst("meta[name=og:novel:status]")?.attr("content"))
            related = getRelated(document)
        }
    }

    private fun getRelated(dc: Document): List<SearchResponse>{
        return dc.select("aside.classic-detail-aside > section.mt-5 > div.site-panel > div > a.group").mapNotNull { element ->
            val href = element.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("> div.font-extrabold")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
            }
        }
    }

    override suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean
    ): List<UserReview> {
        val slug = url.removeSuffix("/").substringAfter("/novel/")
        val realUrl = "$mainUrl/api-web/novels/$slug/comments?page=$page&limit=10&sort=most-liked&scope=novel"

        val response = app.get(realUrl).parsedSafe<ReviewResponse>()

        return response?.items?.mapNotNull { item ->
            if (!showSpoilers && item.isSpoiler == true) return@mapNotNull null

            UserReview(
                review = org.jsoup.Jsoup.parse(item.content ?: "").text(),
                username = item.disqusUser?.name ?: "User",
                reviewDate = item.createdDate,
                avatarUrl = fixUrlNull(item.disqusUser?.avatarUrl),
            )
        } ?: emptyList()
    }

    override suspend fun loadHtml(url: String): String {
        val response = app.get(url).parsed<ChapterResponse>()
        val content = response.item?.chapterInfo?.chapterContent ?:throw ErrorLoadingException("No content")
        return content
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api-web/novels?limit=5&page=1&status=all&sort=SEARCH_KEYWORD&genre=ALL&keyword=${Uri.encode(query.trim()).replace("%20","+")}"
        val document = app.get(url).parsed<MainPageResponse>()

        return document.items.map { card ->
            val href = "$mainUrl/novel/" + card.novelId
            val title = card.novelName
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = "https://images.novelarrow.com/novel_164_245/" + card.novelId + ".jpg"
            }
        }
    }

    data class MainPageResponse(
        @JsonProperty("items") val items: List<NovelInfo>
    )

    data class NovelInfo(
        @JsonProperty("novel_name") val novelName: String,
        @JsonProperty("novel_id") val novelId: String
    )

    data class ChaptersResponse(
        @JsonProperty("items") val items: List<ChapterInfo>,
        @JsonProperty("pagination") val pagination: Pagination
    )

    data class ChaptersResponseNested(
        @JsonProperty("items") val items: List<List<ChapterInfo>>,
        @JsonProperty("pagination") val pagination: Pagination
    )

    data class ChapterResponse(
        @JsonProperty("item") val item: ChapterItemWrapper?
    )

    data class ChapterItemWrapper(
        @JsonProperty("chapterInfo") val chapterInfo: ChapterInfo?
    )

    data class ChapterInfo(
        @JsonProperty("chapter_id")
        val chapterId: String?,

        @JsonProperty("chapter_name")
        val chapterName: String,

        @JsonProperty("platinum_content")
        val platinum: Boolean = false,

        @JsonProperty("premium_content")
        val premium: Boolean = false,

        @JsonProperty("coin_price")
        val coinPrice: Int? = 0,

        @JsonProperty("comments_count")
        val commentsCount: Int? = 0,

        @JsonProperty("chapter_content")
        val chapterContent: String?
    )

    data class Pagination(
        @JsonProperty("page") val page: Int,
        @JsonProperty("limit") val limit: Int,
        @JsonProperty("total") val total: Int,
        @JsonProperty("totalPages") val totalPages: Int
    )

    data class ReviewResponse(@JsonProperty("items") val items: List<ReviewItem>?)

    data class ReviewItem(
        @JsonProperty("content") val content: String?,
        @JsonProperty("isSpoiler") val isSpoiler: Boolean?,
        @JsonProperty("disqusUser") val disqusUser: DisqusUser?,
        @JsonProperty("created_date") val createdDate: String?
    )

    data class DisqusUser(
        @JsonProperty("name") val name: String?,
        @JsonProperty("avatarUrl") val avatarUrl: String?
    )
}