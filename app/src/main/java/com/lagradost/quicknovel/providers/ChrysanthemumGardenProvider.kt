package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.*
import org.jsoup.nodes.Document
import com.lagradost.quicknovel.R

class ChrysanthemumGardenProvider : MainAPI() {
    override val name = "Chrysanthemum Garden"
    override val mainUrl = "https://chrysanthemumgarden.com"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_chrysanthemumgarden
    override val iconBackgroundId = R.color.colorPrimaryWhite
    override val usesCloudFlareKiller = true
    override val lang = "en"

    override val mainCategories = listOf(
        "Novels" to "books",
        "Indie Novels" to "indie-novels"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/$mainCategory/page/$page/"
        val document = app.get(url).document
        return HeadMainPageResponse(url, parseNovels(document))
    }

    private fun parseNovels(doc: Document): List<SearchResponse> {
        return doc.select("article").mapNotNull { element ->
            if (element.select("div.series-genres > a").text().contains("Manhua", true)) return@mapNotNull null
            val titleElement = element.selectFirst("h2.novel-title > a") ?: return@mapNotNull null
            val name = titleElement.text()
            val url = titleElement.attr("href").removePrefix(mainUrl).trim('/')
            val coverUrl = element.selectFirst("div.novel-cover > img")?.let {
                it.attr("data-breeze").ifEmpty { it.attr("src") }
            }

            newSearchResponse(name, url) {
                posterUrl = fixUrlNull(coverUrl)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/wp-json/cg/novels"
        val response = app.get(url).parsed<Array<CgNovel>>()

        return response.filter {
            it.name.contains(query, ignoreCase = true)
        }.map { novel ->
            newSearchResponse(novel.name, novel.link)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        doc.select(".novel-raw-title").remove()
        val title = doc.selectFirst("h1.novel-title")?.text()?.trim() ?: return null

        val chapters = doc.select(".chapter-item a").map { element ->
            newChapterData(element.text().trim(), element.attr("href"))
        }

        return newStreamResponse(title, url, chapters) {
            this.posterUrl = doc.selectFirst(".novel-cover img")?.let {
                it.attr("data-breeze").ifEmpty { it.attr("src") }
            }
            this.synopsis = doc.select(".entry-content p").joinToString("\n\n") { it.text().trim() }

            val info = doc.select(".novel-info")?.outerHtml() ?: ""
            this.author = Regex("Author:\\s*([^<]*)<br>").find(info)?.groupValues?.get(1)?.trim()

            val genres = doc.select(".series-genres a").map { it.text().trim() }.toMutableList()
            genres.addAll(doc.select(".series-tag").map { it.text().substringBefore("(").trim() })
            this.tags = genres.distinct()
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val doc = app.get(url).document
        val content = doc.selectFirst("#novel-content") ?: return null

        content.select("[style*='display:none'], [style*='display: none']").remove()
        content.select("[style*='visibility:hidden'], [style*='visibility: hidden']").remove()
        content.select("[style*='font-size:0'], [style*='font-size: 0']").remove()
        content.select("[style*='width:0'], [style*='width: 0']").remove()

        content.select(".chrys-ads, .announcement, .entry-content_content, script, style").remove()
        content.select("p:contains(chrysanthemumgarden)").remove()

        content.select(".jum").remove()

        return content.html()
    }

    data class CgNovel(
        @JsonProperty("name") val name: String,
        @JsonProperty("link") val link: String
    )
}
