package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ComrademaoProvider : MainAPI() {
    override val name: String
        get() = "Comrademao"
    override val mainUrl: String
        get() = "https://comrademao.com"

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type=novel"
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val items = document.select("div.newbox > ul > li")
        if (items.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (item in items) {
            val poster = item.selectFirst("> a > img").attr("src")
            val titleHolder = item.selectFirst("> div > h3 > a")
            val title = titleHolder.text()
            val href = titleHolder.attr("href")
            returnValue.add(SearchResponse(title, href, poster, null, null, this.name))
        }
        return returnValue
    }

    override fun loadHtml(url: String): String? {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div#content").html()
    }

    override fun load(url: String): LoadResponse {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val novelInfo = document.selectFirst("div#NovelInfo")
        val mainDivs = novelInfo.select("> div.columns > div")

        val title = mainDivs[0].selectFirst("> p").text().replace(" – Comrade Mao", "")
        val poster = mainDivs[0].selectFirst("> img").attr("src")

        val descript = mainDivs?.get(1)?.text()
        var genres: ArrayList<String>? = null
        var tags: ArrayList<String>? = null
        var author: String? = null
        var status: String? = null

        fun handleType(element: Element) {
            val txt = element.ownText()
            when {
                txt.contains("Genre") -> {
                    genres = ArrayList(element.select("> a").map { it.text() })
                }
                txt.contains("Tag") -> {
                    tags = ArrayList(element.select("> a").map { it.text() })
                }
                txt.contains("Publisher") -> {
                    author = element.selectFirst("> a").text()
                }
                txt.contains("Status") -> {
                    status = element.selectFirst("> a").text()
                }
            }
        }

        val types = novelInfo.select("> p")
        for (type in types) {
            handleType(type)
        }

        if (genres == null) {
            genres = tags
        } else {
            genres?.addAll(tags ?: listOf())
        }


        val statusInt = when (status) {
            "On-going" -> STATUS_ONGOING
            "Complete" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }
        val pages = document.select("nav.pagination > ul > li > a")
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
            val indexedValues = ArrayList(subDocument.select("table > tbody > tr > th > a").map {
                Pair(it.text(), it.attr("href"))
            })
            data[subData.first] = indexedValues
        }
        val endResult = ArrayList<Pair<String, String>>()
        for (i in data) {
            endResult.addAll(i)
        }
        val map = endResult.asReversed().map { ChapterData(it.first, it.second, null, null) }
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