package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.network.CloudflareKiller
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.collections.ArrayList

class FanFictionDotNetProvider : MainAPI() {
    override val name = "FanFiction.net"
    override val mainUrl = "https://www.fanfiction.net"

    override val hasMainPage = true

    override val rateLimitTime: Long = 50

    override val iconId = R.drawable.ic_fanfiction

    override val iconBackgroundId = R.color.fanFictionDotNetColor

    override val orderBys = listOf(
        Pair("All Types", "0"),
        Pair("New Stories", "1"),
        Pair("Updated Stories", "2"),
        Pair("New Crossovers", "3"),
        Pair("Updated Crossovers", "4"),
    )
    val interceptor = CloudflareKiller()



    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url =
            "$mainUrl/j/0/$orderBy/0/"
        if (page > 1) return HeadMainPageResponse(
            url,
            ArrayList()
        )

        val document = app.get(url, interceptor = interceptor).document
        val works = document.select("div#content_wrapper a.stitle")
        return HeadMainPageResponse(url, works.map { SearchResponse(
            name = it.text(),
            url = it.attr("href"),
            posterUrl = it.selectFirst("img")?.attr("src"),
            apiName = this.name
        ) })
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?keywords=$query&ready=1&type=story", interceptor = interceptor).document

        val works = document.select("div#content_wrapper a.stitle")
        return works.map { SearchResponse(
            name = it.text(),
            url = it.attr("href"),
            posterUrl = it.selectFirst("img")?.attr("src"),
            apiName = this.name

        ) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get("$url", interceptor = interceptor).document

        val urlInfo = url.split("/").reversed()
        val idName = urlInfo[0]
        val id = urlInfo[2]

        val name = document.selectFirst("div#profile_top > b.xcontrast_txt")?.text().toString()
        val author = document.selectFirst("div#profile_top > a.xcontrast_txt")?.text()
        val poster = document.selectFirst("div#profile_top > div[title=\"Click for Larger Image\"] >img")?.attr("src")

        val synopsis = document.selectFirst("div#profile_top > div.xcontrast_txt")?.text()


        val chapters = document.selectFirst("select#chap_select")

        val data = chapters?.children()?.mapIndexed { i: Int, element: Element ->
            ChapterData(
                name = element.text().toString(),
                url = "$mainUrl/s/$id/$i/$idName"
            )
        }


        return LoadResponse(
            url,
            name,
            data ?: ArrayList(),
            author,
            posterUrl = fixUrlNull(poster),
            synopsis = synopsis,
        )
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url, interceptor = interceptor)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div#storytext")?.html()
    }
}