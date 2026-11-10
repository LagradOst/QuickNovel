package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.Score
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.SlopStatus
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newReview
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup

object RoyalRoadProvider : MainAPI() {
    override val name = "Royal Road"
    override val mainUrl = "https://www.royalroad.com"
    override val rateLimitTime = 2000L
    override val hasMainPage = true
    override val hasReviews = true
    override val iconId = R.drawable.big_icon_royalroad
    override val iconBackgroundId = R.color.royalRoadColor
    private const val API = "https://api.royalroad.com/v1"

    private val fictionIdRegex = Regex("""royalroad\.com/fiction/(\d+)/""")
    private val chapterIdRegex = Regex("""/chapter/(\d+)/""")

    override val orderBys = listOf(
        "Best Rated" to "bestrated",
        "Ongoing" to "Ongoing",
        "Completed" to "Completed",
        "Popular this week" to "PopularThisWeek",
        "Latest Updates" to "LatestUpdates",
        "New Releases" to "newreleases",
        "Trending" to "trending",
        "Rising Stars" to "RisingStars",
        "Writathon" to "writathon"
    )

    override val tags = listOf(
        "All" to ""
    ) + (listOf(
        "Wuxia" to "wuxia",
        //"Xianxia" to "xianxia",
        "War and Military" to "war_and_military",
        "Low Fantasy" to "low_fantasy",
        "High Fantasy" to "high_fantasy",
        "Mythos" to "mythos",
        "Martial Arts" to "martial_arts",
        "Secret Identity" to "secret_identity",
        "Cyberpunk" to "cyberpunk",
        "Virtual Reality" to "virtual_reality",
        "Time Loop" to "loop",
        "Space Opera" to "space_opera",
        "First Contact" to "first_contact",
        "Grimdark" to "grimdark",
        "Strong Lead" to "strong_lead",
        "Time Travel" to "time_travel",
        "Ruling Class" to "ruling_class",
        "Action" to "action",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Contemporary" to "contemporary",
        "Drama" to "drama",
        "Fantasy" to "fantasy",
        "Historical" to "historical",
        "Horror" to "horror",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "Satire" to "satire",
        "Sci-fi" to "sci_fi",
        "Hard Sci-fi" to "hard_sci-fi",
        "Soft Sci-fi" to "soft_sci-fi",
        "LitRPG" to "litrpg",
        "Magic" to "magic",
        "GameLit" to "gamelit",
        "Male Lead" to "male_lead",
        "Female Lead" to "female_lead",
        "Portal Fantasy / Isekai" to "summoned_hero",
        "Reincarnation" to "reincarnation",
        "Harem" to "harem",
        "Gender Bender" to "gender_bender",
        "Anti-Hero Lead" to "anti-hero_lead",
        "Progression" to "progression",
        "Strategy" to "strategy",
        "Short Story" to "one_shot",
        "Tragedy" to "tragedy"
    ).sortedBy { it.first })

    data class Similar(
        @JsonProperty("description")
        val description: String?,
        @JsonProperty("averageRating")
        val averageRating: Double?,
        @JsonProperty("tags")
        val tags: List<Tag> = emptyList(),
        @JsonProperty("id")
        val id: Long,
        @JsonProperty("title")
        val title: String,
        @JsonProperty("cover")
        val cover: String?,
        //@JsonProperty("thumbnailHash")
        //val thumbnailHash: String,
    )

    data class ResultRoot(
        val id: Long,
        val title: String,
        val slug: String,
        val description: String?,
        val cover: String?,
        //val thumbnailHash: String,
        val status: String?,
        //val coverBackground: Any?,
        //val donationButtons: DonationButtons,
        val tags: List<Tag>,
        val authorInfo: AuthorInfo,
        //val volumes: List<Volume>,
        val chapters: List<Chapter>,
        //val collaborators: List<Any?>,
        //val adsEnabled: Boolean,
        val averageRating: Double,
        val advancedScores: AdvancedScores?,
        val advancedStats: AdvancedStats?,
        //val lastReadTime: Any?,
        //val lastReadChapterId: Any?,
        //val bookmarks: Any?,
        //val ratingBreakdown: RatingBreakdown,
        val nextRelease: String?,
        //val hasSubscriptions: Boolean,
    )

