package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import java.io.File

open class LnMtlProvider : MainAPI() {
    override val name = "LnMtl"
    override val mainUrl = "https://lnmtl.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_lnmtl

    override val tags = listOf(
        "All" to "",
        "Action" to "Action",
        "Adventure" to "Adventure",
        "Comedy" to "Comedy",
        "Drama" to "Drama",
        "Fantasy" to "Fantasy",
        "Harem" to "Harem",
        "Mystery" to "Mystery",
        "Romance" to "Romance",
        "School Life" to "School Life",
        "Sci-fi" to "Sci-fi",
        "Supernatural" to "Supernatural",
        "Tragedy" to "Tragedy",
    )

    override val orderBys = listOf(
        "Favourites" to "favourites",
        "Latest" to "date",
        "By Name" to "name"
    )

    private val jsonDecoder = Json {
        ignoreUnknownKeys = true
    }

    // Directorio de caché
    private val cacheDir: File
        get() {
            val dir = File(MainActivity.filesDirSafe, "lnmtl_cache")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    // Obtener archivo de caché para una novela
    private fun getCacheFile(novelUrl: String): File {
        val filename = novelUrl.hashCode().toString() + ".json"
        return File(cacheDir, filename)
    }

    // Guardar capítulos en caché
    private fun saveChaptersToCache(novelUrl: String, chapters: List<ChapterData>) {
        try {
            val cacheFile = getCacheFile(novelUrl)
            val chaptersJson = chapters.map { chapter ->
                """{"url":"${chapter.url}","name":"${chapter.name?.replace("\"", "\\\"")}"}"""
            }.joinToString(",", "[", "]")
            
            cacheFile.writeText(chaptersJson)
            android.util.Log.d("LnMtlProvider", "Cache saved: ${cacheFile.absolutePath}")
        } catch (e: Exception) {
            android.util.Log.e("LnMtlProvider", "Error saving cache: ${e.message}")
        }
    }

    // Cargar capítulos del caché
    private fun loadChaptersFromCache(novelUrl: String): List<ChapterData>? {
        return try {
            val cacheFile = getCacheFile(novelUrl)
            if (!cacheFile.exists()) {
                return null
            }
            
            // Verificar si el caché tiene más de 24 horas
            val cacheAge = System.currentTimeMillis() - cacheFile.lastModified()
            if (cacheAge > 24 * 60 * 60 * 1000) { // 24 horas
                android.util.Log.d("LnMtlProvider", "Cache expirado")
                cacheFile.delete()
                return null
            }
            
            val cacheTxt = cacheFile.readText()
            val jsonArray = jsonDecoder.parseToJsonElement(cacheTxt).jsonArray
            
            val chapters = jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val url = obj["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                
                newChapterData(url = url, name = name)
            }
            
            android.util.Log.d("LnMtlProvider", "Cache loaded: ${chapters.size} chapters")
            return chapters
        } catch (e: Exception) {
            android.util.Log.e("LnMtlProvider", "Error loading cache: ${e.message}")
            return null
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val sortBy = when (orderBy) {
            "date" -> "date"
            "name" -> "name"
            else -> "favourites"
        }
        
        val url = "$mainUrl/novel?orderBy=$sortBy&order=desc&filter=all&page=$page"
        val document = app.get(url).document
        
        val headers = document.select(".media")
        val returnValue = headers.mapNotNull { h ->
            val linkElement = h.selectFirst(".media-title a") ?: return@mapNotNull null
            newSearchResponse(
                name = linkElement.text().trim(),
                url = linkElement.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(h.selectFirst("img")?.attr("src"))
            }
        }
        
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        return try {
            val response = app.get(url)
            val html = response.text.replace(Regex(">\\s+<"), "><")
            
            val chapterTextParts = mutableListOf<String>()
            
            val pattern = """<sentence[^>]*class="translated"[^>]*>(.*?)</sentence>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            
            val matches = pattern.findAll(html)
            matches.forEach { match ->
                val content = match.groupValues[1]
                if (content.isNotEmpty()) {
                    chapterTextParts.add("<p>${content.trim()}</p>")
                }
            }
            
            if (chapterTextParts.isNotEmpty()) {
                return chapterTextParts.joinToString("\n").replace("„", "\"")
            }
            
            val document = Jsoup.parse(html)
            val paragraphs = document.select("p")
            
            paragraphs.forEach { p ->
                val text = p.text()
                if (text.isNotEmpty() && text.length > 20) {
                    chapterTextParts.add("<p>${text}</p>")
                }
            }
            
            return if (chapterTextParts.isNotEmpty()) {
                chapterTextParts.joinToString("\n")
            } else {
                "<p>No content found</p>"
            }
            
        } catch (e: Exception) {
            android.util.Log.e("LnMtlProvider", "loadHtml error: ${e.message}")
            "<p>Error loading content</p>"
        }
    }
 
    override suspend fun search(query: String): List<SearchResponse> {
        val homeHtml = app.get(mainUrl).text
        
        val jsonPath = Regex("prefetch: '/(.*?\\.json)'").find(homeHtml)?.groupValues?.get(1)
            ?: return emptyList()
        
        val jsonUrl = "$mainUrl/$jsonPath"
        val jsonText = app.get(jsonUrl).text
        
        val jsonArray = jsonDecoder.parseToJsonElement(jsonText).jsonArray
        
        return jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            
            if (!name.lowercase().contains(query.lowercase())) return@mapNotNull null
            
            val slug = obj["slug"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val image = obj["image"]?.jsonPrimitive?.content ?: ""
            
            newSearchResponse(
                name = name,
                url = "$mainUrl/novel/$slug"
            ) {
                posterUrl = fixUrlNull(image)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val response = app.get(url)
            val document = response.document
            
            val name = document.selectFirst("span.novel-name")?.text() 
                ?: document.selectFirst(".novel-name")?.text()
                ?: return null
                
            val cover = fixUrlNull(
                document.selectFirst(".media-left img")?.attr("src")
                    ?: document.selectFirst("img.img-rounded")?.attr("src")
            )
            
            val synopsis1 = document.selectFirst(".description p")?.text()
                ?: document.selectFirst(".synopsis")?.text()
            
            // Intentar cargar del caché primero
            val cachedChapters = loadChaptersFromCache(url)
            if (cachedChapters != null) {
                android.util.Log.d("LnMtlProvider", "Usando capítulos del caché")
                return newStreamResponse(
                    url = url,
                    name = name,
                    data = cachedChapters
                ) {
                    posterUrl = cover
                    synopsis = synopsis1
                }
            }
            
            android.util.Log.d("LnMtlProvider", "Capítulos no en caché, descargando...")
            
            val scripts = document.select("script")
            var volumeJson = ""
            
            scripts.forEach { script ->
                val html = script.html()
                if (html.contains("lnmtl.volumes")) {
                    volumeJson = html.substringAfter("lnmtl.volumes = ")
                        .substringBefore(";")
                }
            }
            
           /* if (volumeJson.isEmpty()) {
                android.util.Log.e("LnMtlProvider", "No volume JSON found in page")
                return null
            }*/
            
            val chapters = mutableListOf<ChapterData>()
            
            try {
                val volumes = jsonDecoder.parseToJsonElement(volumeJson).jsonArray
                android.util.Log.d("LnMtlProvider", "Total volumes found: ${volumes.size}")
                
                volumes.forEach { volumeElement ->
                    val volumeObj = volumeElement.jsonObject
                    val volumeId = volumeObj["id"]?.jsonPrimitive?.content
                    val volumeName = volumeObj["name"]?.jsonPrimitive?.content ?: "Unknown"
                    
                    if (!volumeId.isNullOrEmpty()) {
                        var currentPage = 1
                        var hasMorePages = true
                        var volumeChapterCount = 0
                        
                        while (hasMorePages) {
                            val volumeUrl = "$mainUrl/chapter?page=$currentPage&volumeId=$volumeId"
                            val volumeChapterResponse = app.get(volumeUrl)
                            val volumeChapterJson = volumeChapterResponse.text
                            
                            val volumeResponse = jsonDecoder.parseToJsonElement(volumeChapterJson).jsonObject
                            val volumeChapters = volumeResponse["data"]?.jsonArray
                            val lastPage = volumeResponse["last_page"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                            
                            volumeChapters?.forEach { element ->
                                val obj = element.jsonObject
                                val chapterUrl = obj["slug"]?.jsonPrimitive?.content
                                val title = obj["title"]?.jsonPrimitive?.content
                                val number = obj["number"]?.jsonPrimitive?.content
                                
                                if (!chapterUrl.isNullOrEmpty() && !title.isNullOrEmpty()) {
                                    chapters.add(
                                        newChapterData(
                                            url = "$mainUrl/chapter/$chapterUrl",
                                            name = "${number?.let { "#$it - " } ?: ""}$title"
                                        )
                                    )
                                    volumeChapterCount++
                                }
                            }
                            
                            currentPage++
                            hasMorePages = currentPage <= lastPage
                            
                            // Pequeña pausa entre requests
                            if (hasMorePages) {
                                kotlinx.coroutines.delay(100)
                            }
                        }
                        
                       // android.util.Log.d("LnMtlProvider", "Volume '$volumeName' complete: $volumeChapterCount chapters added")
                    }
                }
                
                // Guardar en caché después de descargar
                if (chapters.isNotEmpty()) {
                    saveChaptersToCache(url, chapters)
                }
                
            } catch (e: Exception) {
                //android.util.Log.e("LnMtlProvider", "Error parsing volumes: ${e.message}", e)
            }
            
            //android.util.Log.d("LnMtlProvider", "Total chapters loaded: ${chapters.size}")
            
            return newStreamResponse(
                url = url,
                name = name,
                data = chapters
            ) {
                posterUrl = cover
                synopsis = synopsis1
            }
        } catch (e: Exception) {
            //android.util.Log.e("LnMtlProvider", "Error in load: ${e.message}", e)
            null
        }
    }
}
