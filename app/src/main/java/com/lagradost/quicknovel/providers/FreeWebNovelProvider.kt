package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.SearchResponse
import khttp.get
import org.jsoup.Jsoup

class FreeWebNovelProvider : MainAPI() {

    override val name = "Free Web Novel"
    override val mainUrl = "https://freewebnovel.com"

    override fun loadHtml(url: String): String? {
        return super.loadHtml(url)
    }

    override fun search(query: String): List<SearchResponse> {
        val searchRequest = get ("$mainUrl/search=$query")
        val doc = Jsoup.parse(searchRequest.text)


        return super.search(query)
    }

    override fun load(url: String): LoadResponse {
        return super.load(url)
    }



}