    //data class DonationButtons(
    //    val payPal: String,
    //    val patreon: String,
    //)

    data class Tag(
        @JsonProperty("id")
        val id: Long,
        @JsonProperty("slug")
        val slug: String,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("shortName")
        val shortName: String,
        @JsonProperty("type")
        val type: String,
    )

    data class AuthorInfo(
        val userId: Long,
        val username: String,
        val avatar: String,
        //val achievementCount: Long,
        //val isFollowing: Boolean,
    )

    /*data class Volume(
        val id: Long,
        val title: String,
        val cover: String,
        val chapterCount: Long,
        val order: Long,
        val dateAdded: String,
        val dateLastModified: Any?,
        val dateFirstChapter: String,
        val dateLastChapter: String,
        val chapters: Any?,
    )*/

    data class Chapter(
        val id: Long,
        val title: String,
        val isTitleSpoiler: Boolean,
        val published: String,
        val lastEdit: String?,
        val isEarlyAccess: Boolean,
        val isUnlocked: Boolean,
        val isExclusiveChapter: Boolean,
        val visible: Boolean,
        val volumeId: Long?,
        val order: Long,
    )

    data class AdvancedScores(
        val styleScore: Double,
        val storyScore: Double,
        val grammarScore: Double,
        val characterScore: Double,
    )

    data class AdvancedStats(
        val followers: Long,
        val favorites: Long,
        val ratings: Long,
        val totalViews: Long,
        val averageView: Double,
        val wordCount: Long,
        val pageCount: Long,
    )

    /*data class RatingBreakdown(
        @JsonProperty("4")
        val n4: Long,
        @JsonProperty("3.5")
        val n35: Long,
        @JsonProperty("4.5")
        val n45: Long,
        @JsonProperty("5")
        val n5: Long,
        @JsonProperty("0.5")
        val n05: Long,
        @JsonProperty("2.5")
        val n25: Long,
        @JsonProperty("1")
        val n1: Long,
        @JsonProperty("2")
        val n2: Long,
        @JsonProperty("3")
        val n3: Long,
        @JsonProperty("1.5")
        val n15: Long,
    )*/

    data class ChapterRoot(
        val preAuthorsNote: String?,
        val content: String,
        val postAuthorsNote: String?,
        //val previousChapter: NextPrevChapter,
        //val nextChapter: NextPrevChapter,
        val authorInfo: AuthorInfo,
        //val fictionInfo: FictionInfo,
        //val poll: Any?,
        //val commentCount: Long,
        //val patreon: String,
        //val payPal: String,
        //val enableReaderSuggestions: Boolean,
        val id: Long,
        val title: String,
        //val isTitleSpoiler: Boolean,
        //val slug: String,
        //val published: String,
        //val lastEdit: String,
        //val isEarlyAccess: Boolean,
        //val isUnlocked: Boolean,
        //val isExclusiveChapter: Boolean,
        val order: Long,
    )

    data class NextPrevChapter(
        val id: Long,
        val slug: String,
        val title: String,
        val isTitleSpoiler: Boolean,
    )

    data class FictionInfo(
        //val coverBackgroundAlignment: String?,
        val id: Long,
        val title: String,
        val cover: String?,
        //val thumbnailHash: String,
        val slug: String,
    )

    data class ReviewRoot(
        val data: List<Daum>,
    )

    data class Paginate(
        val total: Int,
        val perPage: Int,
        val page: Int,
    )

