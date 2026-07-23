package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import org.jsoup.nodes.Document

class ReadhiveProvider  :  MainAPI() {
    override val name = "ReadHive"
    override val mainUrl = "https://readhive.org"
    override val iconId = R.drawable.icon_readhive
    override val hasMainPage = true
    override val rateLimitTime = 500L

    override val tags = listOf(
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "BL" to "bl",
        "Boy's Love" to "boy's-love",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Fantasy" to "fantasy",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Reincarnation" to "reincarnation",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-Fi" to "sci-fi",
        "Shoujo" to "shoujo",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val hasTag = !tag.isNullOrBlank()
        val tagPath = if (hasTag) "genre/$tag/" else ""

        val url = "$mainUrl/${tagPath}page/$page/"
        val document = app.get(url).document
        val returnValue = parsePageCards(document).toMutableList()

        if (hasTag && page == 1) {
            val nextUrl = "$mainUrl/${tagPath}page/2/"
            val nextDocument = app.get(nextUrl).document
            val secondPageCards = parsePageCards(nextDocument)
            returnValue.addAll(secondPageCards)
        }

        return HeadMainPageResponse(url, returnValue)
    }

    private fun parsePageCards(document: Document): List<SearchResponse> {
        return document.select("a.peer, a.col-span-2").mapNotNull { card ->
            val href = card?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("img")?.attr("alt") ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(card.selectFirst("img")?.attr("src"))
            }
        }
    }


    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document//body > div.body > div > div > div.col-lg-8
        val infoDiv = document.selectFirst("main")?: return newStreamResponse(url, url, emptyList())

        val title = infoDiv.selectFirst("h1")?.text() ?: throw Exception("Title not found")

        val chapters = document.select(
            "section.relative.grid.grid-cols-1.lg\\:grid-areas-series__body.lg\\:grid-cols-series.gap-x-4.px-4.py-2.sm\\:px-8 > div.lg\\:grid-in-content.mt-4 > div:nth-child(1) > div:nth-child(3) > div > div > a"
        ).mapNotNull { li ->
            if(li.selectFirst("> span") != null) return@mapNotNull null
            val name = li.selectFirst("div > div > span")?.text()?:return@mapNotNull null
            val url = li.attr("href")?:return@mapNotNull null
            newChapterData(name, url)
        }.reversed()

        return newStreamResponse(title,fixUrl(url), chapters) {
            this.posterUrl = fixUrlNull(infoDiv.selectFirst("img.object-cover")?.attr("src"))
            this.synopsis = document.select(
                "section.relative.grid.grid-cols-1.lg\\:grid-areas-series__body.lg\\:grid-cols-series.gap-x-4.px-4.py-2.sm\\:px-8 div.mb-4 > p"
            ).joinToString("\n") { p -> p.text() }

            this.author = infoDiv.selectFirst("span.leading-7")?.text() ?: ""

            this.tags = infoDiv.select("section.relative.grid.grid-cols-1.lg\\:grid-areas-series__body.lg\\:grid-cols-series.gap-x-4.px-4.py-2.sm\\:px-8 > div.lg\\:grid-in-info > div > a").mapNotNull {
                it.text().trim().takeIf { text ->  !text.isEmpty() }
            }
            related = getRelated(document)
        }
    }

    private fun getRelated(dc: Document): List<SearchResponse>{
        return dc.select("div.swiper > div.swiper-wrapper > div.swiper-slide").mapNotNull { element ->
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("h6")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(element.selectFirst("img")?.attr("src"))
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val reader = document.selectFirst("main > div.justify-center.flex-grow.mx-auto.prose.md\\:max-w-4xl.lg\\:relative") ?: return null
        return reader.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "$mainUrl/ajax", data = mapOf(
                "query" to query,
                "action" to "search"
            )
        ).parsed<Root>()
        return document.data.map { card ->
            val href = card.url.replace("\\","")
            val title = card.title

            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(card.thumb.replace("\\",""))
            }

        }
    }

    data class Root(
        @JsonProperty("success")
        val success: Boolean,
        @JsonProperty("data")
        val data: List<Post>,
    )

    data class Post(
        @JsonProperty("title")
        val title: String,
        @JsonProperty("thumb")
        val thumb: String,
        @JsonProperty("url")
        val url: String,
    )
}