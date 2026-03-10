package com.lagradost.quicknovel.providers

import android.util.Log
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.json.JSONObject

class MtlbooksProvider : MainAPI() {
    override val name = "MTL Books"
    override val mainUrl = "https://mtlbooks.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_mtlbooks

    override val mainCategories = listOf(
        "All" to "",
        "Completed" to "Completed",
        "Ongoing" to "Ongoing",
        "Hiatus" to "Hiatus"
    )

    override val tags = listOf(
        "All" to "",
        "Action" to "Action",
        "Adventure" to "Adventure",
        "Adult" to "Adult",
        "Anime" to "Anime",
        "BL" to "BL",
        "Billionaire" to "Billionaire",
        "CEO" to "CEO",
        "Comedy" to "Comedy",
        "Contemporary Romance" to "Contemporary+Romance",
        "Cooking" to "Cooking",
        "Drama" to "Drama",
        "Eastern Fantasy" to "Eastern+Fantasy",
        "Ecchi" to "Ecchi",
        "Fan-Fiction" to "Fan-Fiction",
        "Faloo" to "Faloo",
        "Fantasy" to "Fantasy",
        "Game" to "Game",
        "Gender Bender" to "Gender+Bender",
        "Harem" to "Harem",
        "Historical" to "Historical",
        "Historical Romance" to "Historical+Romance",
        "Horror" to "Horror",
        "Josei" to "Josei",
        "LGBT" to "LGBT",
        "Magic" to "Magic",
        "Martial Arts" to "Martial+Arts",
        "Mature" to "Mature",
        "Mecha" to "Mecha",
        "Military" to "Military",
        "Modern" to "Modern",
        "Modern Romance" to "Modern+Romance",
        "Mystery" to "Mystery",
        "Psychological" to "Psychological",
        "Reincarnation" to "Reincarnation",
        "Romance" to "Romance",
        "School Life" to "School+Life",
        "Sci-fi" to "Sci-fi",
        "Shoujo" to "Shoujo",
        "Shounen" to "Shounen",
        "Slice of Life" to "Slice+Of+Life",
        "Smut" to "Smut",
        "Sports" to "Sports",
        "Supernatural" to "Supernatural",
        "Urban" to "Urban",
        "Urban Romance" to "Urban+Romance",
        "Virtual Reality" to "Virtual+Reality",
        "Wuxia" to "Wuxia",
        "Xianxia" to "Xianxia",
        "Xuanhuan" to "Xuanhuan",
        "Yaoi" to "Yaoi",
        "Yuri" to "Yuri"
    )

    override val orderBys = listOf(
        "New" to "recent",
        "Popular" to "popular",
        "Updates" to "updated",
        "Chapter" to "chaptercount",
        "BookMarks" to "bookmarkcount",
        "Word Count" to "wordcount",
        "Views Today" to "dailyviews",
        "Views Week" to "weeklyviews",
        "Views Month" to "monthlyviews",
        "Viewed All" to "views"
    )

    private var lastLoadedPage = 1
    private val minBooksNeeded = 11
    private val maxPagesToFetch = 10
    private val apiURL = "https://alpha.mtlbooks.com/api/v1"
    private val seenUrls = HashSet<String>()

    data class ChapterInfo(
        val novelSlug: String,
        val chapterSlug: String
    )

    private suspend fun apiRequest(url: String, jsonBody: String): JSONObject {
        return try {
            val headers = mapOf(
                "Content-Type" to "application/json",
                "Accept" to "application/json",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Origin" to mainUrl,
                "Referer" to mainUrl,
                "Accept-Language" to "en-US,en;q=0.9",
                "Cache-Control" to "no-cache",
                "Pragma" to "no-cache"
            )

            Log.d("MTL_BOOKS", "Requesting: $url")
            Log.d("MTL_BOOKS", "Body: $jsonBody")

            val response = app.post(
                url,
                headers = headers,
                json = JSONObject(jsonBody)
            )

            Log.d("MTL_BOOKS", "Response: ${response.text}")

            val jsonResponse = JSONObject(response.text)
            val result = jsonResponse.optJSONObject("result")
            
            if (result == null) {
                Log.e("MTL_BOOKS", "No 'result' field in response")
                Log.e("MTL_BOOKS", "Full response: ${jsonResponse.toString(2)}")
            }

            result ?: JSONObject()
        } catch (e: Exception) {
            Log.e("MTL_BOOKS", "API call failed: ${e.message}")
            e.printStackTrace()
            JSONObject()
        }
    }

