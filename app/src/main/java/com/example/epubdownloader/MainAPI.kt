package com.example.epubdownloader

open class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"
    open fun search(query: String): ArrayList<SearchResponse>? {
        return null
    }

    open fun load(url: String): LoadResponse? {
        return null
    }

    open fun loadPage(url: String): String? {
        return null
    }
}

data class SearchResponse(
    val name: String,
    val url: String,
    val posterUrl: String?,
    val rating: Int?,
    val latestChapter: String?,
)

data class LoadResponse(
    val name: String,
    val data: ArrayList<ChapterData>,
    val author: String?,
    val posterUrl: String?,
    //RATING IS FROM 0-100
    val rating: Int?,
    val peopleVoted: Int?,
    val views: Int?,
    val Synopsis: String?,
    val tags: ArrayList<String>?,
)

data class ChapterData(
    val name: String,
    val url: String,
    val dateOfRelease: String?,
    val views: Int?,
    //val index : Int,
)