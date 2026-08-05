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
import org.jsoup.Jsoup

class FaqWikiProvider :  MainAPI() {
    override val name = "FaqWiki"
    override val mainUrl = "https://faqwiki.xyz"
    override val iconId = R.drawable.icon_faqwiki
    override val iconBackgroundId = R.color.black
    override val hasMainPage = true

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        if(page > 1) return HeadMainPageResponse("",emptyList())
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


    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: throw ErrorLoadingException("Invalid Name")
        val chapters = document.select("#lcp_instance_0 > li").mapNotNull { li ->
            val a = li.selectFirst("a")?: return@mapNotNull null
            val name = a.text()
            val url = a.attr("href")
            newChapterData(name, url)
        }
        return newStreamResponse(title,url, chapters) {
            this.posterUrl = document.selectFirst("figure > img")?.attr("src")
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val res = document.selectFirst("main > article > div")
        res?.select("script, div")?.remove()
        return res?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post("${mainUrl.replace("xyz","us")}/novel/wp-admin/admin-ajax.php", headers = mapOf(
            "Referer" to mainUrl
            ),
            data = mapOf(
                "action" to "ajaxsearchlite_search",
                "aslp" to query,
                "asid" to "1",
                "options" to "customset[]=page&asl_gen[]=excerpt&asl_gen[]=content&asl_gen[]=title&qtranslate_lang=0&filters_initial=1&filters_changed=0",
                "asl_req_json" to "1"
            )
        ).parsed<SearchResult>()
        return document.html?.let{
            Jsoup.parse(it).select("div.item").mapNotNull { h ->
                val name = h.selectFirst("h3")?.text() ?: return@mapNotNull null
                newSearchResponse(
                    name = name,
                    url = h.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                ) {
                    posterUrl = fixUrlNull(h.selectFirst("img")?.attr("src"))
                }
            }
        }?: emptyList()
    }

    data class SearchResult(
        @JsonProperty("html") val html: String?,
    )
}