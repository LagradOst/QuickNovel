package com.lagradost.quicknovel.providers

import android.net.Uri
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.USER_AGENT
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newReview
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.providers.LibReadProvider.ChaptersResponse
import com.lagradost.quicknovel.providers.LibReadProvider.LibReadCommentsResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

open class FreewebnovelProvider : MainAPI() {
    override val name = "FreeWebNovel"
    override val mainUrl = "https://freewebnovel.com"
    override val iconId = R.drawable.icon_freewebnovel
    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor
    open val secondUrl = mainUrl
    override val hasMainPage = true
    open val removeHtml = true // because the two sites use .html or not for no reason
    override val hasReviews = true
    override val usesCloudFlareKiller = true
    override val mainCategories = listOf(
        "All" to "",
        "Completed" to "completed"
    )
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
        "Most Popular" to "most-popular"
    )
    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/sort/$orderBy/${if(mainCategory.isNullOrBlank()) "" else "$mainCategory/"}$page" else "$mainUrl/genre/$tag/${if (mainCategory.isNullOrBlank()) "" else "$mainCategory/"}$page"
        val document = app.get(url).document
        val headers = document.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row")
        val returnValue = headers.mapNotNull { h ->
            val h3 = h.selectFirst("h3.tit > a") ?: return@mapNotNull null
            newSearchResponse(
                name = h3.attr("title"),
                url = h3.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(
                    h.selectFirst("picture source")
                        ?.attr("srcset")
                        ?.split(",")
                        ?.lastOrNull()
                        ?.trim()
                        ?.substringBefore(" ")
                ) ?: fixUrlNull(document.selectFirst("div.pic img")?.attr("src"))
                latestChapter = h.select("div.item")[2].selectFirst("> div > a")?.text()
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }

    //used to getChapterList. It's necesary because LibRead and Freewebnovel requires different things.
    open fun getAcode(url: String): String = url.substringAfterLast("/")
    suspend fun getChapterList(doc: Document, url: String): List<ChapterData> {
        val novelId = doc.selectFirst("a.set-case.add")?.attr("data-articleid")
            ?: doc.selectFirst("meta[name=image]")?.attr("content")?.substringAfterLast("/")?.substringBefore("s.jpg")
            ?: return emptyList()
        val baseUrl = url.removeSuffix("/").removeSuffix(".html")
        val acode = getAcode(baseUrl)
        val res = app.post(
            "$secondUrl/api/chapterlist.php", data = mapOf(
                "aid" to novelId,
                "acode" to acode,
                "cid" to "1"
            )
        ).parsed<ChaptersResponse>()
        val document = Jsoup.parse(res.html)
        return document.select("option").mapNotNull { i ->
            newChapterData(
                name = i.text(),//libread chapters doesn't exist anymore
                url = "$secondUrl/novel/$acode/${i.attr("value").substringAfterLast("/")}"
            )
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)
        val document = response.document
        val name = document.selectFirst("h1.tit")?.text() ?: return null
        val chaptersDataphp = getChapterList(document, response.url)
        return newStreamResponse(url = response.url, name = name, data = chaptersDataphp) {
            author =
                document.selectFirst("span.glyphicon.glyphicon-user")?.nextElementSibling()?.text()
            tags =
                document.selectFirst("span.glyphicon.glyphicon-th-list")?.nextElementSiblings()
                    ?.get(0)
                    ?.text()
                    ?.splitToSequence(", ")?.toList()
            posterUrl = fixUrlNull(
                document.selectFirst("picture source")
                    ?.attr("srcset")
                    ?.split(",")
                    ?.lastOrNull()
                    ?.trim()
                    ?.substringBefore(" ")
            ) ?: fixUrlNull(document.selectFirst("div.pic img")?.attr("src"))
            synopsis = document.selectFirst("div.inner")?.text()
            val votes = document.selectFirst("div.m-desc > div.score > p:nth-child(2)")
            if (votes != null) {
                rating = votes.text().substringBefore('/').toFloat().times(200).toInt()
                peopleVoted = votes.text().substringAfter('(').filter { it.isDigit() }.toInt()
            }
            val statusHeader0 = document.selectFirst("span.s1.s2")
            val statusHeader = document.selectFirst("span.s1.s3")

            reviewData = document.selectFirst("a.set-case.add")?.attr("data-articleid")

            setStatus(
                statusHeader?.selectFirst("a")?.text() ?: statusHeader0?.selectFirst("a")?.text()
            )
            related = getRelated(document)
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

    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val realUrl = "$mainUrl/api/comments.php"
        val id = data ?: return emptyList()

        val responses: List<LibReadCommentsResponse> = if (page == 1) {
            (1..4).mapNotNull { i ->
                app.post(
                    realUrl, data = mapOf(
                        "action" to "list",
                        "articleid" to id,
                        "chapterid" to "0",
                        "page" to i.toString()
                    )
                ).parsedSafe<LibReadCommentsResponse>()
            }
        } else {
            listOfNotNull(
                app.post(
                    realUrl, data = mapOf(
                        "action" to "list",
                        "articleid" to id,
                        "chapterid" to "0",
                        "page" to (page + 3).toString()
                    )
                ).parsedSafe<LibReadCommentsResponse>()
            )
        }

        return responses.flatMap { it.data?.dataList ?: emptyList() }.mapNotNull { item ->
            newReview(item.content ?: return@mapNotNull null) {
                username = item.userInfo?.nickname ?: "User"
                date = item.createdAt
                avatarUrl = item.userInfo?.picture
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        document.selectFirst("div.txt>.notice-text")?.remove()
        document.select(".slot-frame").remove()
        document.select("div.reader-ad-skip").remove()

        /*for (e in document.select("p")) {
            if (e.text().contains("The source of this ") || e.selectFirst("a")?.hasAttr("href") == true) {
                e.remove()
            }
        }*/
        return document.selectFirst("div.txt")?.html()
            ?.replace("New novel chapters are published on Freewebnovel.com.", "")
            ?.replace("The source of this content is Freewebnᴏvel.com.", "")
            ?.replace(
                "☞ We are moving Freewebnovel.com to Libread.com, Please visit libread.com for more chapters! ☜",
                ""
            )
    }
     override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get(
            "$mainUrl/search?keyword=${Uri.encode(query.trim()).replace("%20","+")}",
            headers = mapOf(
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*",
                "user-agent" to USER_AGENT
            ),
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
}

/*
class FreewebnovelProvider : MainAPI() {
    override val name = "FreeWebNovel"
    override val mainUrl = "https://freewebnovel.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_freewebnovel

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        "All" to "",
        "Action" to "Action",
        "Adult" to "Adult",
        "AdventCure" to "AdventCure",
        "Comedy" to "Comedy",
        "Drama" to "Drama",
        "Ecchi" to "Ecchi",
        "Fantasy" to "Fantasy",
        //"Editor's choice" to  "Editor's choice",
        "Gender Bender" to "Gender+Bender",
        "Harem" to "Harem",
        "Historical" to "Historical",
        "Horror" to "Horror",
        "Josei" to "Josei",
        "Game" to "Game",
        "Martial Arts" to "Martial+Art",
        "Mature" to "Mature",
        "Mecha" to "Mecha",
        "Mystery" to "Mystery",
        "Psychological" to "Psychological",
        "Romance" to "Romance",
        "School Life" to "School+Life",
        "Sci-fi" to "Sci-fi",
        "Seinen" to "Seinen",
        "Shoujo" to "Shoujo",
        "Shounen Ai" to "Shounen+Ai",
        "Shounen" to "Shounen",
        "Slice of Life" to "Slice+of+Life",
        "Smut" to "Smut",
        "Sports" to "Sports",
        "Supernatural" to "Supernatural",
        "Tragedy" to "Tragedy",
        "Wuxia" to "Wuxia",
        "Xianxia" to "Xianxia",
        "Xuanhuan" to "Xuanhuan",
        "Yaoi" to "Yaoi",
        "Eastern" to "Eastern",
        "Reincarnation" to "Reincarnation",
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/latest-novel/tag/$page.html" else "$mainUrl/genre/$tag/$page.html"
        val response = app.get(url)

        val document = Jsoup.parse(response.text)

        val headers = document.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h?.selectFirst("h3.tit > a")
            val cUrl = fixUrl(h3?.attr("href") ?: continue)

            val name = h3.attr("title")
            val posterUrl = h.selectFirst("div.pic > a > img")?.attr("src")

            val latestChap = h.select("div.item")[2].selectFirst("> div > a")?.text()
            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrlNull(posterUrl),
                    null,
                    latestChap,
                    this.name
                )
            )
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(
            response.text
                .replace("New novel chapters are published on Freewebnovel.com.", "")
                .replace("The source of this content is Freewebnᴏvel.com.", "")
        )
        return document.selectFirst("div.txt")?.html()
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "$mainUrl/search/",
            headers = mapOf(
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "user-agent" to USER_AGENT
            ),
            data = mapOf("searchkey" to query)
        )
        val document = Jsoup.parse(response.text)


        val headers = document.select("div.li-row")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h?.selectFirst("h3.tit > a")
            val cUrl = fixUrl(h3?.attr("href") ?: continue)

            val name = h3.attr("title") ?: continue
            val posterUrl = h.selectFirst("div.pic > a > img")?.attr("src")

            val latestChap = h.select("div.item")[2].selectFirst("> div > a")?.text()
            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrlNull(posterUrl),
                    null,
                    latestChap,
                    this.name
                )
            )
        }
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("h1.tit")?.text() ?: return null

        val author =
            document.selectFirst("span.glyphicon.glyphicon-user")?.nextElementSibling()?.text()
        val tags =
            document.selectFirst("span.glyphicon.glyphicon-th-list")?.nextElementSiblings()?.get(0)
                ?.text()
                ?.splitToSequence(", ")?.toList()

        val posterUrl = document.select(" div.pic > img").attr("src")
        val synopsis = document.selectFirst("div.inner")?.text()

        val data: ArrayList<ChapterData> = ArrayList()
        val chapternumber0 = document.select("div.m-newest1 > ul.ul-list5 > li")[1]
        val chapternumber1 = chapternumber0.selectFirst("a")?.attr("href")
        val aid = "[0-9]+s.jpg".toRegex().find(response.text)?.value?.substringBefore("s")
        val acode = "(?<=r_url\" content=\"https://freewebnovel.com/)(.*)(?=/chapter)".toRegex()
            .find(response.text)?.value
        val chaptersDataphp = app.post(
            "$mainUrl/api/chapterlist.php",
            data = mapOf(
                "acode" to acode!!,
                "aid" to aid!!
            )
        )
        val parsed = Jsoup.parse(chaptersDataphp.text.replace("""\""", "")).select("option")

        for (c in parsed) {

            val cUrl = mainUrl + c?.attr("value")
            val cName = if (c.text().isEmpty()) {
                "chapter $c"
            } else {
                c.text()
            }
            data.add(ChapterData(cName, cUrl, null, null))
        }


        val statusHeader0 = document.selectFirst("span.s1.s2")
        val statusHeader = document.selectFirst("span.s1.s3")

        val status = if (statusHeader != null) {
            when (statusHeader.selectFirst("a")?.text()) {
                "OnGoing" -> STATUS_ONGOING
                "Completed" -> STATUS_COMPLETE
                else -> STATUS_NULL
            }

        } else {
            when (statusHeader0?.selectFirst("> a")?.text()) {
                "OnGoing" -> STATUS_ONGOING
                "Completed" -> STATUS_COMPLETE
                else -> STATUS_NULL
            }
        }

        var rating = 0
        var peopleVoted = 0
        try {
            rating = (document.selectFirst("div.m-desc > div.score > p:nth-child(2)")?.text()!!
                .substringBefore("/").toFloat() * 200).toInt()

            peopleVoted = document.selectFirst("div.m-desc > div.score > p:nth-child(2)")?.text()!!
                .substringAfter("(").filter { it.isDigit() }.toInt()
        } catch (e: Exception) {
            // NO RATING
        }

        return StreamResponse(
            url,
            name,
            data,
            author,
            fixUrlNull(posterUrl),
            rating,
            peopleVoted,
            null,
            synopsis,
            tags,
            status
        )
    }
}*/