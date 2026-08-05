package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
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
import org.jsoup.nodes.Document
import android.net.Uri

open class GalaxyNovelsProvider : MainAPI() {
    override val name = "Galaxy Novels"
    override val mainUrl = "https://galaxynovels.com"
    override val lang = "ar"
    override val iconId = R.drawable.icon_galaxynovels
    override val hasMainPage = true
    override val rateLimitTime = 500L

    override val orderBys = listOf(
        "Most Popular" to "popular",
        "Newest" to "new",
        "Recently Updated" to "recent"
    )

    override val mainCategories = listOf(
        "All Time" to "all",
        "Month" to "month",
        "Week" to "week"
    )

    private fun parseNovels(document: Document): List<SearchResponse> {
        return document.select("article.wor-novel-card").mapNotNull { el ->
            val titleEl = el.selectFirst("h3 a") ?: return@mapNotNull null
            val name = titleEl.text().trim()
            val url = titleEl.attr("href")
            val img = el.selectFirst("img.wor-cover-img")
            val posterUrl = fixUrlNull(img?.attr("data-src"))

            newSearchResponse(name, url) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = when (orderBy) {
            "new" -> "$mainUrl/novels/?sort=new&page=$page"
            "recent" -> "$mainUrl/recent/?page=$page"
            else -> "$mainUrl/novels/?sort=popular&period=$mainCategory&page=$page"
        }

        val document = app.get(url).document
        return HeadMainPageResponse(url, parseNovels(document))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${Uri.encode(query.trim()).replace("%20", "+")}"
        val document = app.get(url).document
        return parseNovels(document)
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: throw ErrorLoadingException("Title not found")

        val chapters = mutableListOf<ChapterData>()
        val indexUrl = document.selectFirst("[data-wor-chapters-container]")
            ?.attr("data-index-url")?.let { fixUrl(it) }

        if (indexUrl != null) {
            runCatching {
                val index = app.get(indexUrl).parsed<ChaptersIndexJson>()
                index.chapters.forEach { ch ->
                    chapters.add(newChapterData(
                        ch.label + (if (ch.title.isNullOrBlank()) "" else ": ${ch.title}"),
                        "${url.removeSuffix("/")}/chapter-${ch.id}/"
                    ) {
                        dateOfRelease = ch.dateIso
                    })
                }
            }
        }

        if (chapters.isEmpty()) {
            document.select("article.wor-novel-chapter-item").forEach { el ->
                val link = el.selectFirst("h3 a") ?: el.selectFirst("a.wor-novel-chapter-item__num")
                val chName = link?.text()?.trim() ?: "Chapter"
                val chUrl = link?.attr("href") ?: return@forEach
                val releaseTime = el.selectFirst("time")?.attr("datetime")?.split("T")?.firstOrNull()

                chapters.add(newChapterData(chName, chUrl) {
                    dateOfRelease = releaseTime
                })
            }
        }

        return newStreamResponse(title, url, chapters) {
            this.posterUrl = fixUrlNull(document.selectFirst("img.wor-cover-img")?.attr("src"))
            this.author = document.selectFirst("p.wor-single-hero__meta-text span")?.text()?.trim()
            this.synopsis = document.select(".wor-single-summary__text").html()
            this.tags = document.select("a.wor-tag-pill").map { it.text().trim() }
            setStatus(document.selectFirst("span.wor-cover-status")?.text()?.trim())
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val chapterId = Regex("chapter-(\\d+)").find(url)?.groupValues?.get(1)
        if (chapterId != null) {
            val apiUrl = "$mainUrl/wp-json/wor-reader-app/v1/chapters/$chapterId"
            runCatching {
                val response = app.get(apiUrl).parsed<ChapterContentResponseJson>()
                if (response.data?.contentHtml?.isNotBlank() == true) {
                    return response.data.contentHtml
                }
            }
        }

        val document = app.get(url).document
        val content = document.selectFirst("article.wor-chapter-content, .wor-chapter-text, .entry-content")
        content?.select("script, style, .ads, .adsbygoogle, iframe")?.remove()
        return content?.html()
    }

    data class ChaptersIndexJson(
        @JsonProperty("chapters") val chapters: List<ChapterJson>
    )

    data class ChapterJson(
        @JsonProperty("id") val id: Int,
        @JsonProperty("label") val label: String,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("date_iso") val dateIso: String? = null
    )

    data class ChapterContentResponseJson(
        @JsonProperty("data") val data: ChapterDataJson? = null
    )

    data class ChapterDataJson(
        @JsonProperty("content_html") val contentHtml: String? = null
    )
}
