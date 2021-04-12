package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.SearchResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class WuxiaWorldOnlineProvider : MainAPI() {
    override val mainUrl: String get() = "https://wuxiaworld.online"
    override val name: String get() = "WuxiaWorldOnline"

    override fun loadPage(url: String): String? {
        return try {
            val response = khttp.get(url)
            val document = Jsoup.parse(response.text)
            val res = document.selectFirst("div.content-area")
            if (res.html() == "") {
                return null
            }
            res.html()
        } catch (e: Exception) {
            null
        }
    }

    override fun search(query: String): ArrayList<SearchResponse>? {
        try {
            val response =
                khttp.get("https://wuxiaworld.online/search.ajax?type=&query=$query") // AJAX, TODO MIGHT ADD QUICK SEARCH

            val document = Jsoup.parse(response.text)
            val headers = document.select("ul > li")
            if (headers.size <= 0) return ArrayList()
            val returnValue: ArrayList<SearchResponse> = ArrayList()
            for (h in headers) {
                val hInfo = h.selectFirst("> span > a")

                val name = hInfo.text()
                val url = hInfo.attr("href")

                var posterUrl = h.selectFirst("> img").attr("src")
                if (posterUrl.startsWith('/')) {
                    posterUrl = mainUrl + posterUrl
                }

                val latestChapter = h.selectFirst("> span > a > span").text()
                returnValue.add(SearchResponse(name, url, posterUrl, null, latestChapter, this.name))
            }
            return returnValue
        } catch (e: Exception) {
            return null
        }
    }

    override fun load(url: String): LoadResponse? {
        try {
            val response = khttp.get(url)

            val document = Jsoup.parse(response.text)
            val infoHeaders = document.select("ul.truyen_info_right > li")
            fun getInfoHeader(startWidth: String): Element? {
                for (a in infoHeaders) {
                    val sel = a.selectFirst("> span")
                    if (sel != null && sel.hasText() && sel.text().startsWith(startWidth)) return a
                }
                return null
            }

            val name = document.selectFirst("li > h1.entry-title").text()

            val auth = getInfoHeader("Author")
            var author: String? = null

            if (auth != null) {
                author = auth.selectFirst("a").text()
            }

            var posterUrl = document.select("span.info_image > img").attr("src")
            if (posterUrl.startsWith('/')) {
                posterUrl = mainUrl + posterUrl
            }

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
                val url = text.attr("href")
                val name = text.text()
                val added = spans[1].text()
                val views = null
                data.add(ChapterData(name, url, added, views))
            }
            data.reverse()

            var rating = 0
            var peopleVoted = 0
            try {
                rating = (document.selectFirst("span#averagerate").text().toFloat() * 200).toInt()

                peopleVoted = document.selectFirst("span#countrate").text().toInt()
            } catch (e: Exception) {
                // NO RATING
            }

            val viewHeader = getInfoHeader("Views")
            var views: Int? = null

            if (viewHeader != null) {
                var hString = viewHeader.text().replace(",", ".")
                    .replace("\"", "")
                    .substring("View : ".length).toLowerCase(Locale.getDefault())

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
                when (statusHeader.selectFirst("> a").text()
                    .toLowerCase(Locale.getDefault())) {
                    "ongoing" -> 1
                    "completed" -> 2
                    else -> 0
                }

            return LoadResponse(name, data, author, posterUrl, rating, peopleVoted, views, synopsis, tags, status)
        } catch (e: Exception) {
            return null
        }
    }
}