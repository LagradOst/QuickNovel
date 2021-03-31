package com.example.epubdownloader.providers

import com.example.epubdownloader.ChapterData
import com.example.epubdownloader.LoadResponse
import com.example.epubdownloader.MainAPI
import com.example.epubdownloader.SearchResponse
import org.jsoup.Jsoup
import java.lang.Exception

class BestLightNovelProvider : MainAPI() {
    override val name: String get() = "BestLightNovel"
    override val mainUrl: String get() = "https://bestlightnovel.com"

    override fun loadPage(url: String): String? {
        return try {
            val response = khttp.get(url)
            val document = Jsoup.parse(response.text)
            val res = document.selectFirst("div.vung_doc")
            if(res.html() == "") {
                return null
            }
            res.html()
        } catch (e: Exception) {
            null
        }
    }

    override fun search(query: String): ArrayList<SearchResponse>? {
        try {
            val response = khttp.get("$mainUrl/search_novels/${query.replace(' ', '_')}")

            val document = Jsoup.parse(response.text)
            val headers = document.select("div.danh_sach > div.list_category")
            if (headers.size <= 0) return ArrayList()
            val returnValue: ArrayList<SearchResponse> = ArrayList()
            for (h in headers) {
                val head = h.selectFirst("> a")
                val name = head.attr("title")
                val url = head.attr("href")

                val posterUrl = head.selectFirst("> img").attr("src")

                val rating = null
                val latestChapter = h.selectFirst("> a.chapter").text()
                returnValue.add(SearchResponse(name, url, posterUrl, rating, latestChapter))
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

            val name = infoHeaders[0].selectFirst("> h1").text()
            val authors = infoHeaders[1].select("> a")
            var author = ""
            for (a in authors) {
                val atter = a.attr("href")
                if (a.hasText() && atter.length > "$mainUrl/search_author/".length && atter.startsWith("$mainUrl/search_author/")) {
                    author = a.text()
                    break
                }
            }

            val posterUrl = document.select("span.info_image > img").attr("src")

            val tags: ArrayList<String> = ArrayList()
            val tagsHeader = infoHeaders[2].select("> a")
            for (t in tagsHeader) {
                tags.add(t.text())
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

            val ratingHeader = infoHeaders[9].selectFirst("> em > em").select("> em")
            val rating = (ratingHeader[1].selectFirst("> em > em").text().toFloat() * 200).toInt()

            val peopleVoted = ratingHeader[2].text().replace(",","").toInt()
            val views = infoHeaders[6].text()
                .replace(",", "")
                .replace("\"", "").substring("View : ".length).toInt()

            return LoadResponse(name, data, author, posterUrl, rating, peopleVoted, views, synopsis, tags)
        } catch (e: Exception) {
            return null
        }
    }
}