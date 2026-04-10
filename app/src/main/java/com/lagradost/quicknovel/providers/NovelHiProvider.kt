package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.util.AppUtils.mapper

private data class NovelHiGenre(
    @JsonProperty("genreId") val genreId: String? = null,
    @JsonProperty("genreName") val genreName: String? = null,
)

private data class NovelHiBook(
    @JsonProperty("bookName") val bookName: String? = null,
    @JsonProperty("authorName") val authorName: String? = null,
    @JsonProperty("simpleName") val simpleName: String? = null,
    @JsonProperty("picUrl") val picUrl: String? = null,
    @JsonProperty("rate") val rate: String? = null,
    @JsonProperty("bookStatus") val bookStatus: String? = null,
    @JsonProperty("lastIndexName") val lastIndexName: String? = null,
    @JsonProperty("bookDesc") val bookDesc: String? = null,
    @JsonProperty("genres") val genres: List<NovelHiGenre>? = null,
)

private data class NovelHiSearchData(
    @JsonProperty("list") val list: List<NovelHiBook>? = null,
)

private data class NovelHiResponse(
    @JsonProperty("code") val code: String? = null,
    @JsonProperty("data") val data: NovelHiSearchData? = null,
)

class NovelHiProvider : MainAPI() {
    override val name = "NovelHi"
    override val mainUrl = "https://novelhi.com"
    override val hasMainPage = true

    override val tags = listOf(
        "All" to "",
        "Action" to "1",
        "Adventure" to "3",
        "Comedy" to "4",
        "Fantasy" to "9",
        "Game" to "10",
        "Gender Bender" to "11",
        "Harem" to "12",
        "Historical" to "13",
        "Horror" to "14",
        "Martial Arts" to "16",
        "Mature" to "17",
        "Mecha" to "18",
        "Military" to "19",
        "Mystery" to "20",
        "Romance" to "22",
        "School Life" to "23",
        "Sci-fi" to "24",
        "Slice of Life" to "30",
        "Sports" to "32",
        "Supernatural" to "33",
        "Tragedy" to "34",
        "Urban Life" to "35",
        "Wuxia" to "36",
        "Xianxia" to "37",
        "Xuanhuan" to "38",
        "Yaoi" to "39",
        "Yuri" to "40",
    )

    override val orderBys = listOf(
        "Popular" to "0",
        "New" to "1",
    )

    private suspend fun fetchBooks(
        keyword: String = "",
        page: Int = 1,
        limit: Int = 20,
        genreId: String = "",
        sort: String = "",
    ): List<NovelHiBook> {
        val params = buildString {
            append("curr=$page&limit=$limit")
            if (keyword.isNotBlank()) append("&keyword=$keyword")
            if (genreId.isNotBlank()) append("&bookGenres=$genreId")
            if (sort.isNotBlank()) append("&sort=$sort")
        }
        val json = app.get("$mainUrl/book/searchByPageInShelf?$params").text
        return try {
            mapper.readValue<NovelHiResponse>(json).data?.list ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun NovelHiBook.toSearchResponse(): SearchResponse? {
        val n = bookName ?: return null
        val slug = simpleName ?: return null
        return newSearchResponse(name = n, url = "$mainUrl/s/$slug") {
            posterUrl = picUrl
            rating = rate?.toFloatOrNull()?.times(200)?.toInt()
            latestChapter = lastIndexName
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return fetchBooks(keyword = query).mapNotNull { it.toSearchResponse() }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val books = fetchBooks(
            page = page,
            genreId = tag ?: "",
            sort = orderBy ?: "",
        )
        return HeadMainPageResponse(
            url = "$mainUrl/book/book_ranking.html",
            list = books.mapNotNull { it.toSearchResponse() },
        )
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.removePrefix("$mainUrl/s/").substringBefore("/")

        // Fetch book metadata from the search API by matching simpleName
        val searchQuery = slug.replace("-", " ")
        val books = fetchBooks(keyword = searchQuery, limit = 10)
        val book = books.find { it.simpleName == slug }

        val name = book?.bookName
            ?: app.get(url).document.selectFirst("h1")?.text()
            ?: return null

        // Fetch chapter list from the dedicated index page
        val indexDoc = app.get("$mainUrl/s/index/$slug").document
        val chapters = indexDoc.select("#indexList a[href]").mapNotNull { a ->
            val href = a.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val chName = a.text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            newChapterData(name = chName, url = href)
        }

        return newStreamResponse(name = name, url = url, data = chapters) {
            if (book != null) {
                author = book.authorName
                posterUrl = book.picUrl
                synopsis = book.bookDesc
                rating = book.rate?.toFloatOrNull()?.times(200)?.toInt()
                tags = book.genres?.mapNotNull { it.genreName }
                setStatus(if (book.bookStatus == "1") "completed" else "ongoing")
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        return document.selectFirst("#chaptercontent")?.html()
            ?: document.selectFirst("#showReading")?.html()
    }
}
