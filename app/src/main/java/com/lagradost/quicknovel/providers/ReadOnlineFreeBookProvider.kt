package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse

class ReadOnlineFreeBookProvider : MainAPI() {
    override val name = "ReadNovelFreeBook"
    override val mainUrl = "https://readonlinefreebook.com"
    val baseHeaders = mapOf(
        "user-agent" to "Mozilla/5.0",
    )

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val intro = document.selectFirst("div.intro_novel")?:return null
        val title = intro.selectFirst("div.title")?.html()?:""
        val content = intro.selectFirst("div.content_novel")?.html()?:return null
        return "$title<br>$content"
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document =
                app.get("$mainUrl/index/search/q/${
                    query.replace(" ", "%20")
                }").document

        return document.select("div.section-left div.capnhat div.section-bottom div div.item").mapNotNull { parent ->
            val title = parent.selectFirst("div.title")?.text()?.trim() ?: return@mapNotNull null
            val novelUrl = fixUrlNull(parent.selectFirst("div.title a")?.attr("href"))?: return@mapNotNull null
            newSearchResponse(title, novelUrl) {
                posterHeaders = baseHeaders
                posterUrl =  fixUrlNull(parent.selectFirst("div.images a img")?.attr("src"))
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val name =
            document.selectFirst("div.title")?.text() ?: throw ErrorLoadingException("invalid name")
        val chapterData = document.select("div.list_chapter div.list-page-novel table tbody tr")

        val data = chapterData.mapNotNull { c ->
            val a = c.selectFirst("td a")?:return@mapNotNull null
            val cUrl = a.attr("href")
            val cName = a.text()
            newChapterData(cName, cUrl)
        }

        return newStreamResponse(name, url, data) {
            val infoDivs = document.select("div.desc ul")

            author = infoDivs.find { it.text().contains("Author:") }?.selectFirst("a")?.text()
            tags = infoDivs.find { it.text().contains("Category") }?.select("a")?.mapNotNull { it.text().takeIf { t -> t.trim().isNotBlank() } }

            val imgElement = document.selectFirst("div.images img")
            posterUrl = fixUrlNull(imgElement?.attr("src"))
            synopsis = document.selectFirst("div.des_novel")?.text()
        }
    }
}
