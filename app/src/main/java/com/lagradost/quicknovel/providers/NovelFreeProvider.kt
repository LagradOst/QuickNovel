package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.SearchResponse
import khttp.get

class NovelFreeProvider : MainAPI() {

    override val name = "Novel Free"
    override val mainUrl = "https://novelfree.ml"

    override fun loadHtml(url: String): String? {
        return super.loadHtml(url)
    }

    override fun search(query: String): List<SearchResponse> {
        val searchRequest = get ("$mainUrl/?search=$query")

        return super.search(query)
    }

    override fun load(url: String): LoadResponse {
        return super.load(url)
    }




    }