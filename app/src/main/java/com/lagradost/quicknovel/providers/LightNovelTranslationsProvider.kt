package com.lagradost.quicknovel.providers

import android.util.Log
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus

class LightNovelTranslationsProvider: MainAPI() {
    override val name = "Light Novel Translations"
    override val mainUrl = "https://lightnovelstranslations.com/"
    override val iconId = R.drawable.icon_lightnoveltranslations

    override val hasMainPage = true

    // Permite elegir orden y estado de la novela en la UI
    override val mainCategories = listOf(
        "Most Liked" to "most-liked",
        "Most Recent" to "most-recent"
    )

    override val tags = listOf(
        "All" to "all",
        "Ongoing" to "ongoing",
        "Completed" to "completed"
    )

    override val orderBys = emptyList<Pair<String, String>>()

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val category = mainCategory ?: "most-liked"
        val statusFilter = when (tag) {
            "ongoing" -> "&status=Ongoing"
            "completed" -> "&status=Completed"
            else -> ""
        }

        val canPages = if(page == 0) 3 else page
        val novels = mutableListOf<SearchResponse>()
        var url = ""
        for(i in 0..canPages){
            url = "$mainUrl/read/page/$i?sortby=$category$statusFilter"
            val document = app.get(url).document
             novels.addAll( document.select("div.read_list-story-item").mapNotNull { el ->
                val link = el.selectFirst(".item_thumb a") ?: return@mapNotNull null
                val img = el.selectFirst(".item_thumb img")?.attr("src")
                val title = link.attr("title")
                val href = link.attr("href")

                newSearchResponse(name = title, url = href) {
                    posterUrl = fixUrlNull(img)
                }
            })
        }

        return HeadMainPageResponse(url, novels)
    }

    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document

        val title = document.selectFirst("div.novel_title h3")?.text()?.trim().orEmpty()
        val author = document.selectFirst("div.novel_detail_info li:contains(Author)")
            ?.text()?.trim().orEmpty()
        val cover = document.selectFirst("div.novel-image img")?.attr("src")
        val statusText = document.selectFirst("div.novel_status")?.text()?.trim()

        val synopsis = try {
            val body2 = app.get(url.replace("?tab=table_contents", "")).document
            body2.selectFirst("div.novel_text p")?.text()?.trim().orEmpty()
        } catch (t: Throwable) {
            logError(t)
            ""
        }

        val status = when (statusText) {
            "Ongoing" -> "Ongoing"
            "Hiatus" -> "On Hiatus"
            "Completed" -> "Completed"
            else -> null
        }

        val chapters = mutableListOf<ChapterData>()
        document.select("li.chapter-item.unlock").forEach { li ->
            val link = li.selectFirst("a") ?: return@forEach
            val chapterTitle = link.text().trim()
            val href = link.attr("href")

            chapters.add(
                newChapterData(
                    name = chapterTitle,
                    url = href
                )
            )
        }

        return newStreamResponse(title, fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = fixUrlNull(cover)
            this.synopsis = synopsis
            setStatus(status)
        }
    }

    override suspend fun loadHtml(url: String): String? {
        return try {
            val document = app.get(url).document
            val content = document.selectFirst("div.text_story")
            content?.select("div.ads_content")?.remove()
            content?.html()
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        val formData = mapOf("field-search" to query)
        val response = app.post("$mainUrl/read", data = formData)
        val document = response.document

        return document.select("div.read_list-story-item").mapNotNull { el ->
            val link = el.selectFirst(".item_thumb a") ?: return@mapNotNull null
            val img = el.selectFirst(".item_thumb img")?.attr("src")
            val title = link.attr("title").orEmpty()
            val href = link.attr("href").orEmpty()

            newSearchResponse(title, href) {
                posterUrl = fixUrlNull(img)
            }
        }
    }
}