package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList

class WuxiaWorldSiteProvider : MainAPI() {
    override val name = "WuxiaWorldSite"
    override val mainUrl = "https://wuxiaworld.site"

    override val hasMainPage = true
    override val iconId = R.drawable.big_icon_wuxiaworldsite
    override val iconBackgroundId = R.color.wuxiaWorldSiteColor

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

    override val orderBys: ArrayList<Pair<String, String>>
        get() = arrayListOf(
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
            "" -> "novel-list"
            null -> "novel-list"
            "completed" -> "tag/$tag"
            else -> "genre/$tag"
        }
        val url =
            "$mainUrl/$order/page/$page/${if (orderBy == null || orderBy == "") "" else "?m_orderby=$orderBy"}"

        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        //""div.page-content-listing > div.page-listing-item > div > div > div.page-item-detail"
        val headers = document.select("div.page-item-detail")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())

        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val imageHeader = h?.selectFirst("div.item-thumb > a")
            val name = imageHeader?.attr("title")
            if (name?.contains("Comic") != false) continue // I DON'T WANT MANGA!

            val cUrl = imageHeader.attr("href") ?: continue
            val posterUrl = imageHeader.selectFirst("> img")?.attr("src")
            val sum = h.selectFirst("div.item-summary")
            val rating =
                (sum?.selectFirst("> div.rating > div.post-total-rating > span.score")?.text()
                    ?.toFloat()?.times(200))?.toInt()
            val latestChap =
                sum?.selectFirst("> div.list-chapter > div.chapter-item > span > a")?.text()
            returnValue.add(SearchResponse(name, cUrl, posterUrl, rating, latestChap, this.name))
        }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val res = document.selectFirst("div.text-left")
        res?.select("script")?.remove()
        val html = res?.html() ?: return null

        return html
            .replace("Read latest Chapters at WuxiaWorld.Site Only", "") // FUCK ADS
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val response = app.get("$mainUrl/?s=$query&post_type=wp-manga")

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.c-tabs-item > div.c-tabs-item__content")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val head = h?.selectFirst("> div > div.tab-summary")
            val title = head?.selectFirst("> div.post-title > h3 > a")
            val name = title?.text()

            if (name?.contains("Comic") != false) continue // I DON'T WANT MANGA!

            val url = title.attr("href") ?: continue

            val posterUrl = h.selectFirst("> div > div.tab-thumb > a > img")?.attr("src")

            val meta = h.selectFirst("> div > div.tab-meta")

            val ratingTxt =
                meta?.selectFirst("> div.rating > div.post-total-rating > span.total_votes")?.text()

            val rating = if (ratingTxt != null) {
                (ratingTxt.toFloat() * 200).toInt()
            } else {
                null
            }

            val latestChapter = meta?.selectFirst("> div.latest-chap > span.chapter > a")?.text()
            returnValue.add(SearchResponse(name, url, posterUrl, rating, latestChapter, this.name))
        }
        return returnValue

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
            val atter = a?.attr("href") ?: continue
            if (atter.length > "$mainUrl/manga-author/".length && atter.startsWith("$mainUrl/manga-author/")) {
                author = a.text()
                break
            }
        }

        val posterUrl = document.select("div.summary_image > a > img").attr("src")

        val tags: ArrayList<String> = ArrayList()
        val tagsHeader = document.select("div.genres-content > a")
        for (t in tagsHeader) {
            tags.add(t.text())
        }

        var synopsis = ""
        val synoParts = document.select("div.summary__content > p")
        for (s in synoParts) {
            if (s.hasText() && !s.text().lowercase(Locale.getDefault())
                    .contains("wuxiaworld.site")
            ) { // FUCK ADS
                synopsis += s.text() + "\n\n"
            }
        }

        val chapterUrl = "${url}ajax/chapters/"
        // CLOUDFLARE CAPTCHA
        val chapterRes = app.post(chapterUrl).text
        val chapterDoc = Jsoup.parse(chapterRes)

        val data: ArrayList<ChapterData> = ArrayList()
        val chapterHeaders = chapterDoc.select("ul.version-chap > li.wp-manga-chapter")
        chapterHeaders.mapNotNull { c ->
            val header = c?.selectFirst("> a")
            val cUrl = header?.attr("href")
            val cName = header?.text()?.replace("  ", " ")?.replace("\n", "")
                ?.replace("\t", "")
            val added = c?.selectFirst("> span.chapter-release-date > i")?.text()
            data.add(
                ChapterData(
                    cName ?: return@mapNotNull null,
                    cUrl ?: return@mapNotNull null,
                    added,
                    0
                )
            )
        }
        data.reverse()

        val rating =
            ((document.selectFirst("span#averagerate")?.text()?.toFloat() ?: 0f) * 200).toInt()
        val peopleVoted = document.selectFirst("span#countrate")?.text()?.toInt() ?: 0

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
