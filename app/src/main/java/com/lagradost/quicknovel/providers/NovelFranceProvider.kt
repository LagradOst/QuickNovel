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
import com.lagradost.quicknovel.setStatus
import java.util.concurrent.ConcurrentHashMap

open class NovelFranceProvider : MainAPI() {
    override val name = "Novel France"
    override val mainUrl = "https://novelfrance.fr"
    override val iconId = R.drawable.icon_novelfrance
    override val hasMainPage = true
    override val hasReviews = true
    override val rateLimitTime = 500L
    override val lang = "fr"
    val novelsIdRequired = ConcurrentHashMap<String, String>()
    private val pageSize = 24

    override val orderBys = listOf(
        "Most Popular" to "popular",
        "Latest" to "latest"
    )

    override val tags = listOf(
        "Tous" to "",
        "Action" to "action",
        "Adulte" to "adulte",
        "Anti-Héros" to "anti-h-ros",
        "Arts Martiaux" to "arts-martiaux",
        "Aventure" to "aventure",
        "Comédie" to "com-die",
        "Drame" to "drama",
        "Ecchi" to "ecchi",
        "Fantaisie" to "fantaisie",
        "Fantastique" to "fantastique",
        "Harem" to "harem",
        "Historique" to "historical",
        "Horreur" to "horreur",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Magie" to "magie",
        "Mature" to "mature",
        "Mécha" to "mcha",
        "Mystère" to "myst-re",
        "Psychologique" to "psychologique",
        "Réincarnation" to "r-incarnation",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shounen" to "shounen",
        "Slice of Life" to "slice-of-life",
        "Sport" to "sport",
        "Surnaturel" to "surnaturel",
        "Système" to "syst-me",
        "Thriller" to "thriller",
        "Tragédie" to "trag-die",
        "Transmigration" to "transmigration",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri"
    )

    override val mainCategories = listOf(
        "Tous" to "",
        "En cours" to "ONGOING",
        "Terminé" to "COMPLETED",
        "En pause" to "HIATUS",
        "Abandonné" to "DROPPED"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val skip = (page - 1) * pageSize
        if (orderBy == "latest") {
            val url = "$mainUrl/api/chapters/latest-home?offset=$skip&limit=$pageSize"
            val data = app.get(url).parsed<LatestHomeResponseJson>()
            val novels = data.data.map { n ->
                newSearchResponse(n.title, "$mainUrl/novel/${n.slug}") {
                    posterUrl = fixUrlNull(n.coverImage)
                }
            }
            return HeadMainPageResponse(url, novels)
        }

        var url = "$mainUrl/api/search?skip=$skip&take=$pageSize"
        if (!tag.isNullOrBlank()) url += "&genres=$tag"
        if (!mainCategory.isNullOrBlank()) url += "&status=$mainCategory"

        val data = app.get(url).parsed<NovelListResponseJson>()
        val novels = data.novels.map { n ->
            newSearchResponse(n.title, "$mainUrl/novel/${n.slug}") {
                posterUrl = fixUrlNull(n.coverImage)
            }
        }
        return HeadMainPageResponse(url, novels)
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = url.removeSuffix("/").substringAfterLast("/")
        val novelApiUrl = "$mainUrl/api/novels/$slug"
        val data = app.get(novelApiUrl).parsed<NovelDetailJson>()
        val chapters = (1..data.count.chapters).map { chNumber ->
            newChapterData("chapter $chNumber", "$mainUrl/novel/$slug/chapter-$chNumber")
        }
        novelsIdRequired[url] = data.id
        return newStreamResponse(data.title, url, chapters) {
            author = data.author
            posterUrl = fixUrlNull(data.coverImage)
            synopsis = data.description
            tags = data.genres.map { it.name }
            rating = (data.rating * 200).toInt()
            views = data.views
            related = app.get(url).document.let{ getRelated(it) }
            setStatus(data.status)
        }
    }
    fun getRelated(dc: org.jsoup.nodes.Document):List<SearchResponse>{
        return dc.select("div.space-y-6 > div.space-y-4 > a.border.group").mapNotNull { novel ->
            newSearchResponse(
                name = novel.selectFirst("h4")?.text() ?: return@mapNotNull null,
                url = novel.attr("href")
            ) {
                posterUrl = fixUrlNull(novel.selectFirst("img")?.attr("src"))
            }
        }
    }
    override suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean
    ): List<UserReview> {
        val reviewData = novelsIdRequired[url] ?: return emptyList()
        val realUrl = "$mainUrl/api/comments?novelId=${reviewData}&type=REVIEW&sortBy=newest&page=$page&limit=15&hasRating=true"
        val res = app.get(realUrl).parsed<CommentsResponse>()
        return res.comments.map { r ->
            UserReview(
                r.content,
                username = r.author.username,
                reviewDate = r.createdAt,
                avatarUrl = fixUrlNull(r.author.avatar),
                rating = r.rating
            )
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val data = app.get(url).document.select("main > article").first { it.selectFirst("div.chapter-content") != null }
        data?.select("nav, div.absolute")?.remove()
        return data?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = Uri.encode(query.trim()).replace("%20","+")
        val url = "$mainUrl/api/search?q=$encoded&skip=0&take=50"
        val data = app.get(url).parsed<NovelListResponseJson>()
        return data.novels.map { n ->
            newSearchResponse(n.title, "$mainUrl/novel/${n.slug}") {
                posterUrl = fixUrlNull(n.coverImage)
            }
        }
    }

    data class NovelListItemJson(
        @JsonProperty("title") val title: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("coverImage") val coverImage: String
    )

    data class NovelListResponseJson(
        @JsonProperty("novels") val novels: List<NovelListItemJson>
    )

    data class NovelDetailJson(
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("coverImage") val coverImage: String,
        @JsonProperty("author") val author: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("chapterViews") val views: Int,
        @JsonProperty("rating") val rating: Float,
        @JsonProperty("genres") val genres: List<GenreJson>,
        @JsonProperty("_count") val count: Count,
    )
    data class Count(
        @JsonProperty("chapters") val chapters: Int
    )

    data class GenreJson(
        @JsonProperty("name") val name: String,
        @JsonProperty("slug") val slug: String
    )

    data class LatestHomeItemJson(
        @JsonProperty("title") val title: String,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("coverImage") val coverImage: String
    )

    data class LatestHomeResponseJson(
        @JsonProperty("data") val data: List<LatestHomeItemJson>
    )
    data class CommentsResponse(
        @JsonProperty("comments")
        val comments: List<Comment>
    )
    data class Comment(
        @JsonProperty("author") val author: Author,
        @JsonProperty("content") val content: String,
        @JsonProperty("createdAt") val createdAt: String,
        @JsonProperty("hasSpoiler") val hasSpoiler: Boolean,
        @JsonProperty("rating") val rating: Int,
    )
    data class Author(
        @JsonProperty("username") val username: String,
        @JsonProperty("avatar") val avatar: String?,
    )
}
