package com.lagradost.quicknovel.providers

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus

class LightNovelWorldProvider : MainAPI() {
    override val name = "LightNovelWorld"
    override val mainUrl = "https://lightnovelworld.org"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_lightnovelworld
    override val iconBackgroundId = R.color.colorPrimaryWhite

    override val mainCategories = listOf(
        "New Update" to "/updates/",
        "Top Ranked" to "/ranking/?sort=rank",
        "Top Reviews" to "/ranking/?sort=reviews",
        "Top Comments" to "/ranking/?sort=comments",
        "Top Collections" to "/ranking/?sort=collections"
    )

    data class SearchNovel(
        @JsonProperty("title") val title: String?,
        @JsonProperty("slug") val slug: String?,
        @JsonProperty("cover_path") val coverPath: String?
    )

    data class SearchResponseDto(
        @JsonProperty("novels") val novels: List<SearchNovel>?
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = withPageParam(fixUrl(mainCategory ?: "/updates/"), page)
        val document = app.get(url).document

        val cards = (document.select("div.ranking-list > a") + document.select("a.card-link"))
            .distinctBy { it.attr("href") }

        val novels = cards.mapNotNull { card ->
            val href = card.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = card.selectFirst("h4.ranking-item-title, h3.card-title, h4")?.text()?.trim()
                ?: return@mapNotNull null

            newSearchResponse(name = title, url = href) {
                posterUrl = fixUrlNull(
                    card.selectFirst("div.ranking-item-cover > img")?.attr("src")
                        ?: card.selectFirst("div.card-cover")?.attr("data-bg-image")
                        ?: card.selectFirst("img")?.attr("src")
                )
            }
        }

        return HeadMainPageResponse(url, novels)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/search/?q=${Uri.encode(query)}"
        val response = app.get(url).parsed<SearchResponseDto>()

        return response.novels?.mapNotNull { novel ->
            val title = novel.title?.trim() ?: return@mapNotNull null
            val slug = novel.slug?.trim() ?: return@mapNotNull null

            newSearchResponse(name = title, url = "/novel/$slug/") {
                posterUrl = fixUrlNull(novel.coverPath)
            }
        } ?: emptyList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.novel-title, meta[property=og:title]")?.let {
            if (it.tagName() == "meta") it.attr("content") else it.text()
        }?.trim() ?: return null

        val chapters = getChapters(url)

        return newStreamResponse(name = title, url = url, data = chapters) {
            author = document.selectFirst("p.novel-author > a")?.text()?.trim()
            posterUrl = fixUrlNull(
                document.selectFirst("meta[property=og:image]")?.attr("content")
                    ?: document.selectFirst("img.novel-cover")?.attr("src")
            )
            synopsis = document.selectFirst("div.summary-content")?.text()?.trim()
            tags = document.select("div.genre-tags > span.genre-tag").map { it.text().trim() }
            setStatus(document.selectFirst("span.status-badge")?.text()?.trim())
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val chapterRoot = document.selectFirst("div.chapter-text") ?: return null

        chapterRoot.select("script, style, .ads, .ad, .advertisement, .chapter-nav").remove()

        val paragraphs = chapterRoot.select("> p")
        return if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("") { it.outerHtml() }
        } else {
            chapterRoot.html().takeIf { it.isNotBlank() }
        }
    }

    private suspend fun getChapters(url: String): List<ChapterData> {
        val cleanUrl = url.removeSuffix("/")
        val firstPageUrl = "$cleanUrl/chapters/?page=1"
        val document = app.get(firstPageUrl).document

        val pagination = document.selectFirst("select#pageSelect")
        if (pagination != null) {
            val lastPageNumber = pagination.select("option").lastOrNull()?.text()?.toIntOrNull() ?: 1

            // Intentamos obtener el número total del último capítulo para generar URLs numéricas
            val lastPageUrl = "$cleanUrl/chapters/?page=$lastPageNumber"
            val lastPageDoc = app.get(lastPageUrl).document
            val totalChapters = lastPageDoc.select("div.chapters-grid > div.chapter-card")
                .lastOrNull()
                ?.selectFirst("div.chapter-number")
                ?.text()
                ?.toIntOrNull()

            if (totalChapters != null) {
                return (1..totalChapters).map { num ->
                    newChapterData("Chapter $num", "$cleanUrl/chapter/$num")
                }
            }
        }

        return document.select("div.chapters-grid > div.chapter-card").mapNotNull { li ->
            val name = li.selectFirst("h3")?.text() ?: return@mapNotNull null
            val onClick = li.attr("onClick")
            val chapterUrl = fixUrlNull(onClick.substringAfter("location.href='", "").substringBefore("'", "")) ?: ""

            newChapterData(name, chapterUrl) {
                dateOfRelease = li.selectFirst("p.chapter-time")?.text()
            }
        }
    }

    private fun withPageParam(url: String, page: Int): String {
        return if (url.contains("?")) "$url&page=$page" else "$url?page=$page"
    }
}