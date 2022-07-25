package com.lagradost.quicknovel

import com.lagradost.quicknovel.mvvm.logError
import org.jsoup.Jsoup

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36"

abstract class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"

    open val lang = "en" // ISO_639_1 check SubtitleHelper

    open val rateLimitTime: Long = 0

    // DECLARE HAS ACCESS TO MAIN PAGE INFORMATION
    open val hasMainPage = false

    open val mainCategories: List<Pair<String, String>> = listOf()
    open val orderBys: List<Pair<String, String>> = listOf()
    open val tags: List<Pair<String, String>> = listOf()

    open val iconId: Int? = null
    open val iconBackgroundId: Int = R.color.darkBackground

    open suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        throw NotImplementedError()
    }

    open val hasReviews: Boolean = false
    open suspend fun loadReviews(url: String, page: Int, showSpoilers: Boolean = false): List<UserReview> {
        throw NotImplementedError()
    }

    open suspend fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    open suspend fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    open suspend fun loadHtml(url: String): String? {
        throw NotImplementedError()
    }
}

class ErrorLoadingException(message: String? = null) : Exception(message)
fun MainAPI.fixUrlNull(url: String?): String? {
    if (url.isNullOrEmpty()) {
        return null
    }
    return fixUrl(url)
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http")) {
        return url
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return mainUrl + url
        }
        return "$mainUrl/$url"
    }
}

//\.([A-z]) instead of \.([^-\s]) to preserve numbers like 17.4
val String?.textClean: String?
    get() = (this
        ?.replace(
            "\\.([A-z]|\\+)".toRegex(),
            "$1"
        ) //\.([^-\s]) BECAUSE + COMES AFTER YOU HAVE TO ADD \+ for stuff like shapes.h.i.+fted
        ?.replace("\\+([A-z])".toRegex(), "$1") //\+([^-\s])
            )

fun stripHtml(txt: String, chapterName: String? = null, chapterIndex: Int? = null): String {
    val document = Jsoup.parse(txt)
    try {
        if (chapterName != null && chapterIndex != null) {
            for (a in document.allElements) {
                if (a != null && a.hasText() &&
                    (a.text() == chapterName || (a.tagName() == "h3" && a.text().startsWith("Chapter ${chapterIndex + 1}")))
                ) { // IDK, SOME MIGHT PREFER THIS SETTING??
                    a.remove() // THIS REMOVES THE TITLE
                    break
                }
            }
        }
    } catch (e : Exception) {
        logError(e)
    }

    return document.html()
        .replace("<p>.*<strong>Translator:.*?Editor:.*>".toRegex(), "") // FUCK THIS, LEGIT IN EVERY CHAPTER
        .replace("<.*?Translator:.*?Editor:.*?>".toRegex(), "") // FUCK THIS, LEGIT IN EVERY CHAPTER

}

class HomePageList(
    val name: String,
    val list: List<SearchResponse>
)

data class HeadMainPageResponse(
    val url: String,
    val list: List<SearchResponse>,
)

data class UserReview(
    val review: String,
    val reviewTitle: String?,
    val username: String?,
    val reviewDate: String?,
    val avatarUrl: String?,
    val rating: Int?,
    val ratings: List<Pair<Int, String>>?,
)
/*
data class MainPageResponse(
    val name: String,
    val url: String,
    val posterUrl: String?,
    val rating: Int?,
    val latestChapter: String?,
    val apiName: String,
    val tags: ArrayList<String>,
)*/

data class SearchResponse(
    val name: String,
    val url: String,
    val posterUrl: String?,
    val rating: Int?,
    val latestChapter: String?,
    val apiName: String,
)

const val STATUS_NULL = 0
const val STATUS_ONGOING = 1
const val STATUS_COMPLETE = 2
const val STATUS_PAUSE = 3
const val STATUS_DROPPED = 4

data class LoadResponse(
    val url: String,
    val name: String,
    val data: List<ChapterData>,
    val author: String?,
    val posterUrl: String?,
    //RATING IS FROM 0-1000
    val rating: Int?,
    val peopleVoted: Int?,
    val views: Int?,
    val synopsis: String?,
    val tags: List<String>?,
    val status: Int?, // 0 = null - implemented but not found, 1 = Ongoing, 2 = Complete, 3 = Pause/HIATUS, 4 = Dropped
)

data class ChapterData(
    val name: String,
    val url: String,
    val dateOfRelease: String?,
    val views: Int?,
    val regerer: String? = null
    //val index : Int,
)