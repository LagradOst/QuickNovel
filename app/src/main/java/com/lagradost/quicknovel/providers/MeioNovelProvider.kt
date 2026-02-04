package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.USER_AGENT
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import java.util.Locale
import kotlin.text.contains

/*
// https://boxnovel.com/
// https://morenovel.net/
// https://meionovel.id/
 */
class MeioNovelProvider : MoreNovelProvider() {
    override val name = "MeioNovel"
    override val mainUrl = "https://meionovels.com"
    override val iconId = R.drawable.ic_meionovel
    override val iconBackgroundId = R.color.colorPrimaryBlue

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val name =
            document.selectFirst("div.post-title > h1")?.text()?.replace("  ", " ")
                ?.replace("\n", "")
                ?.replace("\t", "") ?: return null

        val data = getChapters(
            app.post("${url}ajax/chapters/", headers = mapOf("user-agent" to USER_AGENT)).text)
        return newStreamResponse(url = url, name = name, data = data) {
            tags = document.select("div.genres-content > a").map { it.text() }
            val authors = document.selectFirst("div.author-content > a")
            author = authors?.text()
            var synopsis = ""
            var synopsisParts = document.select("#editdescription > p")
            if (synopsisParts.size == 0) synopsisParts = document.select("div.j_synopsis > p")
            if (synopsisParts.size == 0) synopsisParts = document.select("div.summary__content > p")
            for (s in synopsisParts) {
                if (s.hasText() && !s.text().lowercase(Locale.getDefault())
                        .contains(mainUrl)
                ) { // FUCK ADS
                    synopsis += s.text() + "\n\n"
                }
            }
            if (synopsis.isNotEmpty()) {
                this.synopsis = synopsis
            }
            setStatus(document.select("div.post-status > div.post-content_item > div.summary-content")
                .last()?.text())
            posterUrl = fixUrlNull(document.select("div.summary_image > a > img").attr("src"))
            rating = ((document.selectFirst("span#averagerate")?.text()?.toFloatOrNull()
                ?: 0f) * 200).toInt()

            val peopleVotedText =
                document.selectFirst("span#countrate")?.text()
            // Turn K to thousands, 9.3k -> 2 zeroes | 95K -> 3 zeroes
            peopleVoted =
                peopleVotedText?.replace("K", if (peopleVotedText.contains(".")) "00" else "000")
                    ?.replace(".", "")
                    ?.toIntOrNull() ?: 0
        }
    }
}