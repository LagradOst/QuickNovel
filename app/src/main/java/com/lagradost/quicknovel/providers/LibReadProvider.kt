package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.providers.NovelFireProvider.PostsResponse
import com.lagradost.quicknovel.providers.WtrLabProvider.LoadJsonResponse2
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

open class LibReadProvider : MainAPI() {
    override val name = "LibRead"
    override val mainUrl = "https://libread.com"

    //for some reason, now is freewebnovel
    val secondUrl = "https://freewebnovel.com"
    override val hasMainPage = true

    open val removeHtml = false // because the two sites use .html or not for no reason

    override val iconId = R.drawable.icon_libread

    override val iconBackgroundId = R.color.libread_header_color
    override val hasReviews = true
    var novelId = ""

    override val tags = listOf(
        "All" to "",
        "Action" to "Action",
        "Adult" to "Adult",
        "Adventure" to "Adventure",
        "Comedy" to "Comedy",
        "Drama" to "Drama",
        "Eastern" to "Eastern",
        "Ecchi" to "Ecchi",
        "Fantasy" to "Fantasy",
        "Game" to "Game",
        "Gender Bender" to "Gender Bender",
        "Harem" to "Harem",
        "Historical" to "Historical",
        "Horror" to "Horror",
        "Josei" to "Josei",
        "Martial Arts" to "Martial Arts",
        "Mature" to "Mature",
        "Mecha" to "Mecha",
        "Mystery" to "Mystery",
        "Psychological" to "Psychological",
        "Reincarnation" to "Reincarnation",
        "Romance" to "Romance",
        "School Life" to "School Life",
        "Sci-fi" to "Sci-fi",
        "Seinen" to "Seinen",
        "Shoujo" to "Shoujo",
        "Shounen Ai" to "Shounen Ai",
        "Shounen" to "Shounen",
        "Slice of Life" to "Slice of Life",
        "Smut" to "Smut",
        "Sports" to "Sports",
        "Supernatural" to "Supernatural",
        "Tragedy" to "Tragedy",
        "Wuxia" to "Wuxia",
        "Xianxia" to "Xianxia",
        "Xuanhuan" to "Xuanhuan",
        "Yaoi" to "Yaoi"
    )

    override val orderBys = listOf(
        "Latest Release" to "latest-release",
        "Latest Novels" to "latest-novel",
        "Completed Novels" to "completed-novel"
    )

    private fun getChapterList(doc: Document, url: String): List<ChapterData> {
        val scriptData = doc.select("script").map { it.data() }
            .find { it.contains("window.chapterPagination") } ?: return emptyList()

        val totalChapters = "totalChapters:\\s*(\\d+)".toRegex()
            .find(scriptData)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val cleanedUrl = url.removeSuffix("/").substringAfterLast("/").replace("(-novel)?-\\d{4,}+$".toRegex(), "")

        return (1..totalChapters).map { i ->
            newChapterData(
                name = "Chapter $i",
                url = "$secondUrl/novel/$cleanedUrl/chapter-$i"
            )
        }
    }
    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/sort/${orderBy ?: "latest-release"}/$page" else "$mainUrl/genre/$tag/$page"
        val document = app.get(url).document
        val headers = document.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row")
        val returnValue = headers.mapNotNull { h ->
            val h3 = h.selectFirst("h3.tit > a") ?: return@mapNotNull null
            newSearchResponse(
                name = h3.attr("title"),
                url = h3.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(h.selectFirst("div.pic > a > img")?.attr("src"))
                latestChapter = h.select("div.item")[2].selectFirst("> div > a")?.text()
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(
            response.text
                .replace(
                    "\uD835\uDCF5\uD835\uDC8A\uD835\uDC83\uD835\uDE67\uD835\uDE5A\uD835\uDC82\uD835\uDCED.\uD835\uDCEC\uD835\uDE64\uD835\uDE62",
                    "",
                    true
                )
                .replace("libread.com", "", true)
        )
        return document.selectFirst("div.txt")?.html()
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "$mainUrl/search",
            headers = mapOf(
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*",
                "user-agent" to USER_AGENT
            ),
            data = mapOf("searchkey" to query)
        ).document

        return document.select("div.li-row > div.li > div.con").mapNotNull { h ->
            val h3 = h.selectFirst("div.txt > h3.tit > a") ?: return@mapNotNull null

            newSearchResponse(
                name = h3.attr("title") ?: return@mapNotNull null,
                url = h3.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(h.selectFirst("div.pic img")?.attr("src"))
                //latestChapter = h.select("div.item")[2].selectFirst("> div > a")?.text()
            }
        }
    }

