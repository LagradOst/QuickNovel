package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup

class ScribblehubProvider : MainAPI() {
    override val name: String
        get() = "Scribblehub"
    override val mainUrl: String
        get() = "https://www.scribblehub.com"

    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type=fictionposts"
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        val items = document.select("div.search_main_box")
        val returnValue = ArrayList<SearchResponse>()
        for (item in items) {
            val img = item.selectFirst("> div.search_img > img").attr("src")
            val body = item.selectFirst("> div.search_body > div.search_title > a")
            val title = body.text()
            val href = body.attr("href")
            returnValue.add(SearchResponse(title, href, img, null, null, this.name))
        }
        return returnValue
    }

    override fun load(url: String): LoadResponse {
        val id = Regex("series/([0-9]*?)/")
            .find(url)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: throw ErrorLoadingException("Error getting Id of $url")

        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)

        val listResponse = khttp.post(
            "https://www.scribblehub.com/wp-admin/admin-ajax.php",
            data = mapOf("action" to "wi_getreleases_pagination", "pagenum" to "1", "mypostid" to "$id"),
            cookies = mapOf("toc_show" to "10000", "toc_sorder" to "asc")
        )

        val doc = Jsoup.parse(listResponse.text)
        val items = doc.select("ol.toc_ol > li")
        var index = 0
        val data = items.map {
            index++
            val aHeader = it.selectFirst("> a")
            val href = aHeader.attr("href")
            val date = it.selectFirst("> span").text()
            val chapterName = aHeader.ownText()
            ChapterData(
                if(chapterName.isNullOrBlank()) "Chapter $index" else chapterName,
                href,
                date,
                null
            )
        }

        val poster = document.selectFirst("div.fic_image > img").attr("src")
        val title = document.selectFirst("div.fic_title").text()
        val synopsis = document.selectFirst("div.wi_fic_desc").text()
        val genres = document.select("span.wi_fic_genre > span > a.fic_genre").map { it.text() }
        //val tags = document.select("span.wi_fic_showtags > span.wi_fic_showtags_inner > a").map { it.text() }
        val ratings = document.select("span#ratefic_user > span > span")
        val ratingEval = ratings.first()?.text()?.toFloatOrNull()?.times(200)?.toInt()
        val ratingsTotal = ratings?.get(1)?.selectFirst("> span")?.text()?.replace(" ratings", "")?.toIntOrNull()
        val author = document.selectFirst("span.auth_name_fic")?.text()

        val statusSpan = document.selectFirst("ul.widget_fic_similar > li > span").lastElementSibling().ownText()
        val status = when {
            statusSpan.contains("Hiatus") -> STATUS_PAUSE
            statusSpan.contains("Ongoing") -> STATUS_ONGOING
            else -> STATUS_NULL
        }
        return LoadResponse(url, title, data, author, poster, ratingEval, ratingsTotal, null, synopsis, genres, status)
    }

    override fun loadHtml(url: String): String? {
        val response = khttp.get(url)

        val document = Jsoup.parse(response.text)
        return document
            .selectFirst("div#chp_raw")
            .html()
    }
}