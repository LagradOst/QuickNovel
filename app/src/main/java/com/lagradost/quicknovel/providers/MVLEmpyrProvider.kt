package com.lagradost.quicknovel.providers

import android.util.Log
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

// Single cache for the full novel list with 24h TTL
private val novelListCache = CacheWithTTL<List<Map<String, Any>>>(
    maxAgeMillis = 24 * 60 * 60 * 1000L
)

private val tagCache = mutableMapOf<Int, Long>()

// Simple TTL cache implementation
class CacheWithTTL<T>(private val maxAgeMillis: Long) {
    private var data: T? = null
    private var timestamp: Long = 0

    fun get(): T? {
        val now = System.currentTimeMillis()
        return if (data != null && (now - timestamp) < maxAgeMillis) data else null
    }

    fun set(value: T) {
        data = value
        timestamp = System.currentTimeMillis()
    }
}

fun parseJsonArray(response: String): List<Map<String, Any>> {
    val resultList = mutableListOf<Map<String, Any>>()
    val jsonArray = JSONArray(response)

    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = jsonObject.get(key)
        }
        resultList.add(map)
    }
    return resultList
}

fun calculateTag(novelCode: Int): Long {
    return tagCache.getOrPut(novelCode) {
        val base = 7L
        val modulus = 1999999997L
        var result = 1L
        var power = base
        var exp = novelCode.toLong()

        while (exp > 0) {
            if ((exp and 1L) == 1L) result = (result * power) % modulus
            power = (power * power) % modulus
            exp = exp shr 1
        }
        result
    }
}

class MVLEmpyrProvider : MainAPI() {
    override val name = "MVLEmpyr"
    override val mainUrl = "https://www.mvlempyr.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_mvlempyr
    override val iconBackgroundId = R.color.colorPrimaryDark

    private val apiUrl = "https://chap.heliosarchive.online/wp-json/wp/v2/mvl-novels?"
    private val chapterNamesApiUrl = "https://chap.heliosarchive.online/wp-json/wp/v2/posts?"
    private val chapterApiURL = "https://www.mvlempyr.com/chapter/"

    override val orderBys = listOf(
        "New" to "new",
        "Chapters Desc" to "chapters_desc",
        "Chapters Asc" to "chapters_asc",
        "Average Rating" to "rating",
        "Most Reviewed" to "reviews"
    )

    override val tags = listOf(
        "All" to "all",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Fan-Fiction" to "fan-fiction",
        "Fantasy" to "fantasy",
        "Gender Bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri"
    )

    override val mainCategories = listOf(
        "All" to "All",
        "Ongoing" to "Ongoing",
        "Completed" to "Completed",
        "Hiatus" to "Hiatus"
    )

    // Load full novel list with caching
    private suspend fun loadFullNovelList(): List<Map<String, Any>> {
        novelListCache.get()?.let { return it }

        val response = app.get("${apiUrl}per_page=10000", timeout = 60).body.string()
        val novels = parseJsonArray(response)
        novelListCache.set(novels)
        return novels
    }

