package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup

class ScribblehubProvider : MainAPI() {
    override val rateLimitTime: Long = 1000L
    override val name = "Scribblehub"
    override val iconId = R.drawable.icon_scribblehub
    override val iconBackgroundId = R.color.white
    override val mainUrl = "https://www.scribblehub.com"
    override val usesCloudFlareKiller = true
    override val hasMainPage = true

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/?pg=$page"
        val document = app.get(url).document
        val returnValue = document.select("table#main_releases > tbody > tr, table#main_releases > tr").mapNotNull { select ->
            val a = select.selectFirst("a.fp_title") ?: return@mapNotNull null
            newSearchResponse(
                name = a.text(),
                url = a.attr("href")
            ) {
                posterUrl = fixUrlNull(select.selectFirst("img")?.attr("src"))
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type=fictionposts"
        val document = app.get(url).document
        return document.select("div.search_main_box").mapNotNull { item ->
            val img = item.selectFirst("> div.search_img > img")?.attr("src")
            val body = item.selectFirst("> div.search_body > div.search_title > a")
            val title = body?.text() ?: return@mapNotNull null
            val href = body.attr("href")
            SearchResponse(title, href, img, null, null, this.name)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val id = Regex("series/([0-9]*?)/")
            .find(url)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: throw ErrorLoadingException("Error getting Id of $url")

        val response = app.get(url)
        val document = Jsoup.parse(response.text)

        val doc = app.post(
            "$mainUrl/wp-admin/admin-ajax.php",
            data = mapOf(
                "action" to "wi_getreleases_pagination",
                "pagenum" to "1",
                "mypostid" to "$id"
            ),
            cookies = mapOf("toc_show" to "10000", "toc_sorder" to "asc")
        ).document
        val items = doc.select("ol.toc_ol > li")
        val data = items.mapIndexedNotNull { index, element ->
            val aHeader = element.selectFirst("> a")
            val href = aHeader?.attr("href")
            val date = element.selectFirst("> span")?.text()
            val chapterName = aHeader?.ownText()
            newChapterData(
                name = if (chapterName.isNullOrBlank()) "Chapter $index" else chapterName,
                url = href ?: return@mapIndexedNotNull null
            ) {
                dateOfRelease = date
            }
        }

        val title = document.selectFirst("div.fic_title")?.text()

        return newStreamResponse(
            url = url,
            name = title ?: throw ErrorLoadingException("invalid name"),
            data = data
        ) {
            posterUrl = fixUrlNull(document.selectFirst("div.fic_image > img")?.attr("src"))
            synopsis = document.selectFirst("div.wi_fic_desc")?.text()
            val ratings = document.select("span#ratefic_user > span > span")
            tags = document.select("span.wi_fic_genre > span > a.fic_genre").map { it.text() }
            rating = ratings.first()?.text()?.toFloatOrNull()?.times(200)?.toInt()
            peopleVoted =
                ratings.getOrNull(1)?.selectFirst("> span")?.text()?.replace(" ratings", "")
                    ?.toIntOrNull()
            author = document.selectFirst("span.auth_name_fic")?.text()
            val statusSpan =
                document.selectFirst("ul.widget_fic_similar > li > span")?.lastElementSibling()
                    ?.ownText()
            setStatus(statusSpan?.substringBefore("-"))
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        return document
            .selectFirst("div#chp_raw")
            ?.html()
    }
}
