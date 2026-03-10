package com.lagradost.quicknovel.providers

import android.os.Debug
import android.util.Log
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.utils.SessionCookieProvider
import com.lagradost.quicknovel.LibraryHelper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject


private var cachedCookies: String? = null
private var cachedCsrfToken: String? = null

//private val chapterContentCache = LruCache<String, String>(20) // Cache last 20 chapter contents

class WebnovelFanficProvider : MainAPI() {
    override val name = "Webnovel (Fanfic)"
    override val mainUrl = "https://m.webnovel.com"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_webnovel // Replace with your actual icon

    companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.131 Mobile Safari/537.36"
    }

    // Category mappings for fanfiction types
    override val tags = listOf(
        "All" to "fanfic",
        "Anime & Comics" to "fanfic-anime-comics",
        "Video Games" to "fanfic-video-games",
        "Celebrities" to "fanfic-celebrities",
        "Music & Bands" to "fanfic-music-bands",
        "Movies" to "fanfic-movies",
        "Book & Literature" to "fanfic-book-literature",
        "TV" to "fanfic-tv",
        "Theater" to "fanfic-theater",
        "Others" to "fanfic-others"
    )

    override val orderBys = listOf(
        "Popular" to "1",
        "Recommended" to "2",
        "Most Collections" to "3",
        "Rating" to "4",
        "Updated" to "5"
    )

    override val mainCategories = listOf(
        "All" to "0",
        "Ongoing" to "1",
        "Completed" to "2",
    )

    private suspend fun ensureCookiesLoaded() {
        if (cachedCookies != null && cachedCsrfToken != null) return

        val cookies = SessionCookieProvider.getValidCookie(MainActivity.context)
        val csrfToken = Regex("_csrfToken=([^;]+)").find(cookies)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("CSRF token not found in cookies")

        cachedCookies = cookies
        cachedCsrfToken = csrfToken
    }

    var lastLoadedPage = 1
    val minBooksNeeded=11
    val maxPagesToFetch=20

    val seenUrls = HashSet<String>()



    fun logLong(tag: String, message: String) {
        val maxLogSize = 4000
        for (i in 0..message.length step maxLogSize) {
            val end = (i + maxLogSize).coerceAtMost(message.length)
            Log.d(tag, message.substring(i, end))
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {

        ensureCookiesLoaded()

       // isChapterCountFilterNeeded=true

        val slug = tag ?: "fanfic"
        val categoryId = getCategoryIdFromTag(slug)

        var currentPage = if (page <= lastLoadedPage) lastLoadedPage else page
        val collectedResults = mutableListOf<SearchResponse>()
        var pagesFetched = 0


        //var apiUrl = "$mainUrl/go/pcm/category/categoryAjax?_csrfToken=$cachedCsrfToken&language=en&categoryId=$categoryId&categoryType=4&orderBy=$orderBy&pageIndex=$page&bookStatus=$mainCategory"

        fun fetchPage(p: Int): Pair<List<SearchResponse>, Boolean> {
            val apiUrl =
                "$mainUrl/go/pcm/category/categoryAjax?_csrfToken=$cachedCsrfToken&language=en&categoryId=$categoryId&categoryType=4&orderBy=$orderBy&pageIndex=$p&bookStatus=$mainCategory"

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("User-Agent", MOBILE_USER_AGENT)
                .addHeader("Referer", "$mainUrl/stories/$slug")
                .addHeader("Cookie", cachedCookies!!)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw ErrorLoadingException("Empty response")

            if(!body.startsWith("{") || body.startsWith("<!DOCTYPE"))
            {
                Log.e("WEBNOVEL", "Non-JSON response detected, skipping page.")
                return Pair(emptyList(), false)
            }
            else
            {


                val json = JSONObject(body)
                val items = json.getJSONObject("data").getJSONArray("items")
                val hadAnyRawItems = items.length() > 0

                //logLong("WEBNOVEL", items.toString())

                if(!hadAnyRawItems)
                {
                    showToast("No More Books !!")
                }

                val filtered = mutableListOf<SearchResponse>()

                for (i in 0 until items.length()) {
                    val book = items.getJSONObject(i)
                    val bookId = book.optString("bookId") ?: continue
                    val link = fixUrlNull("$mainUrl/book/$bookId") ?: continue
                    if (!seenUrls.add(link)) continue // skip duplicates

                    val chapterCount = book.optString("chapterNum", "0")
                   /* if (!LibraryHelper.isChapterCountInRange(ChapterFilter, chapterCount.toString())) {
                        continue
                    }*/

                    val title = book.optString("bookName", "Untitled")
                    val cover =
                        "https://book-pic.webnovel.com/bookcover/$bookId?imageMogr2/thumbnail/180x|imageMogr2/format/webp|imageMogr2/quality/70!"


                    filtered.add(newSearchResponse(title, link) {
                        posterUrl = cover
                        //totalChapterCount = chapterCount
                    })
                }

                //Log.d("WEBNOVEL","Before: ${items.length()}  Filtered: ${filtered.size}")
                lastLoadedPage = p

                return Pair(filtered, hadAnyRawItems)
            }


        }

        // Keep fetching pages until enough results or end of listing
        while (collectedResults.size < minBooksNeeded && pagesFetched < maxPagesToFetch) {
            val (pageResults, hadRawItems) = fetchPage(currentPage)
            Log.e("WEBNOVEL","Current Page: ${currentPage} ${collectedResults.size}")

            if (!hadRawItems) break // stop if API returned no items (end)

            if (pageResults.isNotEmpty()) {
                collectedResults.addAll(pageResults)
            }

            currentPage++
            pagesFetched++
            //Thread.sleep(1000)
    kotlinx.coroutines.delay(1000) // Cambiar Thread.sleep por delay
        }
        //Log.d("WEBNOVEL","Final: ${collectedResults.size} Last page: ${lastLoadedPage}")
        val apiUrl =
            "$mainUrl/go/pcm/category/categoryAjax?_csrfToken=$cachedCsrfToken&language=en&categoryId=$categoryId&categoryType=4&orderBy=$orderBy&pageIndex=$lastLoadedPage&bookStatus=$mainCategory"



        return HeadMainPageResponse(apiUrl, collectedResults)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureCookiesLoaded()

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<SearchResponse>()
        val allResults = mutableListOf<SearchResponse>()
        var pageIndex = 1
        var isLastPage = false

        while (!isLastPage) {
            val nextPage = fetchSearchPage(encodedQuery, pageIndex)
            allResults.addAll(nextPage.first)
            isLastPage = nextPage.second
            pageIndex++
        }
        return allResults
    }

    private fun fetchSearchPage(encodedQuery: String, pageIndex: Int): Pair<List<SearchResponse>, Boolean> {
        val url = "$mainUrl/go/pcm/search/result?_csrfToken=$cachedCsrfToken&pageIndex=$pageIndex&type=fanfic&keywords=$encodedQuery"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", MOBILE_USER_AGENT)
            .addHeader("Referer", "$mainUrl/fanfic-search?keywords=$encodedQuery")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Cookie", cachedCookies!!)
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val body = response.body?.string() ?: return Pair(emptyList(), true)

        val json = JSONObject(body)
        val data = json.optJSONObject("data") ?: return Pair(emptyList(), true)
        val fanficData = data.optJSONObject("fanficBookInfo") ?: return Pair(emptyList(), true)
        val books = fanficData.optJSONArray("fanficBookItems") ?: return Pair(emptyList(), true)

        val results = mutableListOf<SearchResponse>()
        for (i in 0 until books.length()) {
            val book = books.getJSONObject(i)
            val id = book.optString("bookId") ?: continue
            val title = book.optString("bookName", "Untitled")
            val cover = "https://book-pic.webnovel.com/bookcover/$id?imageMogr2/thumbnail/180x|imageMogr2/format/webp|imageMogr2/quality/70!"
            val link = "$mainUrl/book/$id"
            val chapterCount=book.optString("chapterNum","0")

            results.add( newSearchResponse(title, fixUrl(link)) {
                this.posterUrl = cover;
                //this.totalChapterCount=chapterCount
            })

        }

        val isLastPage = fanficData.optInt("isLast", 1) == 1
        return Pair(results, isLastPage)
    }



    override suspend fun load(url: String): StreamResponse {
       // isOpeningBook=true
        ensureCookiesLoaded()

        val bookId = url.substringAfterLast("/")

        // Fetch book info from API
        val detailUrl = "$mainUrl/go/pcm/book/get-book-detail?_csrfToken=$cachedCsrfToken&bookId=$bookId"
        val infoRequest = Request.Builder()
            .url(detailUrl)
            .addHeader("User-Agent", MOBILE_USER_AGENT)
            .addHeader("Cookie", cachedCookies!!)
            .build()
        Log.d("WebNovel","${detailUrl}")

        val client = OkHttpClient()
        val infoResponse = client.newCall(infoRequest).execute()
        val infoBody = infoResponse.body?.string() ?: throw ErrorLoadingException("Empty book info")

        val bookInfo = JSONObject(infoBody)
            .getJSONObject("data")
            .getJSONObject("bookInfo")

        val title = bookInfo.optString("bookName", "Untitled")
        val author = bookInfo.optString("authorName", "Unknown")
        val description = bookInfo.optString("description", "")
        val cover = "https://book-pic.webnovel.com/bookcover/$bookId?imageMogr2/thumbnail/180x|imageMogr2/format/webp|imageMogr2/quality/70!"
        val status = when (bookInfo.optInt("actionStatus", 0)) {
            30 -> "ongoing"
            50 -> "completed"
            else -> "paused"
        }

        val catalogUrl = "$mainUrl/book/$bookId/catalog"
        val catalogRequest = Request.Builder()
            .url(catalogUrl)
            .addHeader("User-Agent", MOBILE_USER_AGENT)
            .addHeader("Cookie", cachedCookies!!)
            .build()

        val catalogResponse = client.newCall(catalogRequest).execute()
        val catalogHtml = catalogResponse.body?.string() ?: throw ErrorLoadingException("Empty catalog page")

        val doc = org.jsoup.Jsoup.parse(catalogHtml)
        val chapterLinks = doc.select("a.lh24.db.oh.fs14.clearfix.g_row.pr.pt8.pb8")

        val chapters = mutableListOf<ChapterData>()

        for ((i, el) in chapterLinks.withIndex()) {
            val chapterUrl = el.attr("href") // Already absolute URL
            val chapterName = el.selectFirst("strong.styles_chapter_name__lQv69 > span")
                ?.text()?.trim() ?: "Chapter ${i + 1}"

            chapters.add(ChapterData(chapterName,chapterUrl))
        }

        return newStreamResponse(title, fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = cover
            this.synopsis = description
            setStatus(status)
        }
    }

    override suspend fun loadHtml(url: String): String? {
       /* chapterContentCache[url]?.let {
            Log.d("WebnovelFanficProvider", "Loaded chapter content from cache for $url")
            return it
        }*/

        val fullUrl = fixUrl(url)
        Log.d("WebnovelFanficProvider", "Loading Chapter HTML from URL: $fullUrl")

        try {
            val document = app.get(fullUrl).document

            // Select <div class="cha-content " data-report-l1="3"><div class="cha-words ...">
            val contentElement = document.selectFirst("div.cha-content[data-report-l1='3'] div.cha-words")
            val content = contentElement?.html()

          /*  if (content != null) {
                Log.d("WebnovelFanficProvider", "Chapter Content Loaded Successfully")
                chapterContentCache[url] = content
                return content
            } else {
                Log.e("WebnovelFanficProvider", "Chapter Content NOT FOUND in expected structure")
            }*/
        } catch (e: Exception) {
            Log.e("WebnovelFanficProvider", "Failed to load chapter HTML: ${e.message}")
        }

        return null
    }




    private fun getCategoryIdFromTag(tag: String): String {
        return when (tag) {
            "fanfic-anime-comics" -> "81006"
            "fanfic-video-games" -> "81005"
            "fanfic-celebrities" -> "81002"
            "fanfic-music-bands" -> "81003"
            "fanfic-movies" -> "81007"
            "fanfic-book-literature" -> "81001"
            "fanfic-tv" -> "81008"
            "fanfic-theater" -> "81004"
            "fanfic-others" -> "81009"
            "fanfic" -> "0"
            else -> "0"
        }
    }
}
