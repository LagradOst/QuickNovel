package com.lagradost.quicknovel.providers

import android.net.Uri
import com.lagradost.quicknovel.DownloadExtractLink
import com.lagradost.quicknovel.DownloadLink
import com.lagradost.quicknovel.DownloadLinkType
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.newEpubResponse
import com.lagradost.quicknovel.newSearchResponse
import org.jsoup.nodes.Document

class PlanetaEpubProvider :  MainAPI() {
    override val name = "PlanetaEpub"
    override val mainUrl = "https://www.planetaepub.com"
    override val iconId = R.drawable.icon_planetaepub
    override val iconBackgroundId = R.color.white
    override val lang = "es"

    override val hasMainPage = true

    override val mainCategories = listOf(
        "All" to "",
        "Completed" to "completed",
        "Ongoing" to "ongoing",
        "Hiatus" to "hiatus",
        "Dropped" to "dropped"
    )


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = "$mainUrl${if(mainCategory.isNullOrEmpty()) "" else "/categoria/$mainCategory"}/page/$page/"
        val document = app.get(url).document

        val returnValue = document.select("main.content > div > div.post-list > div > article").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("h2")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = card.selectFirst("img")?.attr("src")?.replace("-600x340","")
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }



    private fun getDownloadLink(document: Document): String?{
        val script = document.selectFirst("#content > div:nth-child(2) > article > div > div.entry.themeform > div.entry-inner > script")
            ?.data() ?: return null

        val regex = Regex("""window\.location\.href\s*=\s*"([^"]+)"""")
        val match = regex.find(script)

        return match?.groupValues?.getOrNull(1)
    }
    private fun extract(url: String, name: String): DownloadLinkType {
        return if (url.contains(".epub")) {
            DownloadLink(
                url = url,
                name = name,
                kbPerSec = 2
            )
        } else {
            DownloadExtractLink(
                url = url,
                name = name
            )
        }
    }

    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document
        val titleContent = document.selectFirst("h1")?.text()
        val title = titleContent?.substringBeforeLast("–") ?: ""
        val chapters = extract(getDownloadLink(document) ?: "", title)

        return newEpubResponse(title,fixUrl(url), listOf(chapters)) {
            this.posterUrl = document.selectFirst("div.image-container > img")?.attr("src")
            this.synopsis =  document.selectFirst("#content > div:nth-child(2) > article > div > div.entry.themeform > div.entry-inner > div.descripcion-libro-premium > div:nth-child(2) > p")?.text() ?: ""
            this.author = titleContent?.substringAfterLast("–")
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${Uri.encode(query).replace("%20","+")}"
        val document = app.get(url).document

        return document.select("main.content > div > div.post-list > div > article").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("h2")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = card.selectFirst("img")?.attr("src")?.replace("-600x340","")
            }
        }
    }
}