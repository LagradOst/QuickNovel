package com.lagradost.quicknovel.providers

import android.util.Log
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.*
import org.jsoup.Jsoup
import kotlin.math.roundToInt

class NovelHallProvider : MainAPI() {

    override val name = "NovelHall"
    override val mainUrl = "https://www.novelhall.com"
    override val iconId = R.drawable.icon_novelhall
    override val iconBackgroundId = R.color.white
    override val hasMainPage = false

    override val mainCategories = listOf(
        "Latest" to "latest",
        "Popular" to "popular"
    )

    override val orderBys = emptyList<Pair<String, String>>() 
    override val tags = emptyList<Pair<String, String>>()
    
    // Headers para evitar el error 403
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    private fun listUrl(type: String, page: Int): String {
        return when (type) {
            "popular" -> "$mainUrl/all-$page.html"
            else -> "$mainUrl/all-$page.html"
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {

        val type = mainCategory ?: "latest"
        val url = listUrl(type, page)
        val doc = app.get(url, headers = headers).document

        val list = doc.select(".type li.btm").mapNotNull { el ->
            val href = el.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = el.selectFirst("a")?.text() ?: return@mapNotNull null
            val poster = "android.resource://com.lagradost.quicknovel/${R.drawable.default_cover}"


            newSearchResponse(
                name = title,
                url = fixUrl(href)
            ) {
                posterUrl = poster
            }
        }

        return HeadMainPageResponse(url, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/index.php?s=so&module=book&keyword=$query"
        val doc = app.get(url, headers = headers).document

        return doc.select(".section3 table tr").mapNotNull { el ->
            val a = el.selectFirst("td:nth-child(2) a") ?: return@mapNotNull null
            val href = a.attr("href")
            val title = a.text()
            val poster = "android.resource://com.lagradost.quicknovel/${R.drawable.default_cover}"

            newSearchResponse(title, fixUrl(href)) {
                posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = headers).document

        val title = doc.selectFirst(".book-info h1")?.text() ?: ""
        val rawAuthor = doc.selectFirst(".book-info .blue:contains(Author)")
            ?.text()
            ?.replace("Author：", "", ignoreCase = true)
            ?.trim()
            ?: ""

        val author = rawAuthor.replace(Regex("\\d+"), "").trim()

        val cover = doc.selectFirst(".book-img img")?.attr("src")
        val desc = doc.select("span.js-close-wrap")
            .eachText().joinToString("\n")
        
        Log.e("NovelHallProvider", "Cover URL: $cover")
        
        /*   val statusElement = doc.selectFirst(".book-info .blue:nth-child(3)")
        val statusRaw  = statusElement?.nextElementSibling()?.text() ?: "Unknown"
        val status: ReleaseStatus? = when {
            statusRaw.contains("Complete", true) -> ReleaseStatus.COMPLETED
            else -> ReleaseStatus.ONGOING
        }*/

        val chapters = mutableListOf<ChapterData>()

        val chapterDoc = app.get(url, headers = headers).document
        chapterDoc.select("#morelist a").forEach { a ->
            val chapterName = a.text()
            val chapterUrl = fixUrl(a.attr("href"))

            chapters.add(newChapterData(chapterName, chapterUrl))
        }

        return newStreamResponse(title, fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = fixUrlNull(cover)
            this.synopsis = desc
            // this.status = status  // Descomentar cuando se defina status
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val full = fixUrl(url)
        return try {
            val doc = app.get(full, headers = headers).document
            doc.select("div#htmlContent").html()
        } catch (e: Exception) {
            Log.e("NovelHallProvider", "Failed to load HTML: ${e.message}")
            null
        }
    }
}
