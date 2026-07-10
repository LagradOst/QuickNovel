package com.lagradost.quicknovel.providers

import android.net.Uri
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.USER_AGENT
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.providers.NovelFireProvider.RelatedResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.jvm.Throws

class DevilNovelsProvider : MainAPI() {

    override val name = "DevilNovels"
    override val mainUrl = "https://devilnovels.com"
    private val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
    override val iconId = R.drawable.icon_devilnovels
    override val lang = "es"
    override val hasMainPage = true

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/listado-de-novelas/"
        val document = app.get(url).document

        val items = document.select("div.pvc-featured-pages-grid > div.pvc-featured-page-item").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("p")?.text() ?: return@mapNotNull null

            newSearchResponse(title, href) {
                posterUrl = card.selectFirst("img")?.attr("src")
            }
        }

        return HeadMainPageResponse(url, items)
    }

    private suspend fun getPageChapters(id: Int, page: Int = 1): GetFirstPageChaptersResponse {
        return app.post(
            ajaxUrl, headers = mapOf(
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*",
                "user-agent" to USER_AGENT
            ), data = mapOf(
                "action" to "dv_load_chapters",
                "cat_id" to "$id",
                "page" to "$page",
                "search" to "",
            )
        ).parsed<GetFirstPageChaptersResponse>()
    }

    private fun getRelated(document: Document): List<SearchResponse> {
        return document.select("div.nv-related-grid a").mapNotNull { element ->
            val href = element.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("div.nv-related-name")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(
                    element.selectFirst("img")?.attr("src")
                )
            }
        }
    }
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val scriptContent = document.select("script").find { it.data().contains("var CAT_ID") }?.data()
            ?: ""
        val catIdRegex = Regex("""var\s+CAT_ID\s*=\s*(\d+);""")
        val totalChaptersRegex = Regex("""var\s+TOTAL_CH\s*=\s*(\d+);""")

        val novelId = catIdRegex.find(scriptContent)?.groupValues?.get(1)?.toIntOrNull() ?: throw ErrorLoadingException("No id found")
        val totalChapters = totalChaptersRegex.find(scriptContent)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val chapters = if (totalChapters > 100) {
            (0 until totalChapters).map { index ->
                newChapterData("Chapter ${index + 1}", "$url--------$novelId--------$index")
            }
        } else {
            val res = getPageChapters(novelId, 1)
            res.data.chapters.map { ch ->
                newChapterData(ch.title, ch.link)
            }
        }

        return newStreamResponse(document.selectFirst("div.nv-meta > h1")?.text() ?: "", url, chapters) {
            this.posterUrl = document.selectFirst("div.nv-cover > img")?.attr("src")
            this.synopsis = document.selectFirst("#nvt-sinopsis > div")?.html()
            this.author = document.selectFirst("#nvt-sinopsis > div > p:nth-child(3)")?.ownText()
            this.tags = document.selectFirst("#nvt-sinopsis > div > p:nth-child(5)")?.text()
                ?.replace("Géneros: ", "")?.split(",")?.map { it.trim() }
            related = getRelated(document)
        }
    }

    private suspend fun getSpecificChapter(novelId: Int, index: Int): String {
        val page = (index / 100) + 1
        val res = getPageChapters(novelId, page)

        val relativeIndex = index % 100
        return res.data.chapters.getOrNull(relativeIndex)?.link ?: ""
    }

    override suspend fun loadHtml(url: String): String? {
        var realUrl = url

        if (url.contains("--------")) {
            val parts = url.split("--------")
            if (parts.size >= 3) {
                val novelId = parts[1].toInt()
                val index = parts[2].toInt()
                realUrl = getSpecificChapter(novelId, index)
            }
        }

        if (realUrl.isEmpty()) return null

        val document = app.get(realUrl).document
        return document.selectFirst("article.dv-post-article")?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?post_type=page&s=${Uri.encode(query)}"
        val document = app.get(url).document

        return document.select("main#main > div > article").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("h2")?.text() ?: return@mapNotNull null

            newSearchResponse(title, href) {
                posterUrl = card.selectFirst("img")?.attr("src")
            }
        }
    }

    data class GetFirstPageChaptersResponse(
        val success: Boolean,
        val data: ChaptersPage
    )

    data class ChaptersPage(
        val chapters: List<Chapters>,
        val page: Int,
        val pages: Int,
        val total: Int,
    )

    data class Chapters(
        val id: Int,
        val link: String,
        val title: String,
    )
}