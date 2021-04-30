package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.SearchResponse
import org.jsoup.Jsoup
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class WuxiaWorldSiteProvider : MainAPI() {
    override val name: String get() = "WuxiaWorldSite"
    override val mainUrl: String get() = "https://wuxiaworld.site"

    override fun loadPage(url: String): String? {
        return try {
            val response = khttp.get(url)
            val document = Jsoup.parse(response.text)
            val res = document.selectFirst("div.text-left")
            if (res.html() == "") {
                return null
            }
            res.html()
                .replace("(adsbygoogle = window.adsbygoogle || []).push({});","")
                .replace("Read latest Chapters at WuxiaWorld.Site Only","") // FUCK ADS
        } catch (e: Exception) {
            null
        }
    }

    override fun search(query: String): ArrayList<SearchResponse>? {
        try {
            val response = khttp.get("$mainUrl/?s=$query&post_type=wp-manga")

            val document = Jsoup.parse(response.text)
            val headers = document.select("div.c-tabs-item > div.c-tabs-item__content")
            if (headers.size <= 0) return ArrayList()
            val returnValue: ArrayList<SearchResponse> = ArrayList()
            for (h in headers) {
                val head = h.selectFirst("> div > div.tab-summary")
                val title = head.selectFirst("> div.post-title > h3 > a")
                val name = title.text()

                if(name.contains("Comic")) continue // I DON'T WANT MANGA!

                val url = title.attr("href")

                val posterUrl = h.selectFirst("> div > div.tab-thumb > a > img").attr("src")

                val meta = h.selectFirst("> div > div.tab-meta")

                val ratingTxt = meta.selectFirst("> div.rating > div.post-total-rating > span.total_votes").text()

                val rating = if (ratingTxt != null) {
                    (ratingTxt.toFloat() * 200).toInt()
                } else {
                    null
                }

                val latestChapter = meta.selectFirst("> div.latest-chap > span.chapter > a").text()
                returnValue.add(SearchResponse(name, url, posterUrl, rating, latestChapter, this.name))
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

            val name = document.selectFirst("div.post-title > h1").text().replace("  ", " ").replace("\n", "")
                .replace("\t", "")
            val authors = document.select("div.author-content > a")
            var author = ""
            for (a in authors) {
                val atter = a.attr("href")
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
                if(s.hasText() && !s.text().toLowerCase(Locale.getDefault()).contains("wuxiaworld.site")) { // FUCK ADS
                    synopsis += s.text()!! + "\n\n"
                }
            }

            val data: ArrayList<ChapterData> = ArrayList()
            val chapterHeaders = document.select("ul.version-chap > li.wp-manga-chapter")
            for (c in chapterHeaders) {
                val header = c.selectFirst("> a")
                val url = header.attr("href")
                val name = header.text().replace("  ", " ").replace("\n", "")
                    .replace("\t", "")
                val added = c.selectFirst("> span.chapter-release-date > i").text()
                data.add(ChapterData(name, url, added, 0))
            }
            data.reverse()

            val rating = ((document.selectFirst("span#averagerate")?.text()?.toFloat() ?: 0f) * 200).toInt()
            val peopleVoted = document.selectFirst("span#countrate")?.text()?.toInt() ?: 0

            val views = null

            val aHeaders = document.select("div.post-status > div.post-content_item > div.summary-content")
            val aHeader = aHeaders.last()

            val status = when (aHeader.text().toLowerCase(Locale.getDefault())) {
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