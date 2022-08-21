package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.USER_AGENT
import org.jsoup.Jsoup

class RedditProvider : MainAPI() {
    override val mainUrl = "https://www.reddit.com"
    override val name = "Reddit"

    fun getName(url: String?): String? {
        return Regex("reddit\\.com/r/.*?/comments.*?/.*?/(.*)/").find(
            url ?: return null
        )?.groupValues?.getOrNull(1)?.replace('_', ' ')
    }

    fun isValidLink() {

    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(
            url, headers = mapOf(
                "cookie" to "over18=1", // 😏
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*",
                "user-agent" to USER_AGENT
            )
        )
        val document = Jsoup.parse(response.text)
        if(url.contains("/comment/")) {
            document.selectFirst("div.RichTextJSON-root")?.html()?.let { return it }
        }
        return document.allElements.firstOrNull { it.attr("data-click-id") == "text" }?.html() ?: document.selectFirst("div.RichTextJSON-root")?.html()
    }
}