    data class Daum(
        val id: Long,
        val user: User,
        val overallScore: Int,
        val title: String,
        val content: String,
        val advancedScores: AdvancedScores?,

        /*val fiction: Fiction,
        val isAdvanced: Boolean,
        val reviewDate: String,
        val upVotes: Long,
        val downVotes: Long,
        val totalVotes: Long,
        val myVote: Any?,
        val lastChapterId: Long,
        val lastChapterTitle: String,
        val reviewSwap: Boolean,
        val showBorder: Boolean,
        val level: Long,
        val reputationLevel: Long,
        val currentBorder: Any?,
        val isPremium: Boolean,
        val fictionAuthorId: Long,
        val hideReviewFromAuthor: Boolean,
        val lastEdit: Any?,
        val isReview: Boolean,

        val fictionId: Long,
        val fictionTitle: String,
        val fictionCover: String,
        val thumbnailHash: String,*/
    )

    data class Fiction(
        val id: Long,
        val title: String,
        val cover: String?,
        // val thumbnailHash: String,
        val slug: String,
    )

    data class User(
        //val border: String,
        //val reputationLevel: Long,
        //val administrator: Boolean,
        //val staffMember: Boolean,
        val id: Long?,
        val userName: String?,
        val avatar: String?,
    )

    /*data class Paginate(
        val total: Long,
        val perPage: Long,
        val page: Long,
    )*/

    data class SearchRoot(
        val data: List<Similar>,
        val paginate: Paginate,
    )


    suspend fun similar(id: Int) = app.get("$API/fiction/$id/similar").parsed<Array<Similar>>()

    suspend fun result(id: Int) = app.get("$API/fiction/$id").parsed<ResultRoot>()

    suspend fun chapter(id: Int) = app.get("$API/fiction/chapters/$id").parsed<ChapterRoot>()

    suspend fun review(id: Int, page: Int) =
        app.get("$API/review/$id?page=$page&sort=Top&dir=Descending").parsed<ReviewRoot>()

    suspend fun list(type: String, tag: String, page: Int) =
        app.get(
            "$API/fiction/list/$type?genre=$tag&showStubs=false&page=$page&perPage=20"
        ).parsed<SearchRoot>()

    suspend fun search(title: String, tagsAdded: List<String>, orderBy: String?, page: Int) =
        app.post(
            "$API/fiction/search/advanced", json =
                mapOf(
                    "Title" to title,
                    "MinPages" to 0,
                    "MaxPages" to 20000,
                    "MinRating" to 0.5,
                    "MaxRating" to 5,
                    "Status" to null,
                    "TagsAdd" to tagsAdded,
                    "TagsRemove" to arrayOf<String>(),
                    "OrderBy" to (orderBy ?: "relevance"),
                    "Dir" to "desc",
                    "Page" to page,
                    "Type" to "All",
                    "IncludeNotInterested" to false,
                    "ExcludeFollowsFavorites" to false,
                    "Advanced" to true
                )
        ).parsed<SearchRoot>().data

    @Throws
    fun getFictionIdFromUrl(url: String) = (fictionIdRegex.find(url) ?: throw ErrorLoadingException(
        "Invalid url"
    )).groupValues[1].toInt()

    @Throws
    fun getChapterIdFromUrl(url: String) = (chapterIdRegex.find(url) ?: throw ErrorLoadingException(
        "Invalid url"
    )).groupValues[1].toInt()

    fun newSearchResponse(similar: Similar) =
        newSearchResponse(name = similar.title, url = "$mainUrl/fiction/${similar.id}/") {
            posterUrl = similar.cover
            slopStatus = getSlopStatus(similar.tags)
            tags = similar.tags.filter { it.type == "Tag" }.map { it.name }
            contentWarnings = similar.tags.filter { it.type == "Warning" }.map { it.name }
            genre = similar.tags.filter { it.type == "Genre" }.map { it.name }
            synopsis = similar.description
            score = Score.from5(similar.averageRating)
        }

