package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.USER_AGENT
import org.jsoup.Jsoup

class RedditProvider : MainAPI() {
    override val mainUrl = "https://www.reddit.com"
    override val name = "Reddit"

    companion object {
    fun getName(url: String?): String? {
        return Regex("reddit\\.com/r/.*?/comments.*?/.*?/(.*)/").find(
            url ?: return null
        )?.groupValues?.getOrNull(1)?.replace('_', ' ')
    }
    }
    fun isValidLink() {

    }

    private fun findComment(url: String): String? {
        val str = Regex("comment/(\\w*?)/").find(url)?.groupValues?.get(1)
            ?: Regex("comments/\\w*?/\\w*?/(\\w*?)/").find(url)?.groupValues?.get(1)
        if(str.isNullOrBlank() || str.length < 4) {
            return null
        }
        return str
    }

    // ":{"document":[{"c":[{"e":"text","t":"
    override suspend fun loadHtml(url: String): String? {
        val curl = url.replace("http://","https://")
        //println("LOADING: ${curl}")
        val response = app.get(
            curl, headers = mapOf(
                //"cookie" to "over18=1", // 😏
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*",
                "user-agent" to USER_AGENT
            )
        )
        if (response.text.contains("Log in to confirm you")) {
           // println("NSFW")
            val range = findComment(curl)?.let { result ->
                val start = Regex(""""id":"\w\d_$result","isAdmin""").find(response.text)?.range?.first ?: return@let null
                //val lookStart = response.text.indexOf("sendReplies")
                //Regex("")
                //var start = response.text.indexOf(checkfor, lookStart)
                //if (start == -1) {
                //    start = 0
                //}
                var end = response.text.indexOf("\"id\"", start+4)
                if (end == -1) {
                    end = response.text.length
                }
                //println("RESPONSE: $start -> $end")
                //println("REP2: ${response.text.subSequence(start, end)}")
                start..end
            } ?: kotlin.run {
                val start = response.text.indexOf("permalink")
                val end = response.text.indexOf("numCrossposts")
                start..end
            }
            // nsfw

            val str = response.text.subSequence(range)
            //println("STRING: $str")
            val regex = Regex(""""e":"text","t":"((?:\\"|[^"])*?)"""")//""""e":"text","t":"(.*?)"""") // \{"c":\[\{
            println("Finding shit")
            return regex.findAll(str).joinToString(separator = "<br>") { it.groupValues[1] }.replace("\\\"","\"")
        } else {
            println("REGULAR")
            val document = Jsoup.parse(response.text)
            return document.allElements.firstOrNull { it.attr("slot") == "text-body" }?.html()
        }
    }
}