package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.MainAPI
import org.jsoup.Jsoup

class RedditProvider: MainAPI()  {
    override val mainUrl = "https://www.reddit.com"
    override val name = "Reddit"

    fun isValidLink(url : String?) : String? {
        return Regex("reddit\\.com/r/.*?/comments.*?/.*?/(.*)/").find(url ?: return null)?.groupValues?.getOrNull(1)?.replace('_', ' ')
    }

    override fun loadHtml(url: String): String? {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div.RichTextJSON-root").html()
    }
}