    private fun getChapterList(data: JSONObject, slug: String): List<ChapterData> {
        val chapters = mutableListOf<ChapterData>()
        try {
            val chapterLists = data.getJSONArray("chapter_lists")
            for (i in 0 until chapterLists.length()) {
                val item = chapterLists.getJSONObject(i)
                val chapterUrl = "$mainUrl/novel/$slug/${item.optString("chapter_slug")}"
                val chapterName = item.optString("chapter_title")
                chapters.add(ChapterData(chapterName, chapterUrl))
            }
        } catch (e: Exception) {
            Log.e("MTL_BOOKS", "Failed to parse chapter list: ${e.message}")
        }
        return chapters
    }

    private fun parseNovelAndChapter(url: String): ChapterInfo? {
        return try {
            val cleanedUrl = url.split("?")[0]
            val parts = cleanedUrl.split("/").filter { it.isNotEmpty() }
            val novelIndex = parts.indexOf("novel")

            if (novelIndex != -1 && parts.size > novelIndex + 2) {
                ChapterInfo(parts[novelIndex + 1], parts[novelIndex + 2])
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MTL_BOOKS", "Failed to parse URL: ${e.message}")
            null
        }
    }

    private fun convertJsonContentToHtml(content: String): String {
        val paragraphs = content.split("\n\n")
        return paragraphs.joinToString("\n") { paragraph ->
            "<p>${paragraph.replace("\n", "<br>")}</p>"
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val collectedResults = mutableListOf<SearchResponse>()
        var currentPage = if (page <= lastLoadedPage) lastLoadedPage else page
        var pagesFetched = 0

        while (collectedResults.size < minBooksNeeded && pagesFetched < maxPagesToFetch) {
            val params = buildString {
                append("page=$currentPage")
                if (!orderBy.isNullOrBlank()) append("&order=$orderBy")
                if (!tag.isNullOrBlank()) append("&include_genres=$tag")
                if (!mainCategory.isNullOrBlank()) append("&status=$mainCategory")
            }
            
            val url = "$apiURL/search?$params"
            
            try {
                Log.d("MTL_BOOKS", "Main page URL: $url")
                val response = app.get(url)
                Log.d("MTL_BOOKS", "Main page response: ${response.text}")
                
                val json = JSONObject(response.text)
                val result = json.optJSONObject("result")
                
                if (result == null) {
                    Log.e("MTL_BOOKS", "No result in main page response")
                    break
                }
                
                val items = result.getJSONArray("data")

                if (items.length() == 0) break

                for (i in 0 until items.length()) {
                    val book = items.getJSONObject(i)
                    val slug = book.optString("slug") ?: continue
                    val link = fixUrlNull("$apiURL/novels/$slug") ?: continue

                    if (!seenUrls.add(link)) continue

                    val title = book.optString("name", "Untitled")
                    val pic = book.optString("thumbnail")
                    val cover = "https://wsrv.nl/?url=https://cdn.mtlbooks.com/poster/$pic&w=300&h=400&fit=cover&output=webp&maxage=3M"

                    collectedResults.add(newSearchResponse(title, link) {
                        posterUrl = cover
                    })
                }

                lastLoadedPage = currentPage
                currentPage++
                pagesFetched++
            } catch (e: Exception) {
                Log.e("MTL_BOOKS", "Error in loadMainPage: ${e.message}")
                e.printStackTrace()
                break
            }
        }

        val params = buildString {
            append("page=$lastLoadedPage")
            if (!orderBy.isNullOrBlank()) append("&order=$orderBy")
            if (!tag.isNullOrBlank()) append("&include_genres=$tag")
            if (!mainCategory.isNullOrBlank()) append("&status=$mainCategory")
        }
        val url = "$apiURL/search?$params"
        return HeadMainPageResponse(url, collectedResults)
    }

    override suspend fun load(url: String): StreamResponse? {
        return try {
            val response = app.get(url)
            val json = JSONObject(response.text)
            val result = json.getJSONObject("result")

            val title = result.optString("name", "Untitled")
            val description = result.optString("description", "")
            val pic = result.optString("thumbnail")
            val cover = "https://wsrv.nl/?url=https://cdn.mtlbooks.com/poster/$pic&w=300&h=400&fit=cover&output=webp&maxage=3M"
            val status = result.optString("status")
            val slug = result.optString("slug")

            val chapters = mutableListOf<ChapterData>()
            val chapApi = "$apiURL/chapters/list"

            val jsonBody = """{"novel_slug": "$slug","page": 1,"order": "ASC"}"""
            val firstData = apiRequest(chapApi, jsonBody)
            chapters.addAll(getChapterList(firstData, slug))

            val pagination = firstData.optJSONObject("pagination")
            if (pagination != null) {
                val totalItems = pagination.optInt("total")
                val limit = pagination.optInt("limit")
                val totalPages = (totalItems + limit - 1) / limit

                for (page in 2..totalPages) {
                    val pageJsonBody = """{"novel_slug": "$slug","page": $page,"order": "ASC"}"""
                    chapters.addAll(getChapterList(apiRequest(chapApi, pageJsonBody), slug))
                }
            }

            newStreamResponse(title, fixUrl(url), chapters) {
                posterUrl = cover
                synopsis = description
                setStatus(status)
            }
        } catch (e: Exception) {
            Log.e("MTL_BOOKS", "Failed to load novel: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override suspend fun loadHtml(url: String): String? {
        return try {
            val fullUrl = fixUrl(url)
            val chapterInfo = parseNovelAndChapter(fullUrl) ?: return null

            val chapApi = "$apiURL/chapters/read"
            val jsonBody = """{"novel_slug": "${chapterInfo.novelSlug}","chapter_slug": "${chapterInfo.chapterSlug}"}"""
            val chap = apiRequest(chapApi, jsonBody)
            val content = chap.optJSONObject("chapter")?.optString("content")

            if (!content.isNullOrBlank()) {
                val html = convertJsonContentToHtml(content)
                Log.d("MTL_BOOKS", "Chapter loaded successfully")
                html
            } else {
                Log.e("MTL_BOOKS", "Chapter content is empty")
                null
            }
        } catch (e: Exception) {
            Log.e("MTL_BOOKS", "Failed to load chapter: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$apiURL/search?q=$encodedQuery&page=1&order=popular"
            
            Log.d("MTL_BOOKS", "Search URL: $url")
            val response = app.get(url)
            Log.d("MTL_BOOKS", "Search response: ${response.text}")
            
            val json = JSONObject(response.text)
            val result = json.optJSONObject("result")

            if (result == null) {
                Log.e("MTL_BOOKS", "No result in search response")
                return emptyList()
            }

            val items = result.getJSONArray("data")

            val results = mutableListOf<SearchResponse>()

            for (i in 0 until items.length()) {
                val book = items.getJSONObject(i)
                val slug = book.optString("slug") ?: continue
                val link = fixUrlNull("$apiURL/novels/$slug") ?: continue

                val title = book.optString("name", "Untitled")
                val pic = book.optString("thumbnail")
                val cover = "https://wsrv.nl/?url=https://cdn.mtlbooks.com/poster/$pic&w=300&h=400&fit=cover&output=webp&maxage=3M"

                results.add(newSearchResponse(title, link) {
                    posterUrl = cover
                })
            }

            results
        } catch (e: Exception) {
            Log.e("MTL_BOOKS", "Search failed: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}
