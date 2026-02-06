package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup


open class LibReadProvider : MainAPI() {
    override val name = "LibRead"
    override val mainUrl = "https://libread.com"
    override val hasMainPage = true

    open val removeHtml = false // because the two sites use .html or not for no reason

    override val iconId = R.drawable.icon_libread

    override val iconBackgroundId = R.color.libread_header_color

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

    override suspend fun load(url: String): LoadResponse? {
        val trimmed = url.trim().removeSuffix("/")
        val response = app.get(url)
        val document = response.document
        val name = document.selectFirst("h1.tit")?.text() ?: return null

        val aid = "[0-9]+s.jpg".toRegex().find(response.text)?.value?.substringBefore("s")
        val chaptersDataphp = app.post(
            "$mainUrl/api/chapterlist.php",
            data = mapOf(
                "aid" to aid!!
            )
        )

        val prefix = if (removeHtml) {
            trimmed.removeSuffix(".html")
        } else {
            trimmed
        }

        val data =
            Jsoup.parse(chaptersDataphp.text.replace("""\""", "")).select("option").map { c ->
                val cUrl = "$prefix/${c.attr("value").split('/').last()}" // url + '/' +
                val cName = c.text().ifEmpty {
                    "chapter $c"
                }
                newChapterData(url = cUrl, name = cName)
            }

        return newStreamResponse(url = url, name = name, data = data) {
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
        }
    }
}