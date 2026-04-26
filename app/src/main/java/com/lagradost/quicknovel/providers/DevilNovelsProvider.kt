package com.lagradost.quicknovel.providers

import android.net.Uri
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import org.jsoup.nodes.Document

class DevilNovelsProvider  : MainAPI() {

    override val name = "DevilNovels"
    override val mainUrl = "https://devilnovels.com"
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

    private suspend fun getChapters(document: Document, url: String): List<ChapterData> {
        val pagination = document.select("nav.elementor-pagination a.page-numbers")
        if (pagination.size > 1) {
            val lastPageElement = pagination[pagination.size - 1]
            val lastPageNumber = lastPageElement.ownText().toIntOrNull() ?: 1

            val lastPageUrl = "$url/?e-page-bc939d8=$lastPageNumber"
            val lastPageDoc = app.get(lastPageUrl).document
            val lastTotalChapters = lastPageDoc.select("div.elementor-posts-container > article").size
            val totalChapters = ((lastPageNumber - 1) * 100) + lastTotalChapters

            return (0..< totalChapters).map { chapterNumber ->
                newChapterData("Chapter ${chapterNumber + 1}", "$url--------$chapterNumber")
            }
        }

        return document.select("div.elementor-posts-container > article").mapNotNull { li ->
            val name = li.selectFirst("a")?.text() ?: return@mapNotNull null
            val url = li.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            newChapterData(name, url)
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val chapters = getChapters(document, url)

        return newStreamResponse(document.selectFirst("div.elementor-widget-container p")?.text() ?: "", url, chapters) {
            this.posterUrl = document.selectFirst("div.elementor-widget-container > p > img")?.attr("src")
            this.synopsis =  document.select("div.elementor-widget-container > p")?.drop(1)?.joinToString("\n"){it.text()}
            this.author = author
            this.tags = tags
        }
    }

    private suspend fun getSpecificChapter(url:String, number:Int):String{
        val page = (number + 100 - 1)/100
        val document = app.get("$url/?e-page-bc939d8=$page").document
        val realNumber = number % 100
        val chapter = document.select("div.elementor-posts-container > article")[realNumber]
        return chapter.selectFirst("a")?.attr("href") ?: ""
    }

    override suspend fun loadHtml(url: String): String? {
        var newUrl = url
        val chapterNumber = url.split("--------")
        if(chapterNumber.size > 1) {
            newUrl = getSpecificChapter(chapterNumber[0],chapterNumber[1].toInt())
        }

        val document = app.get(newUrl).document
        val content = document.select("div.ast-post-format- > div.entry-content > p")
            ?.joinToString("</br>") { it.html() }
        return content
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
}