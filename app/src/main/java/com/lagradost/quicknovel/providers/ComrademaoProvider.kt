package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ComrademaoProvider : MainAPI() {
    override val name = "Comrademao"
    override val mainUrl = "https://comrademao.com"

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type=novel"
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val items = document.select(".bs")
        return items.mapNotNull {
            val titleHolder = it.selectFirst("a")
            val title = titleHolder?.text() ?: return@mapNotNull null
            val href = titleHolder.attr("href")
            newSearchResponse(name = title, url = href ?: return@mapNotNull null) {
                posterUrl = it.selectFirst("img")?.attr("src")
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div[readability]")?.html()
            ?.replace("(end of this chapter)", "", ignoreCase = true)
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val novelInfo = document.selectFirst("div.thumb > img")
        val mainDivs = document.select("div.infox")

        val title = novelInfo?.attr("title")?.replace(" – Comrade Mao", "") ?: return null

        var genres: ArrayList<String>? = null
        var tags: ArrayList<String>? = null
        var author: String? = null
        var status: String? = null

        fun handleType(element: Element) {
            val txt = element.text()
            when {
                txt.contains("Genre") -> {
                    genres = ArrayList(element.select("a").map { it.text() })
                }
                txt.contains("Tag") -> {
                    tags = ArrayList(element.select("a").map { it.text() })
                }
                txt.contains("Publisher") -> {
                    author = element.selectFirst("a")?.text()
                }
                txt.contains("Status") -> {
                    status = element.selectFirst("a")?.text()
                }
            }
        }

        mainDivs.select(".wd-full").forEach(::handleType)

        if (genres == null) {
            genres = tags
        } else {
            genres?.addAll(tags ?: listOf())
        }

        val chapters = document.select("li[data-num]").mapNotNull {
            val name = it.select(".chapternum").text() ?: return@mapNotNull null
            val chapUrl = it.select("a").attr("href")
            newChapterData(name = name, url = chapUrl) {
                dateOfRelease = it.select(".chapterdate").text()
            }
        }.reversed()

        return newStreamResponse(url = url, name = title, data = chapters) {
            this.status = when (status) {
                "On-going" -> STATUS_ONGOING
                "Complete" -> STATUS_COMPLETE
                else -> STATUS_NULL
            }
            this.author = author
            synopsis = document.select("div.wd-full p").lastOrNull()?.text()
            posterUrl = fixUrlNull(novelInfo.attr("src"))
            this.tags = (tags ?: emptyList()) + (genres ?: emptyList())
        }
    }
}