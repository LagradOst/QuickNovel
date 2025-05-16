package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup

class ScribblehubProvider : MainAPI() {
    override val rateLimitTime: Long = 500L
    override val name = "Scribblehub"
    override val mainUrl = "https://www.scribblehub.com"

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type=fictionposts"
        val document = app.get(url).document
        return document.select("div.search_main_box").mapNotNull { item ->
            val img = item.selectFirst("> div.search_img > img")?.attr("src")
            val body = item.selectFirst("> div.search_body > div.search_title > a")
            val title = body?.text() ?: return@mapNotNull null
            val href = body.attr("href") ?: return@mapNotNull null
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

        val listResponse = app.post(
            "https://www.scribblehub.com/wp-admin/admin-ajax.php",
            data = mapOf(
                "action" to "wi_getreleases_pagination",
                "pagenum" to "1",
                "mypostid" to "$id"
            ),
            cookies = mapOf("toc_show" to "10000", "toc_sorder" to "asc")
        )

        val doc = Jsoup.parse(listResponse.text)
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

        //val tags = document.select("span.wi_fic_showtags > span.wi_fic_showtags_inner > a").map { it.text() }

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
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        return document
            .selectFirst("div#chp_raw")
            ?.html()
    }
}