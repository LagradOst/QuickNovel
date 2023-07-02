package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.network.CloudflareKiller
import org.jsoup.Jsoup
import kotlin.math.roundToInt

class NovelsOnlineProvider : MainAPI() {
    override val name = "NovelsOnline"
    override val mainUrl = "https://novelsonline.net"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_novelsonline
    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor
    override val usesCloudFlareKiller = true

    override val tags = listOf(
        Pair("All", ""),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Celebrity", "celebrity"),
        Pair("Comedy", "comedy"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Josei", "josei"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Mystery", "mystery"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School Life", "school-life"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shotacon", "shotacon"),
        Pair("Shoujo", "shoujo"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen", "shounen"),
        Pair("Shounen Ai", "shounen-ai"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Sports", "sports"),
        Pair("Supernatural", "supernatural"),
        Pair("Tragedy", "tragedy"),
        Pair("Wuxia", "wuxia"),
        Pair("Xianxia", "xianxia"),
        Pair("Xuanhuan", "xuanhuan"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
    )
    val interceptor = CloudflareKiller()

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/top-novel/$page" else "$mainUrl/category/$tag/$page"

        val document = app.get(url, interceptor = interceptor).document

        val headers = document.select("div.top-novel-block")

        val list = headers.map { h ->
            val adata = h.selectFirst("div.top-novel-header > h2 > a")
            val name = adata!!.text()
            val cUrl = adata.attr("href")
            val posterUrl =
                h.selectFirst("div.top-novel-content > div.top-novel-cover > a > img")!!.attr("src")

            SearchResponse(
                name,
                cUrl,
                posterUrl,
                null,
                null,
                this.name, interceptor.getCookieHeaders(url).toMap()
            )
        }
        return HeadMainPageResponse(url, list)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url, interceptor = interceptor)
        val document =
            Jsoup.parse(response.text.replace("Your browser does not support JavaScript!", ""))
        return document.selectFirst("#contentall")!!.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "$mainUrl/sResults.php",
            interceptor = interceptor,
            data = mapOf("q" to query)
        ).document
        val headers = document.select("li")
        return headers.mapNotNull { h ->
            val name = h.text()
            val cUrl = h.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = h.selectFirst("img")?.attr("src")

            SearchResponse(
                name,
                cUrl,
                posterUrl,
                null,
                null,
                this.name
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {

        val document = app.get(url, interceptor = interceptor).document

        val name = document.selectFirst("h1")!!.text()

        val author =
            document.selectFirst("div.novel-details > div:nth-child(5) > div.novel-detail-body")
                ?.select("li")?.map { it.text() }?.joinToString()
        val tags =
            document.selectFirst("div.novel-details > div:nth-child(2) > div.novel-detail-body")
                ?.select("li")?.map { it.text() }

        val posterUrl = document.select("div.novel-left > div.novel-cover > a > img").attr("src")
        val synopsis =
            document.selectFirst("div.novel-right > div > div:nth-child(1) > div.novel-detail-body")
                ?.text()

        val data = document.select("ul.chapter-chs > li > a").map { c ->
            val cUrl = c.attr("href")
            val cName = c.text()
            ChapterData(cName, cUrl, null, null)
        }


        val rating =
            document.selectFirst("div.novel-right > div > div:nth-child(6) > div.novel-detail-body")
                ?.text()?.toFloatOrNull()?.times(100)?.roundToInt()

        return StreamResponse(
            url,
            name,
            data,
            author,
            fixUrl(posterUrl),
            rating,
            null,
            null,
            synopsis,
            tags,
            null,
        )
    }
}