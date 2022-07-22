package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class WuxiaWorldOnlineProvider : MainAPI() {
    override val mainUrl: String get() = "https://wuxiaworld.online"
    override val name: String get() = "WuxiaWorldOnline"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_wuxiaworldonline

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", ""),
        Pair("Chinese", "Chinese"),
        Pair("Action", "Action"),
        Pair("Adventure", "Adventure"),
        Pair("Fantasy", "Fantasy"),
        Pair("Martial Arts", "Martial Arts"),
        Pair("Romance", "Romance"),
        Pair("Xianxia", "Xianxia"),
        //Pair("Editor's choice", "Editor's choice"),
        Pair("Original", "Original"),
        Pair("Korean", "Korean"),
        Pair("Comedy", "Comedy"),
        Pair("Japanese", "Japanese"),
        Pair("Xuanhuan", "Xuanhuan"),
        Pair("Mystery", "Mystery"),
        Pair("Supernatural", "Supernatural"),
        Pair("Drama", "Drama"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Sci-Fi", "Sci-Fi"),
        Pair("Thriller", "Thriller"),
        Pair("Futuristic", "Futuristic"),
        Pair("Academy", "Academy"),
        Pair("Completed", "Completed"),
        Pair("Harem", "Harem"),
        Pair("School Life", "Schoollife"),
        Pair("Martial Arts", "Martialarts"),
        Pair("Slice of Life", "Sliceoflife"),
        Pair("English", "English"),
        Pair("Reincarnation", "Reincarnation"),
        Pair("Psychological", "Psychological"),
        Pair("Sci-fi", "Scifi"),
        Pair("Mature", "Mature"),
        Pair("Ghosts", "Ghosts"),
        Pair("Demons", "Demons"),
        Pair("Gods", "Gods"),
        Pair("Cultivation", "Cultivation"),
    )

    override val orderBys: List<Pair<String, String>>
        get() = listOf(
            Pair("Recents Updated", "recents"),
            Pair("Most Viewed", "popularity"),
            Pair("Lastest Releases", "date_added"),
        )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/wuxia-list?sort=$orderBy&genres_include=$tag&page=$page" // TAGS
        val response = app.get(url)

        val document = Jsoup.parse(response.text)

        val headers = document.select("div.manga-grid > div.itemupdate")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val a = h?.selectFirst("> a")
            val cUrl = a?.attr("href") ?: continue

            val name = a.attr("title") ?: continue
            val posterUrl = a.selectFirst("> img")?.attr("src")

            val latestChap = h.select("> ul > li")[1].selectFirst("> span > a")?.text()
            returnValue.add(
                SearchResponse(
                    name,
                    fixUrl(cUrl),
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
        val document = Jsoup.parse(response.text)
        val res = document.selectFirst("div.content-area")
        res?.allElements?.forEach { i ->
            val style = i?.attr("style")
            if (style?.contains("display:none") == true) { // FUCKERS ADDS BLOAT TO SITE BUT DOES NOT DISPLAY IT
                i.remove()
            }
        }

        // FUCK ADS
        return res?.html()
            ?.replace("(~|)( |)wuxiaworld\\.online( |)(~|)".toRegex(), "")
            ?.replace("UU reading www.uukanshu.com", "")
            ?.replace("Do you like this site? Donate here:", "")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response =
            app.get("https://wuxiaworld.online/search.ajax?type=&query=$query") // AJAX, MIGHT ADD QUICK SEARCH

        val document = Jsoup.parse(response.text)
        val headers = document.select("ul > li")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val hInfo = h?.selectFirst("> span > a")

            val name = hInfo?.text() ?: continue
            val url = hInfo.attr("href") ?: continue

            val posterUrl = h.selectFirst("> img")?.attr("src")

            val latestChapter = h.selectFirst("> span > a > span")?.text()
            returnValue.add(
                SearchResponse(
                    name,
                    url,
                    fixUrlNull(posterUrl),
                    null,
                    latestChapter,
                    this.name
                )
            )
        }
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val infoHeaders = document.select("ul.truyen_info_right > li")
        fun getInfoHeader(startWidth: String): Element? {
            for (a in infoHeaders) {
                val sel = a?.selectFirst("> span")
                if (sel != null && sel.hasText() && sel.text().startsWith(startWidth)) return a
            }
            return null
        }

        val name = document.selectFirst("li > h1.entry-title")?.text() ?: return null

        val auth = getInfoHeader("Author")
        var author: String? = null

        if (auth != null) {
            author = auth.selectFirst("a")?.text()
        }

        val posterUrl = document.select("span.info_image > img").attr("src")

        val tags: ArrayList<String> = ArrayList()

        val gen = getInfoHeader("Genres")
        if (gen != null) {
            val tagsHeader = gen.select("> a")
            for (t in tagsHeader) {
                tags.add(t.text())
            }
        }

        val synopsis = document.select("div.entry-header > div")[1].text()

        val data: ArrayList<ChapterData> = ArrayList()
        val chapterHeaders = document.select("div.chapter-list > div")
        for (c in chapterHeaders) {
            val spans = c.select("> span")
            val text = spans[0].selectFirst("> a")
            val cUrl = text?.attr("href") ?: continue
            val cName = text.text() ?: continue
            val added = spans[1].text()
            val views = null
            data.add(ChapterData(cName, cUrl, added, views))
        }
        data.reverse()

        var rating = 0
        var peopleVoted = 0
        try {
            rating = (document.selectFirst("span#averagerate")?.text()!!.toFloat() * 200).toInt()

            peopleVoted = document.selectFirst("span#countrate")?.text()!!.toInt()
        } catch (e: Exception) {
            // NO RATING
        }

        val viewHeader = getInfoHeader("Views")
        var views: Int? = null

        if (viewHeader != null) {
            var hString = viewHeader.text().replace(",", ".")
                        .replace("\"", "")
                        .substring("View : ".length).lowercase(Locale.getDefault())

            var multi = 1
            if (hString.contains('k')) { // YE THIS CAN BE IMPROVED
                multi = 1000
                hString = hString.substring(0, hString.indexOf('k') - 1)
            }
            if (hString.contains('m')) {
                multi = 1000000
                hString = hString.substring(0, hString.indexOf('m') - 1)
            }

            views = (hString.toFloat() * multi).toInt()
        }

        val statusHeader = getInfoHeader("Status")
        val status = if (statusHeader == null) null else
            when (statusHeader.selectFirst("> a")?.text()
                ?.lowercase()) {
                "ongoing" -> STATUS_ONGOING
                "completed" -> STATUS_COMPLETE
                else -> STATUS_NULL
            }

        return LoadResponse(
            url,
            name,
            data,
            author,
            fixUrlNull(posterUrl),
            rating,
            peopleVoted,
            views,
            synopsis,
            tags,
            status
        )
    }
}