    // Simplified filtering and sorting
    private fun filterAndSortNovels(
        novels: List<Map<String, Any>>,
        orderBy: String?,
        status: String?,
        genre: String?
    ): List<Map<String, Any>> {
        var filtered = novels

        // Filter by status
        if (!status.isNullOrEmpty() && !status.equals("All", ignoreCase = true)) {
            filtered = filtered.filter { novel ->
                (novel["status"] as? String ?: "").equals(status, ignoreCase = true)
            }
        }

        // Filter by genre
        if (!genre.isNullOrEmpty() && !genre.equals("all", ignoreCase = true)) {
            filtered = filtered.filter { novel ->
                val genreList = when (val genreObj = novel["genre"]) {
                    is List<*> -> genreObj.filterIsInstance<String>()
                    is JSONArray -> List(genreObj.length()) { i -> genreObj.optString(i) }
                    is String -> genreObj.split(",", "·", ";").map { it.trim() }
                    else -> emptyList()
                }
                genreList.any { it.equals(genre, ignoreCase = true) }
            }
        }

        // Sort
        return when (orderBy) {
            "new" -> filtered.sortedByDescending { it["createdOn"] as? String }
            "chapters_desc" -> filtered.sortedByDescending { (it["total-chapters"] as? Number)?.toInt() ?: 0 }
            "chapters_asc" -> filtered.sortedBy { (it["total-chapters"] as? Number)?.toInt() ?: 0 }
            "rating" -> filtered.sortedByDescending { novel ->
                val rating = (novel["average-review"] as? Number)?.toDouble() ?: 0.0
                val reviews = (novel["total-reviews"] as? Number)?.toInt() ?: 0
                rating * kotlin.math.log10((reviews + 1).toDouble())
            }
            "reviews" -> filtered.sortedByDescending { (it["total-reviews"] as? Number)?.toInt() ?: 0 }
            else -> filtered
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val itemsPerPage = 30

        val novels = loadFullNovelList()
        val sorted = filterAndSortNovels(novels, orderBy, mainCategory, tag)

        val items = sorted.drop((page - 1) * itemsPerPage)
            .take(itemsPerPage)
            .mapNotNull { item ->
                val name = item["name"] as? String ?: return@mapNotNull null
                val slug = item["slug"] as? String ?: return@mapNotNull null
                val novelCode = (item["novel-code"] as? Number)?.toInt() ?: return@mapNotNull null
                val coverUrl = "https://assets.mvlempyr.app/images/300/${novelCode}.webp"
                val link = fixUrlNull("/novel/$slug") ?: return@mapNotNull null

                newSearchResponse(name, link) {
                    posterUrl = coverUrl
                }
            }

        return HeadMainPageResponse(mainUrl, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val novels = loadFullNovelList()
        val queryLower = query.lowercase()

        return novels.filter { item ->
            val name = (item["name"] as? String)?.lowercase() ?: ""
            val author = (item["author-name"] as? String)?.lowercase() ?: ""
            val tags = (item["tags"] as? List<*>)?.joinToString(" ")?.lowercase() ?: ""
            val genres = (item["genre"] as? List<*>)?.joinToString(" ")?.lowercase() ?: ""
            val synopsis = (item["synopsis-text"] as? String)?.lowercase() ?: ""

            name.contains(queryLower) ||
                    author.contains(queryLower) ||
                    tags.contains(queryLower) ||
                    genres.contains(queryLower) ||
                    synopsis.contains(queryLower)
        }.mapNotNull { item ->
            val name = item["name"] as? String ?: return@mapNotNull null
            val slug = item["slug"] as? String ?: return@mapNotNull null
            val novelCode = (item["novel-code"] as? Number)?.toInt() ?: return@mapNotNull null
            val coverUrl = "https://assets.mvlempyr.app/images/300/${novelCode}.webp"
            val link = fixUrlNull("/novel/$slug") ?: return@mapNotNull null

            newSearchResponse(name, link) {
                posterUrl = coverUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val slug = Regex("/novel/([^/]+)").find(url)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Slug not found")

        val novels = loadFullNovelList()
        val novelData = novels.find { it["slug"] == slug }
            ?: throw ErrorLoadingException("Novel not found")

        val name = novelData["name"] as? String ?: throw ErrorLoadingException("Name not found")
        val author = novelData["author-name"] as? String
        val novelCode = (novelData["novel-code"] as? Number)?.toInt() ?: 0
        val poster = "https://assets.mvlempyr.app/images/300/${novelCode}.webp"
        val synopsisHtml = novelData["synopsis"] as? String
        val synopsisText = Jsoup.parse(synopsisHtml.toString()).text()
        val status = novelData["status"] as? String
        val tag = calculateTag(novelCode)

        // Fetch chapters with pagination
        val chapters = mutableListOf<ChapterData>()
        var page = 1
        var fetchedAll = false

        while (!fetchedAll) {
            val chaptersUrl = "${chapterNamesApiUrl}tags=$tag&per_page=500&page=$page"
            val response = app.get(chaptersUrl).parsed<List<Map<String, Any>>>()

            if (response.isEmpty()) {
                fetchedAll = true
            } else {
                response.mapNotNull { item ->
                    val acf = item["acf"] as? Map<*, *> ?: return@mapNotNull null
                    val chapterName = acf["ch_name"] as? String ?: return@mapNotNull null
                    val chapterUrl = item["link"] as? String ?: return@mapNotNull null

                    val match = Regex("/chapter/(\\d+)-(\\d+)").find(chapterUrl)
                    val finalUrl = if (match != null) {
                        val chapterNumber = match.groupValues[2]
                        "${chapterApiURL}${novelCode}-${chapterNumber}"
                    } else {
                        chapterUrl
                    }

                    newChapterData(chapterName, fixUrl(finalUrl))
                }.forEach { chapters.add(it) }

                if (response.size < 500) fetchedAll = true
                else page++
            }
        }

        chapters.reverse()

        return newStreamResponse(name, fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = poster
            this.synopsis = synopsisText
            setStatus(status)
        }
    }

    override suspend fun loadHtml(url: String): String? {
        return try {
            val result = app.get(fixUrl(url))
            val body = result.body.string()
            val loadedCheerio = Jsoup.parse(body)
            loadedCheerio.selectFirst("div#chapter")?.html() ?: ""
        } catch (e: Exception) {
            Log.e("MVLEmpyrProvider", "Error loading chapter: ${e.message}")
            null
        }
    }
}
