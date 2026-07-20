package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import java.util.Locale

class NovLoveProvider  : MeioNovelProvider()
{
    override val name = "NovLove"
    override val mainUrl = "https://novelnice.com"
    override val hasMainPage = false
    override val usesCloudFlareKiller = true
    override val lang = "en"

    override suspend fun load(url: String): LoadResponse? {
        //This is for migrating NovLove URLs.
        val path = url.substringAfter("://").substringAfter("/", "")
        val adaptedPath = path.replaceFirst("novel/", "read/")
        val finalUrl = "${mainUrl}/$adaptedPath".removeSuffix("/")
        val document = app.get(finalUrl).document
        val name = document.selectFirst("div.post-title > h1")?.text()?.clean() ?: return null

        return newStreamResponse(url, name, getChapters(url)) {
            tags = document.select("div.genres-content > a").map { it.text() }
            author = document.selectFirst("div.author-content > a")?.text()

            synopsis = document.select("#editdescription > p, div.j_synopsis > p, div.summary__content > p")
                .filter { it.hasText() && !it.text().lowercase().contains(mainUrl) }
                .joinToString("\n\n") { it.text() }

            setStatus(document.select("div.post-status > div.post-content_item > div.summary-content").last()?.text())

            posterUrl = fixUrlNull(document.selectFirst("div.summary_image > a > img")?.let {
                it.attr("data-src").takeIf { src -> src.isNotBlank() } ?: it.attr("src")
            })

            rating = (document.selectFirst("span#averagerate")?.text()?.toFloatOrNull()?.times(200))?.toInt()
            peopleVoted = document.selectFirst("span#countrate")?.text()?.let { text ->
                text.replace("K", if (text.contains(".")) "00" else "000")
                    .replace(".", "").toIntOrNull()
            } ?: 0

            related = getRelated(document)
        }
    }
}