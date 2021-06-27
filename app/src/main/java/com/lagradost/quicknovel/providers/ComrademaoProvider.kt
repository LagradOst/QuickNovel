package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup

class ComrademaoProvider : MainAPI() {
    override val name: String
        get() = "Comrademao"
    override val mainUrl: String
        get() = "https://comrademao.com"

    override fun search(query: String): ArrayList<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type=novel"
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val items = document.select("div.article-inner-wrapper > div")
        if (items.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (item in items) {
            val divs = item.select("> div")
            val poster = divs[0].selectFirst("> a > img").attr("src")
            val titleHolder = divs[1].selectFirst("> h5 > a")
            val title = titleHolder.text()
            val href = titleHolder.attr("href")
            returnValue.add(SearchResponse(title, href, poster, null, null, this.name))
        }
        return returnValue
    }

    override fun loadHtml(url: String): String? {
        return try {
            val response = khttp.get(url)
            val document = Jsoup.parse(response.text)
            document.selectFirst("div.entry-content").html()
        } catch (e: Exception) {
            null
        }
    }

    override fun load(url: String): LoadResponse {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val title = document.selectFirst("title").text().replace(" – Comrade Mao", "")
        val poster = document.selectFirst("div#thumbnail > img").attr("src")
        val descript = document.selectFirst("div#Description").ownText()
        val genres = ArrayList(document.select("div#Genre > a").map { it.text() })
        val tags = ArrayList(document.select("div#Tag > a").map { it.text() })
        genres.addAll(tags)
        val author = document.selectFirst("div#Publisher > a").text()
        val status = document.selectFirst("div#Status > a").text()
        val statusInt = when (status) {
            "On-going" -> 1
            "Complete" -> 2
            else -> 0
        }
        val pages = document.select("nav.pagination > div.column > a")
        var biggestPage = 0
        var pageUrl = ""
        for (p in pages) {
            val groups = "(.*?)/page/([0-9]*?)/".toRegex().find(p.attr("href"))?.groupValues
            if (groups != null) {
                val pageNumber = groups[2].toInt()
                if (pageNumber > biggestPage) {
                    biggestPage = pageNumber
                    pageUrl = groups[1] + "/page/"
                }
            }
        }
        val data = Array<List<Pair<String, String>>>(biggestPage) { ArrayList() }
        val pagesIndex = ArrayList<Pair<Int, String>>()
        for (i in 1..biggestPage) {
            pagesIndex.add(Pair(i - 1, pageUrl + i))
        }
        pagesIndex.pmap { subData ->
            val subResponse = khttp.get(subData.second)
            val subDocument = Jsoup.parse(subResponse.text)
            val indexedValues = ArrayList(subDocument.select("div.chapter-list > table > tbody > tr > td > a").map {
                Pair(it.text(), it.attr("href"))
            })
            data[subData.first] = indexedValues
        }
        val endResult = ArrayList<Pair<String, String>>()
        for (i in data) {
            endResult.addAll(i)
        }
        val map = ArrayList(endResult.asReversed().map { ChapterData(it.first, it.second, null, null) })
        return LoadResponse(
            url,
            title,
            map,
            author,
            poster,
            null,
            null,
            null,
            descript,
            genres,
            statusInt,
        )
    }
}