package com.lagradost.quicknovel.providers

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
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
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

open class NovelFireProvider:  MainAPI() {
    override val name = "NovelFire"
    override val mainUrl = "https://novelfire.net"
    override val iconId = R.drawable.icon_novelfire
    override val rateLimitTime = 500L
    override val hasMainPage = true
    override val hasReviews = true
    val novelsIdRequired = ConcurrentHashMap<String, String>()
    var nextPosts: String = ""
    override val mainCategories = listOf(
        "All" to "status-all",
        "Completed" to "status-completed",
        "Ongoing" to "status-ongoing"
    )
    override val orderBys =
        listOf("Popular" to "sort-popular", "New" to "sort-new", "Updates" to "sort-latest-release")

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
        "Fan-fiction" to "fan-fiction",
        "Fantasy" to "fantasy",
        "Game" to "game",
        "Gender-bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Lgbt" to "lgbt",
        "Magic" to "magic",
        "Magical-realism" to "magical-realism",
        "Manhua" to "manhua",
        "Martial-arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Military" to "military",
        "Modern-life" to "modern-life",
        "Movies" to "movies",
        "Mystery" to "mystery",
        "Other" to "other",
        "Psychological" to "psychological",
        "Realistic-fiction" to "realistic-fiction",
        "Reincarnation" to "reincarnation",
        "Romance" to "romance",
        "School-life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shoujo-ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen-ai" to "shounen-ai",
        "Slice-of-life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "System" to "system",
        "Tragedy" to "tragedy",
        "Urban" to "urban",
        "Urban-life" to "urban-life",
        "Video-games" to "video-games",
        "War" to "war",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/genre-${tag}/${orderBy}/${mainCategory}/all-novel?page=$page"
        val document = app.get(url).document


        val returnValue = document.select("li.novel-item").mapNotNull { select ->
            val node = select.selectFirst("a[title]") ?: return@mapNotNull null
            val href = node.attr("href")
            val title =
                node.attr("title").takeIf { !it.isEmpty() } ?: node.selectFirst("h4.novel-title")
                    ?.text() ?: return@mapNotNull null

            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(
                    select.selectFirst("img")?.attr("data-src")
                        ?: select.selectFirst("img")?.attr("src")
                )
            }

        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document
        val infoDiv = document.select("div.novel-info")

        val title = infoDiv.selectFirst("h1.novel-title")?.text() ?: throw ErrorLoadingException("Title not found")

        novelsIdRequired[url] = document.selectFirst("a#novel-report")?.attr("report-post_id") ?: throw ErrorLoadingException("Id not found")
        val chapters = getChapters(url)

