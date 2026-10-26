package com.lagradost.quicknovel.providers

import android.annotation.SuppressLint
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newReview
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Date

class RoyalRoadProvider : MainAPI() {
    override val name = "Royal Road"
    override val mainUrl = "https://www.royalroad.com"
    override val rateLimitTime = 500L
    override val hasMainPage = true
    override val hasReviews = true
    override val iconId = R.drawable.big_icon_royalroad
    override val iconBackgroundId = R.color.royalRoadColor
    private val api = "https://api.royalroad.com/v1"

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
        val description: String,
        @JsonProperty("averageRating")
        val averageRating: Double?,
        @JsonProperty("priorityTags")
        val priorityTags: List<PriorityTag> = emptyList(),
        @JsonProperty("id")
        val id: Long,
        @JsonProperty("title")
        val title: String,
        @JsonProperty("cover")
        val cover: String?,
        //@JsonProperty("thumbnailHash")
        //val thumbnailHash: String,
    )

    data class PriorityTag(
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
        val id: Long,
        val slug: String,
        val name: String,
        val shortName: String,
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
        val overallScore: Long,
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
        val data: List<SearchDaum>,
        val paginate: Paginate,
    )

    data class SearchDaum(
        //val description: String,
        //val firstUpdate: String,
        //val lastUpdate: String,
        //val tags: List<Tag>,
        //val priorityTags: List<PriorityTag>,
        //val status: String,
        //val authorId: Long,
        //val authorName: String,
        //val followers: Long?,
        //val averageViews: Long,
        val averageRating: Double?,
        //val ratingCount: Long?,
        //val pages: Long,
        //val chapterCount: Long,
        //val notInterested: Boolean,
        //val following: Boolean,
        //val favorite: Boolean,
        //val readLater: Boolean,
        //val coverBackgroundAlignment: String,
        val id: Long,
        val title: String,
        val cover: String?,
        //val thumbnailHash: String,
    )


    suspend fun similar(id: Int) = app.get("$api/fiction/$id/similar").parsed<Array<Similar>>()

    suspend fun result(id: Int) = app.get("$api/fiction/$id").parsed<ResultRoot>()

    suspend fun chapter(id: Int) = app.get("$api/fiction/chapters/$id").parsed<ChapterRoot>()

    suspend fun review(id: Int, page: Int) =
        app.get("$api/review/$id?page=$page&sort=Top&dir=Descending").parsed<ReviewRoot>()

    suspend fun list(type: String, tag: String, page: Int) =
        app.get(
            "$api/fiction/list/$type?genre=$tag&showStubs=false&page=$page&perPage=20"
        ).parsed<SearchRoot>()

    suspend fun search(title: String, tagsAdded: List<String>, orderBy: String?, page: Int) =
        app.post(
            "$api/fiction/search/advanced", json =
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

    companion object {
        private val fictionIdRegex = Regex("""royalroad\.com/fiction/(\d+)/""")
        private val chapterIdRegex = Regex("""/chapter/(\d+)/""")
    }

    @Throws
    fun getFictionIdFromUrl(url: String) = (fictionIdRegex.find(url) ?: throw ErrorLoadingException(
        "Invalid url"
    )).groupValues[1].toInt()

    @Throws
    fun getChapterIdFromUrl(url: String) = (chapterIdRegex.find(url) ?: throw ErrorLoadingException(
        "Invalid url"
    )).groupValues[1].toInt()

    fun newSearchResponse(response: SearchDaum) =
        newSearchResponse(name = response.title, url = "$mainUrl/fiction/${response.id}/") {
            posterUrl = response.cover
            rating = response.averageRating?.times(200.0)?.toInt()
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
            synopsis = data.description
            tags = data.tags.map { it.name }
            author = data.authorInfo.username
            views = data.advancedStats?.totalViews?.toInt()
            rating = (data.averageRating * 200.0).toInt()
            peopleVoted = data.advancedStats?.ratings?.toInt()
            reviewData = id.toString()
            related = similar(id).map { similar ->
                newSearchResponse(name = similar.title, url = "$mainUrl/fiction/${similar.id}/") {
                    posterUrl = similar.cover
                    // 0-10
                    rating = similar.averageRating?.times(200.0)?.toInt()
                }
            }
        }
    }

    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val id = data!!.toInt()
        val data = review(id, page)
        return data.data.map { review ->
            newReview(content = review.content) {
                title = review.title
                username = review.user.userName
                avatarUrl = review.user.avatar
                rating = review.overallScore.toInt() * 100
                ratings = review.advancedScores?.let { scores ->
                    listOf(
                        scores.storyScore.times(100.0).toInt() to "Story",
                        scores.styleScore.times(100.0).toInt() to "Style",
                        scores.grammarScore.times(100.0).toInt() to "Grammar",
                        scores.characterScore.times(100.0).toInt() to "Character"
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

class RoyalRoadWebProvider : MainAPI() {
    override val name = "Royal Road"
    override val mainUrl = "https://www.royalroad.com"
    override val rateLimitTime = 500L
    override val hasMainPage = true
    override val hasReviews = true
    override val iconId = R.drawable.big_icon_royalroad
    override val iconBackgroundId = R.color.royalRoadColor

    override val orderBys = listOf(
        "Best Rated" to "best-rated",
        "Ongoing" to "active-popular",
        "Completed" to "complete",
        "Popular this week" to "weekly-popular",
        "Latest Updates" to "latest-updates",
        "New Releases" to "new-releases",
        "Trending" to "trending",
        "Rising Stars" to "rising-stars",
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

    @SuppressLint("SimpleDateFormat")
    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val realUrl = "$url?sorting=top&reviews=$page" //SORTING ??
        val document = app.get(realUrl).document
        val reviews = document.select("div.reviews-container > div.review")
        return reviews.mapNotNull { r ->
            val textContent = r.selectFirst("> div.review-right-content")
            val scoreContent = r.selectFirst("> div.review-side")
            fun parseScore(data: String?): Int? {
                return (data?.replace("stars", "")
                    ?.toFloatOrNull()?.times(200))?.toInt()
            }

            val scoreHeader = scoreContent?.selectFirst("> div.scores > div")
            var overallScore =
                parseScore(
                    scoreHeader?.selectFirst("> div.overall-score-container")
                        ?.select("> div")?.get(1)?.attr("aria-label")
                )

            if (overallScore == null) { //SOMETHING WENT WRONG
                val divHeader = scoreHeader?.selectFirst("> div.overall-score-container")
                val divs = divHeader
                    ?.select("> div")
                val names = divs?.get(1)?.selectFirst("> div")
                if (names != null)
                    overallScore = when { // PROBS FOR LOOP HERE
                        names.hasClass("star-50") -> {
                            50
                        }

                        names.hasClass("star-45") -> {
                            45
                        }

                        names.hasClass("star-40") -> {
                            40
                        }

                        names.hasClass("star-35") -> {
                            35
                        }

                        names.hasClass("star-30") -> {
                            30
                        }

                        names.hasClass("star-25") -> {
                            25
                        }

                        names.hasClass("star-20") -> {
                            20
                        }

                        names.hasClass("star-15") -> {
                            15
                        }

                        names.hasClass("star-10") -> {
                            10
                        }

                        names.hasClass("star-5") -> {
                            5
                        }

                        else -> {
                            null
                        }
                    }?.times(20)
            }

            val avatar = scoreContent?.selectFirst("> div.avatar-container-general > img")

            val scores = scoreHeader?.select("> div.advanced-score")
            val scoresData =
                if ((scores?.size
                        ?: 0) <= 0
                ) ArrayList() else scores?.mapNotNull { s ->
                    val divs = s.select("> div")
                    Pair(
                        parseScore(divs[1].attr("aria-label")) ?: return@mapNotNull null,
                        divs[0].text()
                    )
                }

            val reviewHeader = textContent?.selectFirst("> div.review-header")
            val reviewMeta = reviewHeader?.selectFirst("> div.review-meta")

            val reviewTitle = reviewHeader?.selectFirst("> div > div > h4")?.text()

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd")

            val reviewDate =
                reviewMeta?.selectFirst("> span > a > time")?.attr("unixtime")?.toLong()?.let {
                    Date(it * 1000)
                }

            val reviewTime = reviewDate?.let { sdf.format(it).toString() }

            val reviewContent = textContent?.selectFirst("> div.review-content")
            //if (!showSpoilers) reviewContent?.removeClass("spoiler")
            reviewContent?.removeClass("spoiler")
            val reviewTxt = reviewContent?.html()

            newReview(reviewTxt ?: return@mapNotNull null) {
                date = reviewTime
                title = reviewTitle
                username = reviewMeta?.selectFirst("> span > a")?.text()
                avatarUrl = avatar?.attr("src")
                rating = overallScore
                ratings = scoresData
                containsSpoilers = reviewContent.getElementsByClass("spoiler").isNotEmpty()
            }
        }
    }

    data class RelatedData(
        @JsonProperty("synopsis")
        val synopsis: String?,
        @JsonProperty("overallScore")
        val overallScore: Double?,
        @JsonProperty("cover")
        val cover: String?,
        @JsonProperty("title")
        val title: String?,
        @JsonProperty("url")
        val url: String?,
        @JsonProperty("id")
        val id: Int?,
    )

    private suspend fun loadRelated(id: Int?): List<SearchResponse>? {
        if (id == null) return null
        return try {
            // https://www.royalroad.com/fictions/similar?fictionId=68679
            app.get("$mainUrl/fictions/similar?fictionId=$id").parsed<Array<RelatedData>>()
                .mapNotNull { data ->
                    newSearchResponse(
                        name = data.title ?: return@mapNotNull null,
                        url = data.url ?: return@mapNotNull null
                    ) {
                        posterUrl = fixUrlNull(data.cover)
                    }
                }
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url =
            "$mainUrl/fictions/$orderBy?page=$page${if (tag == null || tag == "") "" else "&genre=$tag"}"
        if (page > 1 && arrayOf(
                "trending",
                "rising-stars"
            ).contains(orderBy)
        ) return HeadMainPageResponse(
            url,
            ArrayList()
        ) // TRENDING ONLY HAS 1 PAGE

        val response = app.get(url)

        val document = Jsoup.parse(response.text)

        val returnValue = document.select("div.fiction-list-item").mapNotNull { h ->
            val head = h.selectFirst("> div")
            val hInfo = head?.selectFirst("> h2.fiction-title > a")

            val name = hInfo?.text() ?: return@mapNotNull null
            val cUrl = hInfo.attr("href")

            //val tags = ArrayList(h.select("span.tags > a").map { t -> t.text() })
            newSearchResponse(name = name, url = cUrl) {
                latestChapter = try {
                    if (orderBy == "latest-updates") {
                        head.selectFirst("> ul.list-unstyled > li.list-item > a > span")?.text()
                    } else {
                        h.select("div.stats > div.col-sm-6 > span")[4].text()
                    }
                } catch (_: Throwable) {
                    null
                }
                rating =
                    head.selectFirst("> div.stats")?.select("> div")?.get(1)?.selectFirst("> span")
                        ?.attr("title")?.toFloatOrNull()?.times(200)?.toInt()
                posterUrl = fixUrlNull(h.selectFirst("> figure > a > img")?.attr("src"))
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/fictions/search?title=$query").document

        return document.select("div.fiction-list-item").mapNotNull { h ->
            val head = h.selectFirst("> div.search-content")
            val hInfo = head?.selectFirst("> h2.fiction-title > a")

            val name = hInfo?.text() ?: return@mapNotNull null
            val url = hInfo.attr("href")

            newSearchResponse(url = url, name = name) {
                posterUrl = fixUrlNull(h.selectFirst("> figure.text-center > a > img")?.attr("src"))
                rating =
                    head.selectFirst("> div.stats")?.select("> div")?.get(1)?.selectFirst("> span")
                        ?.attr("title")?.toFloatOrNull()?.times(200)?.toInt()
                // latestChapter = h.select("div.stats > div.col-sm-6 > span")[4].text()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document
        val name = document.selectFirst("h1.font-white")?.text()
            ?: throw ErrorLoadingException("Name not found for '$url'\nmight be deleted or simply a malformed url")
        val fictionId =
            response.text.substringAfter("window.fictionId = ").substringBefore(";").toIntOrNull()

        val chapterHeaders = document.select("div.portlet-body > table > tbody > tr")
        val data = chapterHeaders.mapNotNull { c ->
            val cUrl = c.attr("data-url") ?: return@mapNotNull null
            val td = c.select("> td") // 0 = Name, 1 = Upload
            val cName = td.getOrNull(0)?.selectFirst("> a")?.text() ?: return@mapNotNull null

            newChapterData(name = cName, url = cUrl) {
                dateOfRelease = td[1].selectFirst("> a > time")?.text()
            }
        }

        return newStreamResponse(url = url, name = name, data = data) {
            related = loadRelated(fictionId)

            val statusTxt = document.select("div.col-md-8 > div.margin-bottom-10 > span.label")
            for (s in statusTxt) {
                if (s.hasText()) {
                    if (setStatus(s.text())) break
                }
            }

            val hStates = document.select("ul.list-unstyled")[1]
            val stats = hStates.select("> li")
            views = stats.getOrNull(1)?.text()?.replace(",", "")?.replace(".", "")?.toInt()
            posterUrl =
                document.selectFirst("div.fic-header > div > .cover-art-container > img")
                    ?.attr("src")

            val synoDescript = document.select("div.description > div")
            // val synoParts = synoDescript.select("> p")
            synopsis = synoDescript.html() /*if (synoParts.isEmpty() && synoDescript.hasText()) {
                synoDescript.text().replace("\n", "\n\n") // JUST IN CASE
            } else {
                synoParts.joinToString(separator = "\n\n") { it.text() }
            }*/
            author = document.selectFirst("h4.font-white > span > a")?.text()
            val ratingAttr = document.selectFirst("span.font-red-sunglo")?.attr("data-content")
            tags = document.select("span.tags > a").map { it.text() }
            safe {
                rating =
                    (ratingAttr?.substring(0, ratingAttr.indexOf('/'))?.toFloat()
                        ?.times(200))?.toInt()
            }
        }
    }

    private fun addAuthorNotes(chapter: Element, document: Document) {
        val noteContainerClass = "qnauthornotecontainer"
        val noteContentClass = "qnauthornote"
        val noteSeparatorClass = "qnauthornoteseparator"
        val separatorLine = "━━━━━━━━━━━━━━━━━━━━"
        val spacer = "&nbsp;"

        val noteBeforeChapter = StringBuilder()
        val noteAfterChapter = StringBuilder()

        document.select("div.author-note").forEach { authorNote ->
            val noteContainer = authorNote.parent() ?: return@forEach
            val noteParent = noteContainer.parent() ?: return@forEach
            val chapterParent = chapter.parent() ?: return@forEach

            if (noteParent == chapterParent) {
                val isNoteBeforeChapter =
                    noteContainer.elementSiblingIndex() < chapter.elementSiblingIndex()
                val noteContent = authorNote.html().takeIf { it.isNotBlank() } ?: return@forEach

                if (isNoteBeforeChapter) {
                    noteBeforeChapter.append(noteContent)
                } else {
                    noteAfterChapter.append(noteContent)
                }
            }
        }

        if (noteBeforeChapter.isNotEmpty()) {
            val content = """
                <div class="$noteContainerClass">
                    <div class="$noteContentClass">$noteBeforeChapter</div>
                    <div class="$noteSeparatorClass"><p>$separatorLine</p><p>$spacer</p></div>
                </div>
                """.trimIndent()

            Jsoup.parse(content).selectFirst("div")?.let {
                chapter.prependChild(it)
            }
        }

        if (noteAfterChapter.isNotEmpty()) {
            val content = """
                <div class="$noteContainerClass">
                    <div class="$noteSeparatorClass"><p>$spacer</p><p>$separatorLine</p></div>
                    <div class="$noteContentClass">$noteAfterChapter</div>
                </div>
                """.trimIndent()

            Jsoup.parse(content).selectFirst("div")?.let {
                chapter.appendChild(it)
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val styles = document.select("style")
        val hiddenRegex = Regex("^\\s*(\\..*)\\s*\\{", RegexOption.MULTILINE)
        val chap = document.selectFirst("div.chapter-content") ?: return null
        addAuthorNotes(chap, document)

        styles.forEach { style ->
            hiddenRegex.findAll(style.toString()).forEach {
                val className = it.groupValues[1]
                if (className.isNotEmpty()) {
                    chap.select(className).remove()
                }
            }
        }

        return chap.html()
    }
}
