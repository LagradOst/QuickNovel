package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import kotlin.math.roundToInt

class PawReadProver : MainAPI() {
    override val name = "PawRead"
    override val mainUrl = "https://pawread.com"
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?keywords=$query"
        val document = app.get(url).document
        return document.select(".list-comic-thumbnail").mapNotNull { select ->
            val node = select.selectFirst(".caption>h3>a") ?: return@mapNotNull null
            val href = node.attr("href") ?: return@mapNotNull null
            newSearchResponse(
                name = node.text(),
                url = href
            ) {
                posterUrl = fixUrlNull(select.selectFirst(".image-link>img")?.attr("src"))
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        //val comic = document.selectFirst(".comic-view")
        val board = document.selectFirst("#tab1_board")!!
        val regex = Regex("'(\\d+)'")
        val prefix = "$url/".replace("//", "/")
        return newStreamResponse(
            name = board.selectFirst("div>h1")!!.text(),
            url = url,
            data = document.select(".item-box").map { select ->
                newChapterData(
                    name = select.selectFirst("div>span.c_title")!!.text(),
                    url = "$prefix${regex.find(select.attr("onclick"))!!.groupValues[1]}.html"
                )
            }) {
            val info = document.select("#views_info>div")
            views = info[1].text().trim().removeSuffix("Views").trimEnd().toIntOrNull()
            author = info[3].text()
            rating = document.select(".comic-score>span").getOrNull(1)?.text()?.toFloatOrNull()
                ?.times(1000.0 / 5.0)?.roundToInt()
            peopleVoted = document.selectFirst("#scoreCount")?.text()?.toIntOrNull()
            tags = document.select(".tags").map { it.text().trim().removePrefix("#").trim() }
            synopsis = document.selectFirst("#simple-des")?.text()
            val attr = board.selectFirst(">.col-md-3>div")
            posterUrl =
                fixUrlNull(
                    Regex("image:url\\((.*)\\)").find(
                        attr?.attr("style") ?: ""
                    )?.groupValues?.get(1)
                )
        }
    }

    override suspend fun loadHtml(url: String): String? {
        return app.get(url).document.selectFirst("#chapter_item")!!.html()
    }
}