package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import android.util.Log

class WuxiaWorldProvider : MainAPI() {
    override val name = "WuxiaWorld"
    override val mainUrl = "https://www.wuxiaworld.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_wuxiaworldonline

    override val tags = listOf(
        "All" to "",
        "Action" to "Action",
        "Adventure" to "Adventure",
        "Comedy" to "Comedy",
        "Drama" to "Drama",
        "Fantasy" to "Fantasy",
        "Martial Arts" to "Martial Arts",
        "Mystery" to "Mystery",
        "Romance" to "Romance",
        "Sci-fi" to "Sci-fi",
        "Slice of Life" to "Slice of Life",
        "Supernatural" to "Supernatural",
        "Tragedy" to "Tragedy",
        "Xianxia" to "Xianxia",
        "Xuanhuan" to "Xuanhuan",
    )

    override val orderBys = listOf(
        "All" to "",
        "Popular" to "popular",
        "New" to "new",
        "Completed" to "completed",
    )

    private fun parseNovelList(items: JSONArray): List<SearchResponse> {
        val novels = mutableListOf<SearchResponse>()
        for (i in 0 until items.length()) {
            try {
                val novel = items.getJSONObject(i)
                val name = novel.optString("name", "")
                val slug = novel.optString("slug", "")
                val coverUrl = novel.optString("coverUrl", null)

                if (name.isNotEmpty() && slug.isNotEmpty()) {
                    novels.add(
                        SearchResponse(
                            name = name,
                            url = "$mainUrl/novel/$slug",
                            posterUrl = coverUrl,
                            rating = null,
                            latestChapter = null,
                            apiName = this.name
                        )
                    )
                }
            } catch (e: Exception) {
                continue
            }
        }
        return novels
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/api/novels"
        val response = app.get(url)

        return try {
            val json = JSONObject(response.text)
            val items = json.getJSONArray("items")
            HeadMainPageResponse(url, parseNovelList(items))
        } catch (e: Exception) {
            HeadMainPageResponse(url, emptyList())
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/api/novels/search?query=$encodedQuery"
        val response = app.get(url)

        return try {
            val json = JSONObject(response.text)
            val items = json.getJSONArray("items")
            parseNovelList(items)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extrae capítulos del HTML retornado por el endpoint AJAX
     */
    private fun extractChaptersFromHtml(document: org.jsoup.nodes.Document): List<Pair<String, String>> {
        val chapterList = mutableListOf<Pair<String, String>>()
        
        Log.d("WuxiaWorld", "=== EXTRAYENDO CAPÍTULOS DEL HTML AJAX ===")
        
        // Selector basado en IReader
        val chapters = document.select("div.MuiCollapse-root.MuiCollapse-vertical.MuiCollapse-entered.ww-c4sutr > div > div > div > div > div > div > a")
        
        Log.d("WuxiaWorld", "Capítulos encontrados: ${chapters.size}")
        
        chapters.forEach { element ->
            try {
                val chapterName = element.selectFirst("span span")?.text()?.trim() ?: ""
                val chapterUrl = element.attr("href")
                
                if (chapterName.isNotEmpty() && chapterUrl.isNotEmpty()) {
                    val fullUrl = if (chapterUrl.startsWith("http")) {
                        chapterUrl
                    } else {
                        "https://www.wuxiaworld.com$chapterUrl"
                    }
                    
                    chapterList.add(chapterName to fullUrl)
                    Log.d("WuxiaWorld", "✓ Capítulo: $chapterName")
                }
            } catch (e: Exception) {
                Log.e("WuxiaWorld", "Error procesando capítulo", e)
            }
        }
        
        Log.d("WuxiaWorld", "Total capítulos extraídos: ${chapterList.size}")
        return chapterList
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = url.substringAfter("/novel/").substringBefore("/").trim()

        if (slug.isEmpty()) {
            throw ErrorLoadingException("Invalid novel URL")
        }

        val document = app.get(url).document
        
        val name = document.selectFirst("h1")?.text()
            ?: throw ErrorLoadingException("Could not find novel name")
        
        val posterUrl = document.selectFirst("img[src*=cover]")?.attr("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        
        val synopsis = document.selectFirst("div.novel-summary")?.text()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
        
        val author = document.selectFirst("span.author")?.text()
        val genres = document.select("a[href*=genre]").map { it.text() }
        val statusText = document.selectFirst("span.status")?.text()

        // Obtener capítulos del endpoint AJAX
        Log.d("WuxiaWorld", "Obteniendo capítulos para: $slug")
        
        val chapters = try {
            val ajaxUrl = "$url/ajax/chapters/"
            Log.d("WuxiaWorld", "POST a: $ajaxUrl")
            
            val ajaxResponse = app.post(ajaxUrl)
            val ajaxDocument = ajaxResponse.document
            
            val chapterList = extractChaptersFromHtml(ajaxDocument)
            
            if (chapterList.isNotEmpty()) {
                // Invertir para mostrar en orden correcto (de más viejo a más nuevo)
                chapterList.reversed().map { (name, url) -> newChapterData(name, url) }
            } else {
                Log.w("WuxiaWorld", "No se encontraron capítulos")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("WuxiaWorld", "Error obteniendo capítulos: ${e.message}", e)
            emptyList()
        }

        return newStreamResponse(name, url, chapters) {
            this.posterUrl = posterUrl
            this.synopsis = synopsis
            this.author = author
            this.tags = genres
            setStatus(statusText)
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document

        val content = document.selectFirst("#chapter-content")
            ?: document.selectFirst(".chapter-content")
            ?: document.selectFirst("div.chapter-body")
            ?: document.selectFirst("div[class*=chapter]")

        if (content == null) {
            return "<p>Unable to load chapter content.</p>"
        }

        content.select("script, .chapter-nav, .ads, .advertisement").remove()
        return content.html()
    }
}
