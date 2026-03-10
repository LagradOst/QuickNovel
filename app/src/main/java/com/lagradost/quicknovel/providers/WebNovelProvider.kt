package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LibraryHelper
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus

class WebNovelProvider : MainAPI() {
    override val name = "WebNovel"
    override val mainUrl = "https://www.webnovel.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_webnovel

    // Tags based on LNReader - Male & Female genres
    override val tags = listOf(
        Pair("All", "novel"),
        Pair("Action", "novel-action-male"),
        Pair("ACG", "novel-acg-male"),
        Pair("Eastern", "novel-eastern-male"),
        Pair("Fantasy", "novel-fantasy-male"),
        Pair("Games", "novel-games-male"),
        Pair("History", "novel-history-male"),
        Pair("Horror", "novel-horror-male"),
        Pair("Realistic", "novel-realistic-male"),
        Pair("Sci-fi", "novel-scifi-male"),
        Pair("Sports", "novel-sports-male"),
        Pair("Urban", "novel-urban-male"),
        Pair("War", "novel-war-male"),
        Pair("Fantasy (F)", "novel-fantasy-female"),
        Pair("General (F)", "novel-general-female"),
        Pair("History (F)", "novel-history-female"),
        Pair("LGBT+", "novel-lgbt-female"),
        Pair("Sci-fi (F)", "novel-scifi-female"),
        Pair("Teen", "novel-teen-female"),
        Pair("Urban (F)", "novel-urban-female"),
    )

    override val orderBys = listOf(
        Pair("Popular", "1"),
        Pair("Recommended", "2"),
        Pair("Most Collections", "3"),
        Pair("Rating", "4"),
        Pair("Time Updated", "5"),
    )

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val genreSlug = tag ?: "novel"
        val order = orderBy ?: "1"

        val url = if (genreSlug == "novel") {
            "$mainUrl/stories/novel?orderBy=$order&pageIndex=$page"
        } else {
            "$mainUrl/stories/$genreSlug?orderBy=$order&pageIndex=$page"
        }

        val document = app.get(url, headers = headers).document

        return HeadMainPageResponse(
            url,
            list = document.select(".j_category_wrapper li").mapNotNull { element ->
                val thumb = element.selectFirst(".g_thumb") ?: return@mapNotNull null
                val href = thumb.attr("href")
                if (href.isBlank()) return@mapNotNull null

                val coverUrl = element.selectFirst(".g_thumb > img")?.attr("data-original")
val chapterCount1 = element.select(".pr span")
                    .firstOrNull { it.text().contains("Chapters", true) }
                    ?.text()
                    ?: ""
val chapterCount = Regex("""\d+""").find(chapterCount1)?.value
                SearchResponse(
                    name = thumb.attr("title").ifBlank { "Unknown" },
                    url = fixUrlNull(href) ?: return@mapNotNull null,
                    posterUrl = coverUrl?.let { "https:$it" },
                   // rating = null,
                   //latestChapter = null,
                    apiName = this.name,
                    //totalChapterCount = chapterCount
                )
            }
        )
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url, headers = headers).document

        // Remove comment elements as per LNReader
        document.select(".para-comment").remove()

        val title = document.selectFirst(".cha-tit")?.html() ?: ""
        val content = document.selectFirst(".cha-words")?.html() ?: return null

        return title + content
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?keywords=${query.replace(" ", "+")}"
        val document = app.get(searchUrl, headers = headers).document

        return document.select(".j_list_container li").mapNotNull { element ->
            val thumb = element.selectFirst(".g_thumb") ?: return@mapNotNull null
            val href = thumb.attr("href")
            if (href.isBlank()) return@mapNotNull null

            val coverUrl = element.selectFirst(".g_thumb > img")?.attr("src")

            newSearchResponse(
                thumb.attr("title").ifBlank { "Unknown" },
                fixUrlNull(href) ?: return@mapNotNull null
            ) {
                posterUrl = coverUrl?.let {
                    if (it.startsWith("http")) it else "https:$it"
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = headers).document

        // Get novel name from cover image alt
        val name = document.selectFirst(".g_thumb > img")?.attr("alt")
            ?: throw ErrorLoadingException("Invalid name")

        // Get novel path for catalog URL
        val novelPath = url.removePrefix(mainUrl)
        val catalogUrl = "$mainUrl$novelPath/catalog"

        val catalogDoc = app.get(catalogUrl, headers = headers).document

        // Parse chapters from catalog page
        val chapters = mutableListOf<Pair<String, String>>()

        catalogDoc.select(".volume-item").forEach { volumeElement ->
            // Extract volume name
            val volumeText = volumeElement.ownText().trim()
            val volumeMatch = Regex("""Volume\s+(\d+)""").find(volumeText)
            val volumeName = volumeMatch?.let { "Volume ${it.groupValues[1]}" } ?: ""

            volumeElement.select("li").forEach { chapterElement ->
                val chapterLink = chapterElement.selectFirst("a") ?: return@forEach
                val chapterPath = chapterLink.attr("href")
                if (chapterPath.isBlank()) return@forEach

                val chapterTitle = chapterLink.attr("title").ifBlank { chapterLink.text() }.trim()
                val isLocked = chapterElement.select("svg").isNotEmpty()

                val displayName = buildString {
                    if (volumeName.isNotBlank()) append("$volumeName: ")
                    append(chapterTitle)
                    //if (isLocked) append(" 🔒")
                }

                val fullUrl = fixUrlNull(chapterPath) ?: return@forEach
                chapters.add(displayName to fullUrl)
            }
        }

        val data = chapters.map { (cName, cUrl) ->
            newChapterData(cName, cUrl)
        }.reversed()

        // Get cover URL
        val coverUrl = document.selectFirst(".g_thumb > img")?.attr("src")

        // Get author - find "Author:" label and get next sibling element
        val author = document.select(".det-info .c_s").firstOrNull { elem ->
            elem.text().trim() == "Author:"
        }?.nextElementSibling()?.text()?.trim()

        // Get status - find svg with title="Status" and get next sibling text
        val statusText = document.select(".det-hd-detail svg").firstOrNull { elem ->
            elem.attr("title") == "Status"
        }?.nextElementSibling()?.text()?.trim()

        // Get genres from tag title attribute
        val genres = document.selectFirst(".det-hd-detail > .det-hd-tag")?.attr("title")

        // Get synopsis
        val synopsis = document.selectFirst(".j_synopsis > p")?.apply {
            select("br").before("\\n")
            select("br").remove()
        }?.text()?.replace("\\n", "\n")?.trim()

        return newStreamResponse(name, url, data) {
            this.tags = genres?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
            this.author = author
            this.posterUrl = coverUrl?.let {
                if (it.startsWith("http")) it else "https:$it"
            }
            this.synopsis = synopsis

            setStatus(statusText)
        }
    }
}


