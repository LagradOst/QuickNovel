package com.lagradost.quicknovel

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

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
    val apiName: String,
)

data class LoadResponse(
    val name: String,
    val data: ArrayList<ChapterData>,
    val author: String?,
    val posterUrl: String?,
    //RATING IS FROM 0-1000
    val rating: Int?,
    val peopleVoted: Int?,
    val views: Int?,
    val Synopsis: String?,
    val tags: ArrayList<String>?,
    val status: Int?, // 0 = null - implemented but not found, 1 = Ongoing, 2 = Complete, 3 = Pause/HIATUS
)

data class ChapterData(
    val name: String,
    val url: String,
    val dateOfRelease: String?,
    val views: Int?,
    //val index : Int,
)

data class GithubAsset(
    val name: String,
    val size: Int, // Size bytes
    val browser_download_url: String, // download link
    val content_type: String // application/vnd.android.package-archive
)

data class GithubRelease(
    val tag_name: String, // Version code
    val body: String, // Desc
    val assets: List<GithubAsset>,
    val target_commitish: String // branch
)

data class Update(
    val shouldUpdate: Boolean,
    val updateURL: String?,
    val updateVersion: String?,
    val changelog: String?
)