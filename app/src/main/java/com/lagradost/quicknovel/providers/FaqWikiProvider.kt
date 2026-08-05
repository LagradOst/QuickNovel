package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup

class FaqWikiProvider :  MainAPI() {
    override val name = "FaqWiki"
    override val mainUrl = "https://faqwiki.us/novel"
    override val iconId = R.drawable.icon_faqwiki
    override val iconBackgroundId = R.color.black
    override val hasMainPage = true

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        if (page > 1) return HeadMainPageResponse("", emptyList())
        val document = app.get(mainUrl).document

        val returnValue = document.select("div.plt-page-list > div").mapNotNull { h ->
            val name = h.selectFirst("h3")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = name,
                url = h.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(h.selectFirst("img")?.attr("src"))
            }
        }

        return HeadMainPageResponse(mainUrl, returnValue)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url.replace("xyz","us/novel")).document
        val title = document.selectFirst("h1")?.text() ?: throw ErrorLoadingException("Invalid Name")

        val chapters = document.select("div.entry-content ul.lcp_catlist li, div.entry-content ul li").mapNotNull { li ->
            val a = li.selectFirst("a") ?: return@mapNotNull null
            val name = a.text().trim()
            val href = a.attr("href")
            if (name.isNotEmpty() && href.isNotEmpty())
                newChapterData(name, href)
            else return@mapNotNull null
        }

        return newStreamResponse(title, url, chapters) {
            this.posterUrl = document.selectFirst("figure > img, div.entry-content img")?.attr("src")
            val tags = mutableListOf<String>()

            document.select("div.entry-content p").forEach { p ->
                val text = p.text()
                when {
                    text.startsWith("Description:", ignoreCase = true) -> synopsis = text.removePrefix("Description:").trim()
                    text.startsWith("Author(s):", ignoreCase = true) -> author = text.removePrefix("Author(s):").trim()
                    text.startsWith("Genre:", ignoreCase = true) -> {
                        text.removePrefix("Genre:").split(" ").filter { it.isNotBlank() }.map { it.trim().removeSuffix(",") }.forEach { tags.add(it) }
                    }
                    text.startsWith("Status:", ignoreCase = true) -> {
                        setStatus(text.removePrefix("Status:").trim())
                    }
                }
            }
            this.tags = tags
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val res = document.selectFirst("main > article > div")
        res?.select("script, div")?.remove()
        return res?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "${mainUrl}/wp-admin/admin-ajax.php"
        val response = app.post(
            searchUrl,
            headers = mapOf("Referer" to mainUrl),
            data = mapOf(
                "action" to "ajaxsearchlite_search",
                "aslp" to query,
                "asid" to "1",
                "options" to "customset[]=page&asl_gen[]=excerpt&asl_gen[]=content&asl_gen[]=title&qtranslate_lang=0&filters_initial=1&filters_changed=0",
                "asl_req_json" to "1"
            )
        ).parsed<SearchResult>()

        return response.html?.let { html ->
            Jsoup.parse(html).select("div.item").mapNotNull { h ->
                val name = h.selectFirst("h3")?.text() ?: return@mapNotNull null
                newSearchResponse(
                    name = name,
                    url = h.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                ) {
                    posterUrl = fixUrlNull(h.selectFirst("img")?.attr("src"))
                }
            }
        } ?: emptyList()
    }

    data class SearchResult(
        @JsonProperty("html") val html: String?,
    )
}