        return newStreamResponse(title,fixUrl(url), chapters) {
            this.author = infoDiv.selectFirst("div.author > a")?.text()
            this.posterUrl = fixUrlNull(document.selectFirst("figure.cover img")?.attr("src"))
            this.synopsis = document.selectFirst("meta[itemprop=description]")?.attr("content") ?: ""

            this.tags = infoDiv.select("div.categories ul li").mapNotNull {
                it.text().trim().takeIf { text ->  text.isNotEmpty() }
            }

            infoDiv.select("div.header-stats span").forEach{ span ->
                if(span.text().contains("Status")){
                    setStatus(span.selectFirst("strong")?.text())
                }
                else if(span.text().contains("Views")){
                    this.views = span.selectFirst("strong")?.ownText()?.trim()?.let {
                        if(it.contains("k",true)) it.replace("k","", true).toFloatOrNull()?.times(1000)?.roundToInt()
                        else if(it.contains("m",true)) it.replace("m","", true).toFloatOrNull()?.times(1000000)?.roundToInt()
                        else it.toIntOrNull()
                    }
                }
            }

            this.peopleVoted =
                document.selectFirst("div.novel-body.container a.grdbtn.reviews-latest-container p.latest.text1row")
                    ?.text()
                    ?.replace(Regex("[^0-9]"), "")
                    ?.trim()
                    ?.toIntOrNull() ?: 0
            this.rating = document.selectFirst("div.rating strong.nub")?.text()
                ?.toFloatOrNull()?.times(20)?.times(10)?.roundToInt()
            related = getRelated(url)
        }
    }

    open suspend fun getChapters(url: String): List<ChapterData> {
        val bookSlug = url.trimEnd('/').substringAfterLast("/")
        val ajaxUrl = "$mainUrl/ajax/listChapterDataAjax"
        val response = app.get(ajaxUrl, params = mapOf(
            "draw" to "1",
            "columns[0][data]" to "n_sort",
            "columns[0][name]" to "cmm_posts_detail.n_sort",
            "columns[0][searchable]" to "true",
            "columns[0][orderable]" to "true",
            "columns[0][search][value]" to "",
            "columns[0][search][regex]" to "false",
            "columns[1][data]" to "bookmark_created_at",
            "columns[1][name]" to "bookmark_chapters.created_at",
            "columns[1][searchable]" to "false",
            "columns[1][orderable]" to "true",
            "columns[1][search][value]" to "",
            "columns[1][search][regex]" to "false",
            "order[0][column]" to "0",
            "order[0][dir]" to "asc",
            "order[0][name]" to "cmm_posts_detail.n_sort",
            "start" to "0",
            "length" to "-1",
            "search[value]" to "",
            "search[regex]" to "false",
            "post_id" to novelsIdRequired[url].toString(),
            "only_bookmark" to "false"
        )).parsed<AjaxChapterRoot>()

        return response.data?.mapNotNull { item ->
            val nSort = item.nSort ?: return@mapNotNull null

            val rawTitle = item.title ?: "Chapter $nSort"
            val chapterUrl = "$mainUrl/book/$bookSlug/chapter-$nSort"
            newChapterData(rawTitle, chapterUrl) {
                this.dateOfRelease = item.createdAt
            }
        } ?: emptyList()
    }

    suspend fun getRelated(url: String): List<SearchResponse> {
        val url = "$mainUrl/ajax/novelYouMayLike?post_id=${novelsIdRequired[url]}"
        val document = app.get(url).parsed<RelatedResponse>()
        return Jsoup.parse(document.html).select("li.novel-item").mapNotNull { element ->
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("h5")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(
                    element.selectFirst("img")?.attr("data-src")
                        ?: element.selectFirst("img")?.attr("src")
                )
            }
        }
    }

    override suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean
    ): List<UserReview> {
        val realUrl = "$mainUrl/comment/show?post_id=${novelsIdRequired[url]}&chapter_id=&order_by=newest&cursor=$nextPosts"
        val res = app.get(realUrl).parsed<PostsResponse>()
        nextPosts = res.nextCursor
        val reviews = Jsoup.parse(res.html).select("li:has(.comment-item)")

        return reviews.mapNotNull { r ->
            val header = r.selectFirst("div.comment-header")
            val body = r.selectFirst("div.comment-body")

            val username = header?.selectFirst(".username")?.text()
            val avatarUrl = header?.selectFirst("img.avatar")?.attr("src")
            val reviewTime = header?.selectFirst(".post-date")?.text() // Ej: "11h", "1d"

            val reviewContent = body?.selectFirst(".comment-text")

            val isSpoiler = reviewContent?.attr("data-spoiler") == "1"
            if (!showSpoilers && isSpoiler == true) return@mapNotNull null

            val reviewTxt = reviewContent?.html()

            UserReview(
                reviewTxt ?: return@mapNotNull null,
                username = username,
                reviewDate = reviewTime,
                avatarUrl = fixUrlNull(avatarUrl),
            )
        }
    }

    fun normalize(text: String): String {
        return text
            .lowercase()
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9]"), "")
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        var title = document.selectFirst("span.chapter-title")?.text() ?: ""
        val contentElement = document.selectFirst("div#content")?.apply {
            selectFirst("p")?.let { p ->
                if(normalize(p.text()) == normalize(title)) title = ""
            }
            select("img[src*=disable-blocker.jpg]").forEach { it.remove() }
        } ?: return null

        return if(title.isEmpty())
            contentElement.html()
        else
            "<p>$title</p><br>${contentElement.html()}"
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?keyword=${Uri.encode(query.trim()).replace("%20","+")}&page=1"
        val document = app.get(url).document

        return document.select("ul.novel-list.horizontal.col2.chapters li.novel-item").mapNotNull { element ->
            val a = element.selectFirst("a")?:return@mapNotNull null
            val title = a.attr("title").trim()
            val novelUrl = a.attr("href")
            val coverUrl = fixUrlNull(a.selectFirst("img")?.attr("src"))
            newSearchResponse(title, novelUrl){
                posterUrl = coverUrl
            }
        }
    }

    data class RelatedResponse(
        @JsonProperty("html")
        val html:String
    )

    data class PostsResponse(
        @JsonProperty("has_more_pages")
        val hasMore: Boolean,
        @JsonProperty("html")
        val html: String,
        @JsonProperty("next_cursor")
        val nextCursor: String,
    )

    data class AjaxChapterRoot(
        @JsonProperty("data") val data: List<AjaxChapterItem>? = null
    )

    data class AjaxChapterItem(
        @JsonProperty("n_sort") val nSort: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("bookmark_created_at") val createdAt: String? = null
    )
}