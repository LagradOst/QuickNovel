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
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus


open class NovelBinProvider : MainAPI() {
    override val name = "NovelBin"
    override val mainUrl = "https://novelarrow.com"
    override val hasMainPage = true
    override val usesCloudFlareKiller = true
    override val iconId = R.drawable.icon_novelbin

    override val mainCategories =listOf(
        "All" to "all"
    )
    override val tags = listOf(
        "All Genres" to "ALL",
        "Action" to "ACTION",
        "Adult" to "ADULT",
        "Adventure" to "ADVENTURE",
        "Adventurei" to "ADVENTUREI",
        "Anime & Comics" to "ANIME & COMICS",
        "Comedy" to "COMEDY",
        "Drama" to "DRAMA",
        "Eastern" to "EASTERN",
        "Ecchi" to "ECCHI",
        "Fan-fiction" to "FAN-FICTION",
        "Fantasy" to "FANTASY",
        "Game" to "GAME",
        "Gender Bender" to "GENDER BENDER",
        "Harem" to "HAREM",
        "Historical" to "HISTORICAL",
        "Horror" to "HORROR",
        "Isekai" to "ISEKAI",
        "Josei" to "JOSEI",
        "LGBT+" to "LGBT+",
        "LitRPG" to "LITRPG",
        "Magic" to "MAGIC",
        "Magical Realism" to "MAGICAL REALISM",
        "Martial Arts" to "MARTIAL ARTS",
        "Mature" to "MATURE",
        "Mecha" to "MECHA",
        "Military" to "MILITARY",
        "Modern Life" to "MODERN LIFE",
        "Mystery" to "MYSTERY",
        "Other" to "OTHER",
        "Psychological" to "PSYCHOLOGICAL",
        "Realistic" to "REALISTIC",
        "Reincarnation" to "REINCARNATION",
        "Romance" to "ROMANCE",
        "Romancel" to "ROMANCEL",
        "School Life" to "SCHOOL LIFE",
        "Sci-Fi" to "SCI-FI",
        "Seinen" to "SEINEN",
        "Shoujo" to "SHOUJO",
        "Shoujo Ai" to "SHOUJO AI",
        "Shounen" to "SHOUNEN",
        "Shounen Ai" to "SHOUNEN AI",
        "Slice of Life" to "SLICE OF LIFE",
        "Smut" to "SMUT",
        "Sports" to "SPORTS",
        "Supernatural" to "SUPERNATURAL",
        "System" to "SYSTEM",
        "Thriller" to "THRILLER",
        "Tragedy" to "TRAGEDY",
        "Urban" to "URBAN",
        "Video Games" to "VIDEO GAMES",
        "War" to "WAR",
        "Wuxia" to "WUXIA",
        "Xianxia" to "XIANXIA",
        "Xuanhuan" to "XUANHUAN",
        "Yaoi" to "YAOI",
        "Yuri" to "YURI",
    )

    override val orderBys = listOf(
        "Last" to "LASTEST",
    )


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/api-web/novels?limit=30&page=$page&status=$mainCategory&sort=$orderBy&genre=$tag"
        val document = app.get(url).parsed<MainPageResponse>()

        val returnValue = document.items.map { card ->
            val href = "$mainUrl/novel/" + card.novelId
            val title = card.novelName
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = "https://images.novelarrow.com/novel_164_245/" + card.novelId + ".jpg"
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
                url = "$mainUrl/chapter/$slug/${ch.chapterId}"
            )
        }
    }


    override suspend fun load(url: String): LoadResponse
    {
        val realUrl = url.replace("https://novelbin.com/b/", mainUrl + "/novel/")
        val document = app.get(realUrl).document
        val title = document.selectFirst("title")?.text() ?: throw ErrorLoadingException("No title")

        val chapters = getChapters(realUrl.substringAfter("/novel/"))
        return newStreamResponse(title,realUrl,chapters) {
            this.posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
            this.synopsis = document.selectFirst("meta[name=description]")?.attr("content")
            this.author = document.selectFirst("meta[name=author]")?.attr("content")
            this.tags = document.selectFirst("meta[name=category]")?.attr("content")?.let {listOf(it)}
            setStatus(document.selectFirst("meta[name=og:novel:status]")?.attr("content"))
        }
    }

    override suspend fun loadHtml(url: String): String {
        val document = app.get(url).document
        val script = document.select("script")
            .firstOrNull {
                it.data().contains("\\u003ch1\\u003e")
            }?.data()
            ?: error("No encontrado")

        val raw = Regex(
            """"((?:\\.|[^"])*)"""",
            RegexOption.DOT_MATCHES_ALL
        ).findAll(script)
            .map { it.groupValues[1] }
            .first { it.contains("\\u003ch1\\u003e") }

        //I'm not sure if this is the right way to do it, or if there's already a function that does this.
        return ObjectMapper().readValue("\"$raw\"", String::class.java)
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
        @JsonProperty("items") val items:List<NovelInfo>
    )
    data class NovelInfo(
        @JsonProperty("novel_name") val novelName:String,
        @JsonProperty("novel_id") val novelId:String
    )

    data class ChaptersResponse(
        val items: List<ChapterInfo>,
        val pagination: Pagination
    )

    data class ChaptersResponseNested(
        val items: List<List<ChapterInfo>>,
        val pagination: Pagination
    )

    data class ChapterInfo(
        @JsonProperty("chapter_id")
        val chapterId: String,

        @JsonProperty("chapter_name")
        val chapterName: String,

        @JsonProperty("platinum_content")
        val platinum: Boolean,

        @JsonProperty("premium_content")
        val premium: Boolean,

        @JsonProperty("coin_price")
        val coinPrice: Int,

        @JsonProperty("comments_count")
        val commentsCount: Int
    )

    data class Pagination(
        val page: Int,
        val limit: Int,
        val total: Int,
        val totalPages: Int
    )
}