    fun getRelated(dc: Document): List<SearchResponse> {
        return dc.select("div.col-l > ul.ul-list6 > li").mapNotNull { element ->
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("h3")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(
                    element.selectFirst("img")?.attr("src")
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        novelId = ""
        val response = app.get(url)
        val document = response.document
        val name = document.selectFirst("h1.tit")?.text() ?: return null
        val chaptersDataphp = getChapterList(document, url)
        novelId = document.selectFirst("a.set-case.add")?.attr("data-articleid") ?: ""
        return newStreamResponse(url = url, name = name, data = chaptersDataphp) {
            author =
                document.selectFirst("span.glyphicon.glyphicon-user")?.nextElementSibling()?.text()
            tags =
                document.selectFirst("span.glyphicon.glyphicon-th-list")?.nextElementSiblings()
                    ?.get(0)
                    ?.text()
                    ?.splitToSequence(", ")?.toList()
            posterUrl = fixUrlNull(document.select(" div.pic > img").attr("src"))
            synopsis = document.selectFirst("div.inner")?.text()
            val votes = document.selectFirst("div.m-desc > div.score > p:nth-child(2)")
            if (votes != null) {
                rating = votes.text().substringBefore('/').toFloat().times(200).toInt()
                peopleVoted = votes.text().substringAfter('(').filter { it.isDigit() }.toInt()
            }
            val statusHeader0 = document.selectFirst("span.s1.s2")
            val statusHeader = document.selectFirst("span.s1.s3")

            setStatus(
                statusHeader?.selectFirst("a")?.text() ?: statusHeader0?.selectFirst("a")?.text()
            )
            related = getRelated(document)
        }
    }

    override suspend fun loadReviews(
         url: String,
         page: Int,
         showSpoilers: Boolean
    ): List<UserReview> {
        if (novelId.isEmpty() || page > 1) return emptyList()

        val realUrl = "$mainUrl/api/comments.php"

        val res = app.post(
            realUrl, data = mapOf(
                "action" to "count",
                "articleid" to novelId,
                "chapterid" to "0"
            )
        ).parsedSafe<LibReadCommentsResponse>()
        val dataList = res?.data?.data_list ?: return emptyList()

        return dataList.map { item ->
            UserReview(
                review = item.content ?: "",
                username = item.user_info?.nickname ?: "User",
                reviewDate = item.created_at,
                avatarUrl = fixUrlNull(item.user_info?.picture),
            )
        }
    }
    data class LibReadCommentsResponse(
        @JsonProperty("data") val data: LibReadCommentData? = null
    )

    data class LibReadCommentData(
        @JsonProperty("is_end") val is_end: Boolean? = null,
        @JsonProperty("data_list") val data_list: List<LibReadCommentItem>? = null
    )

    data class LibReadCommentItem(
        @JsonProperty("content") val content: String? = null,
        @JsonProperty("created_at") val created_at: String? = null,
        @JsonProperty("user_info") val user_info: LibReadUserInfo? = null
    )

    data class LibReadUserInfo(
        @JsonProperty("nickname") val nickname: String? = null,
        @JsonProperty("picture") val picture: String? = null
    )
}