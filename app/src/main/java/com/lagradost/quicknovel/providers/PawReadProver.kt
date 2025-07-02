package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import kotlin.math.roundToInt

class PawReadProver : MainAPI() {
    override val name = "PawRead"
    override val mainUrl = "https://pawread.com"
    override val iconId = R.drawable.pawread

    override val hasMainPage = true

    override val mainCategories = listOf(
        "All" to "all-",
        "Completed" to "wanjie-",
        "Ongoing" to "lianzai-",
        "Hiatus" to "hiatus-"
    )

    override val orderBys =
        listOf("Time updated" to "update", "Time Posted" to "post", "Clicks" to "click")

    override val tags = listOf(
        "Fantasy" to "Fantasy",
        "Action" to "Action",
        "Xuanhuan" to "Xuanhuan",
        "Romance" to "Romance",
        "Comedy" to "Comedy",
        "Mystery" to "Mystery",
        "Mature" to "Mature",
        "Harem" to "Harem",
        "Wuxia" to "Wuxia",
        "Xianxia" to "Xianxia",
        "Tragedy" to "Tragedy",
        "Sci-fi" to "Scifi",
        "Historical" to "Historical",
        "Ecchi" to "Ecchi",
        "Adventure" to "Adventure",
        "Adult" to "Adult",
        "Supernatural" to "Supernatural",
        "Psychological" to "Psychological",
        "Drama" to "Drama",
        "Horror" to "Horror",
        "Josei" to "Josei",
        "Mecha" to "Mecha",
        "Shounen" to "Shounen",
        "Smut" to "Smut",
        "Martial Arts" to "MartialArts",
        "School Life" to "SchoolLife",
        "Slice of Life" to "SliceofLife",
        "Gender Bender" to "GenderBender",
        "Sports" to "Sports",
        "Urban" to "Urban",
        "LitRPG" to "LitRPG",
        "Isekai" to "Isekai"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/list/${mainCategory ?: "all-"}${tag ?: "All"}/${orderBy ?: "update"}/?page=$page"
        val document = app.get(url).document
        return HeadMainPageResponse(
            url,
            list = document.select(".list-comic-thumbnail").mapNotNull { select ->
                val node = select.selectFirst(".caption>h3>a") ?: return@mapNotNull null
                val href = node.attr("href") ?: return@mapNotNull null
                newSearchResponse(
                    name = node.text(),
                    url = href
                ) {
                    posterUrl = fixUrlNull(select.selectFirst(".image-link>img")?.attr("src"))
                }
            })
    }

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
            data = document.select(".item-box").filter { select ->
                select.selectFirst("div>svg") == null
            }.map { select ->
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
        val document = app.get(url).document
        val count = document.selectFirst("#countdown")
        if (count != null) {
            throw ErrorLoadingException("Not released, time until released ${count.text()}")
        }
        val html = document.selectFirst("#chapter_item")!!.html()
        return html
    }
}