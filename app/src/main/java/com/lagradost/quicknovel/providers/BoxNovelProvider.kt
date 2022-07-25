package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import java.util.*

class BoxNovelProvider : MainAPI() {
    override val name = "BoxNovel"
    override val mainUrl = "https://boxnovel.com"
    override val iconId = R.drawable.big_icon_boxnovel

    override val hasMainPage = true

    override val iconBackgroundId = R.color.boxNovelColor

    override val tags = listOf(
        Pair("All", ""),
        Pair("Completed", "completed"),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Drama", "genre"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Harem", "harem"),
        Pair("Josei", "josei"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Mystery", "mystery"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School Life", "school-life"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Sports", "sports"),
        Pair("Supernatural", "supernatural"),
        Pair("Tragedy", "tragedy"),
        Pair("Wuxia", "wuxia"),
        Pair("Xianxia", "xianxia"),
        Pair("Xuanhuan", "xuanhuan"),
    )

    override val orderBys: List<Pair<String, String>>
        get() = listOf(
            Pair("Nothing", ""),
            Pair("New", "new-manga"),
            Pair("Most Views", "views"),
            Pair("Trending", "trending"),
            Pair("Rating", "rating"),
            Pair("A-Z", "alphabet"),
            Pair("Latest", "latest"),
        )

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

        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        //""div.page-content-listing > div.page-listing-item > div > div > div.page-item-detail"
        val headers = document.select("div.page-item-detail")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())

        val returnValue = headers.mapNotNull { h ->
            val imageHeader = h?.selectFirst("div.item-thumb > a")
            val name = imageHeader?.attr("title")
            if (name?.contains("Comic") != false) return@mapNotNull null// I DON'T WANT MANGA!

            val cUrl = imageHeader.attr("href")
            val posterUrl = imageHeader.selectFirst("> img")?.attr("data-src")
            val sum = h.selectFirst("div.item-summary")
            val rating =
                (sum?.selectFirst("> div.rating > div.post-total-rating > span.score")?.text()
                    ?.toFloat()?.times(200))?.toInt()
            val latestChap =
                sum?.selectFirst("> div.list-chapter > div.chapter-item > span > a")?.text()
            SearchResponse(
                name,
                cUrl ?: return@mapNotNull null,
                posterUrl,
                rating,
                latestChap,
                this.name
            )
        }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
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

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/?s=$query&post_type=wp-manga")

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.c-tabs-item__content")
        if (headers.size <= 0) return ArrayList()
        return headers.mapNotNull { h ->
            val head = h?.selectFirst("> div > div.tab-summary")
            val title = head?.selectFirst("> div.post-title > h3 > a")
            val name = title?.text()

            if (name?.contains("Comic") != false) return@mapNotNull null// I DON'T WANT MANGA!

            val url = title.attr("href")

            val posterUrl = h.selectFirst("> div > div.tab-thumb > a > img")?.attr("data-src")

            val meta = h.selectFirst("> div > div.tab-meta")

            val ratingTxt =
                meta?.selectFirst("> div.rating > div.post-total-rating > span.total_votes")?.text()

            val rating = if (ratingTxt != null) {
                (ratingTxt.toFloat() * 200).toInt()
            } else {
                null
            }

            val latestChapter = meta?.selectFirst("> div.latest-chap > span.chapter > a")?.text()
            SearchResponse(
                name,
                url ?: return@mapNotNull null,
                posterUrl,
                rating,
                latestChapter,
                this.name
            )
        }
    }

    fun getChapters(text: String): List<ChapterData> {
        val document = Jsoup.parse(text)
        val data: ArrayList<ChapterData> = ArrayList()
        val chapterHeaders = document.select("ul.version-chap > li.wp-manga-chapter")
        for (c in chapterHeaders) {
            val header = c?.selectFirst("> a")
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
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val name =
            document.selectFirst("div.post-title > h1")?.text()?.replace("  ", " ")
                ?.replace("\n", "")
                ?.replace("\t", "") ?: return null
        val authors = document.select("div.author-content > a")
        var author = ""
        for (a in authors) {
            val atter = a?.attr("href")
            if ((atter?.length
                    ?: continue) > "$mainUrl/manga-author/".length && atter.startsWith("$mainUrl/manga-author/")
            ) {
                author = a.text()
                break
            }
        }

        val posterUrl = document.select("div.summary_image > a > img").attr("data-src")

        val tags: ArrayList<String> = ArrayList()
        val tagsHeader = document.select("div.genres-content > a")
        for (t in tagsHeader) {
            tags.add(t.text())
        }

        var synopsis = ""
        var synoParts = document.select("#editdescription > p")
        if (synoParts.size == 0) synoParts = document.select("div.j_synopsis > p")
        if (synoParts.size == 0) synoParts = document.select("div.summary__content > p")
        for (s in synoParts) {
            if (s.hasText() && !s.text().lowercase(Locale.getDefault())
                    .contains(mainUrl)
            ) { // FUCK ADS
                synopsis += s.text() + "\n\n"
            }
        }

        //val id = WuxiaWorldSiteProvider.getId(response.text) ?: throw ErrorLoadingException("No id found")
        //ajax/chapters/
        val chapResponse = app.post(
            "${url}ajax/chapters/",
        )
        val data = getChapters(chapResponse.text)

        val rating = ((document.selectFirst("span#averagerate")?.text()?.toFloatOrNull()
            ?: 0f) * 200).toInt()

        val peopleVotedText =
            document.selectFirst("span#countrate")?.text()

        // Turn K to thousands, 9.3k -> 2 zeroes | 95K -> 3 zeroes
        val peopleVoted =
            peopleVotedText?.replace("K", if (peopleVotedText.contains(".")) "00" else "000")
                ?.replace(".", "")
                ?.toIntOrNull() ?: 0

        val views = null

        val aHeaders =
            document.select("div.post-status > div.post-content_item > div.summary-content")
        val aHeader = aHeaders.last()

        val status = when (aHeader?.text()?.lowercase()) {
            "ongoing" -> STATUS_ONGOING
            "completed" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }

        return LoadResponse(
            url,
            name,
            data,
            author,
            posterUrl,
            rating,
            peopleVoted,
            views,
            synopsis,
            tags,
            status
        )
    }
}