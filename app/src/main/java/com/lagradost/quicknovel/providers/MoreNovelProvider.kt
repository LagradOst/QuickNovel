package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import java.util.ArrayList
import java.util.Locale
import kotlin.text.contains
import kotlin.text.toFloat

open class MoreNovelProvider : MainAPI() {
    override val name = "MoreNovel"
    override val mainUrl = "https://risenovel.com" // domain change
    override val iconId = R.drawable.ic_morenovel
    override val lang = "id"//indonesian
    override val mainCategories = listOf(
        "All" to "",
        "Novel Korea" to "korean",
        "Novel China" to "chinese",
        "Novel Jepang" to "japanese",
        "Novel Philipina" to "philippines"
    )
    override val tags = listOf(
        "All" to "",
        "Fantasy" to "fantasy",
        "Martial Arts" to "martial-arts",
        "Xuanhuan" to "xuanhuan",
        "Xianxia" to "xianxia",
        "Wuxia" to "wuxia",
        "Action" to "action",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Harem" to "harem",
        "Gender Bender" to "gender-bender",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "School Life" to "school-life",
        "Shoujo" to "shoujo",
        "Shoujo Ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    )

    override val orderBys: List<Pair<String, String>>
        get() = listOf(
            "Nothing" to "",
            "New" to "new-manga",
            "Most Views" to "views",
            "Trending" to "trending",
            "Rating" to "rating",
            "A-Z" to "alphabet",
            "Latest" to "latest",
        )

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val res = document.selectFirst("div.text-left")
        if (res?.html() == "") {
            return null
        }
        return res?.html()
            ?.replace(
                "(If you have problems with this website, please continue reading your novel on our new website myboxnovel.com THANKS!)",
                ""
            )
            ?.replace(
                "Read latest Chapters at BoxNovel.Com Only",
                ""
            ) // HAVE NOT TESTED THIS ONE, COPY FROM WUXIAWORLD
    }


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val order = when (tag) {
            "" -> "novel"
            null -> "novel"
            "completed" -> "manga-tag/$tag"
            else -> "manga-genre/$tag"
        }
        val url =
            "$mainUrl/$order/page/$page/${if (orderBy == null || orderBy == "") "" else "?m_orderby=$orderBy"}"

        val document = app.get(url).document

        //""div.page-content-listing > div.page-listing-item > div > div > div.page-item-detail"
        val returnValue = document.select("div.page-item-detail").mapNotNull { h ->
            val imageHeader = h.selectFirst("div.item-thumb > a")
            val name = imageHeader?.attr("title")
            if (name?.contains("Comic") != false) return@mapNotNull null// I DON'T WANT MANGA!
            newSearchResponse(
                name = name,
                url = imageHeader.attr("href") ?: return@mapNotNull null
            ) {
                val sum = h.selectFirst("div.item-summary")
                latestChapter =
                    sum?.selectFirst("> div.list-chapter > div.chapter-item > span > a")?.text()
                rating =
                    (sum?.selectFirst("> div.rating > div.post-total-rating > span.score")?.text()
                        ?.toFloat()?.times(200))?.toInt()
                posterUrl = imageHeader.selectFirst("> img")?.let { if(it.hasAttr("data-src")) it.attr("data-src") else it.attr("src")}
            }
        }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/?s=$query&post_type=wp-manga")

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.c-tabs-item__content")
        return headers.mapNotNull { h ->
            val head = h.selectFirst("> div > div.tab-summary")
            val title = head?.selectFirst("> div.post-title > h3 > a")
            val name = title?.text()

            if (name?.contains("Comic") != false) return@mapNotNull null// I DON'T WANT MANGA!

            val url = title.attr("href")
            val meta = h.selectFirst("> div > div.tab-meta")

            val ratingTxt =
                meta?.selectFirst("> div.rating > div.post-total-rating > span.total_votes")?.text()

            newSearchResponse(name = name, url = url ?: return@mapNotNull null) {
                posterUrl =
                    fixUrlNull(h.selectFirst("> div > div.tab-thumb > a > img")?.let{if(it.hasAttr("data-src")) it.attr("data-src") else it.attr("src")})
                rating = if (ratingTxt != null) {
                    (ratingTxt.toFloat() * 200).toInt()
                } else {
                    null
                }
                meta?.selectFirst("> div.latest-chap > span.chapter > a")?.text()
            }
        }
    }

    fun getChapters(text: String): List<ChapterData> {
        val document = Jsoup.parse(text)
        val data: java.util.ArrayList<ChapterData> = ArrayList()
        val chapterHeaders = document.select("ul.version-chap li.wp-manga-chapter")
        for (c in chapterHeaders) {
            val header = c.selectFirst("> a")
            val cUrl = header?.attr("href")
            val cName = header?.text()?.replace("  ", " ")?.replace("\n", "")
                ?.replace("\t", "") ?: continue
            val added = c.selectFirst("> span.chapter-release-date > i")?.text()
            data.add(ChapterData(cName, cUrl ?: continue, added, 0))
        }
        data.reverse()
        return data
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val name =
            document.selectFirst("div.manga-title > h2")?.text()?.replace("  ", " ")
                ?.replace("\n", "")
                ?.replace("\t", "") ?: return null

        val data = getChapters(
            app.post(
                "${url}ajax/chapters/",
            ).text
        )

        return newStreamResponse(url = url, name = name, data = data) {
            tags = document.select("div.genres-content a").map { it.text() }
            author = document.selectFirst("div.manga-author a")?.text()

            var synopsis = ""
            var synopsisParts = document.select("div.info-block.manga-excerpt > div.excerpt-content > p")
            if (synopsisParts.size == 0) synopsisParts = document.select("div.j_synopsis > p")
            if (synopsisParts.size == 0) synopsisParts = document.select("div.summary__content > p")
            for (s in synopsisParts) {
                if (s.hasText() && !s.text().lowercase(Locale.getDefault())
                        .contains(mainUrl)
                ) { // FUCK ADS
                    synopsis += s.text() + "\n\n"
                }
            }
            if (synopsis.isNotEmpty()) {
                this.synopsis = synopsis
            }
            posterUrl = fixUrlNull(document.selectFirst("div.summary_image > a > img")?.attr("src"))
            rating = ((document.selectFirst("span#averagerate")?.text()?.toFloatOrNull()
                ?: 0f) * 200).toInt()

            val peopleVotedText =
                document.selectFirst("span#countrate")?.text()
            // Turn K to thousands, 9.3k -> 2 zeroes | 95K -> 3 zeroes
            peopleVoted =
                peopleVotedText?.replace("K", if (peopleVotedText.contains(".")) "00" else "000")
                    ?.replace(".", "")
                    ?.toIntOrNull() ?: 0
            setStatus(document.selectFirst("div.manga-status")?.text())
        }
    }
}