    override suspend fun search(query: String): List<SearchResponse> {
        return search(
            title = query,
            tagsAdded = emptyList(),
            orderBy = null,
            page = 0
        ).map(::newSearchResponse)
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val response = list(
            type = orderBy ?: "bestrated",
            tag = tag ?: "",
            page = page
        )

        return HeadMainPageResponse(
            /** TODO FIX so you can open it in the browser */
            url = "$mainUrl/fictions/$orderBy?page=$page${if (tag == null || tag == "") "" else "&genre=$tag"}",
            list = if (response.paginate.page < page) {
                emptyList()
            } else {
                response.data.map(::newSearchResponse)
            }
        )
    }

    fun getSlopStatus(tags: List<Tag>) =
        when {
            tags.any { it.slug == "ai_assisted" } -> SlopStatus.AiAssisted
            tags.any { it.slug == "ai_generated" } -> SlopStatus.AiGenerated
            else -> SlopStatus.Human
        }

    override suspend fun load(url: String): LoadResponse {
        val id = getFictionIdFromUrl(url)
        val data = result(id)
        val url = "$mainUrl/fiction/$id/${data.slug}/"
        return newStreamResponse(
            name = data.title,
            url = url,
            data = data.chapters.sortedBy { it.order }.map { chapter ->
                newChapterData(
                    name = chapter.title,
                    url = "$url/chapter/${chapter.id}/"
                ) {
                    dateOfRelease = chapter.published
                }
            }) {
            posterUrl = data.cover
            setStatus(data.status)
            slopStatus = getSlopStatus(data.tags)
            synopsis = data.description
            tags = data.tags.filter { it.type == "Tag" }.map { it.name }
            contentWarnings = data.tags.filter { it.type == "Warning" }.map { it.name }
            genre = data.tags.filter { it.type == "Genre" }.map { it.name }
            author = data.authorInfo.username
            views = data.advancedStats?.totalViews?.toInt()
            score = Score.from5(data.averageRating)
            peopleVoted = data.advancedStats?.ratings?.toInt()
            reviewData = id.toString()
            related = similar(id).map(::newSearchResponse)
        }
    }

    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val id = data!!.toInt()
        val data = review(id, page)
        return data.data.map { review ->
            val reviewContent = Jsoup.parse(review.content)
            newReview(content = review.content) {
                title = review.title
                username = review.user.userName
                avatarUrl = review.user.avatar
                containsSpoilers = reviewContent.getElementsByClass("spoiler-new")
                    .isNotEmpty() || reviewContent.getElementsByClass("spoiler").isNotEmpty()
                score = Score.from10(review.overallScore)
                scores = review.advancedScores?.let { scores ->
                    listOf(
                        Score.from10(scores.storyScore) to "Story",
                        Score.from10(scores.styleScore) to "Style",
                        Score.from10(scores.grammarScore) to "Grammar",
                        Score.from10(scores.characterScore) to "Character"
                    )
                }
            }
        }
    }

    override suspend fun loadHtml(url: String): String {
        val id = getChapterIdFromUrl(url)
        val data = chapter(id)

        val document = Jsoup.parse(data.content)

        val noteContainerClass = "qnauthornotecontainer"
        val noteContentClass = "qnauthornote"
        val noteSeparatorClass = "qnauthornoteseparator"
        val separatorLine = "━━━━━━━━━━━━━━━━━━━━"
        val spacer = "&nbsp;"

        if (!data.preAuthorsNote.isNullOrBlank()) {
            val content = """
                <div class="$noteContainerClass">
                    <div class="$noteContentClass">${data.preAuthorsNote}</div>
                    <div class="$noteSeparatorClass"><p>$separatorLine</p><p>$spacer</p></div>
                </div>
                """.trimIndent()

            Jsoup.parse(content).selectFirst("div")?.let {
                document.prependChild(it)
            }
        }

        if (!data.postAuthorsNote.isNullOrBlank()) {
            val content = """
                <div class="$noteContainerClass">
                    <div class="$noteSeparatorClass"><p>$spacer</p><p>$separatorLine</p></div>
                    <div class="$noteContentClass">${data.postAuthorsNote}</div>
                </div>
                """.trimIndent()
            Jsoup.parse(content).selectFirst("div")?.let {
                document.appendChild(it)
            }
        }

        return document.html()
    }
}