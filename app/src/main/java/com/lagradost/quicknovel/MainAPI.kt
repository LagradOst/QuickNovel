package com.lagradost.quicknovel

import org.jsoup.Jsoup

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36"

abstract class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"

    open val rateLimitTime : Long = 0

    // DECLARE HAS ACCESS TO MAIN PAGE INFORMATION
    open val hasMainPage = false

    open val mainCategories: ArrayList<Pair<String, String>> = ArrayList()
    open val orderBys: ArrayList<Pair<String, String>> = ArrayList()
    open val tags: ArrayList<Pair<String, String>> = ArrayList()

    open val iconId: Int? = null
    open val iconBackgroundId: Int = R.color.darkBackground

    open fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        throw NotImplementedError()
    }

    open val hasReviews : Boolean = false
    open fun loadReviews(url : String, page: Int, showSpoilers : Boolean = false): ArrayList<UserReview> {
        throw NotImplementedError()
    }

    open fun search(query: String): ArrayList<SearchResponse> {
        throw NotImplementedError()
    }

    open fun load(url: String): LoadResponse {
        throw NotImplementedError()
    }

    open fun loadHtml(url: String): String? {
        return null
    }
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
        ?.replace("\\.([A-z]|\\+)".toRegex(), "$1") //\.([^-\s]) BECAUSE + COMES AFTER YOU HAVE TO ADD \+ for stuff like shapes.h.i.+fted
        ?.replace("\\+([A-z])".toRegex(), "$1") //\+([^-\s])
            )

fun stripHtml(txt: String, chapterName: String? = null, chapterIndex: Int? = null): String {
    val document = Jsoup.parse(txt)
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

    return document.html()
        .replace("<p>.*<strong>Translator:.*?Editor:.*>".toRegex(), "") // FUCK THIS, LEGIT IN EVERY CHAPTER
        .replace("<.*?Translator:.*?Editor:.*?>".toRegex(), "") // FUCK THIS, LEGIT IN EVERY CHAPTER

}

data class HeadMainPageResponse(
    val url: String,
    val list: ArrayList<SearchResponse>,
)

data class UserReview(
    val review : String,
    val reviewTitle : String?,
    val username: String?,
    val reviewDate: String?,
    val avatarUrl: String?,
    val rating: Int?,
    val ratings: ArrayList<Pair<Int, String>>?,
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

data class LoadResponse(
    val source: String,
    val name: String,
    val data: ArrayList<ChapterData>,
    val author: String?,
    val posterUrl: String?,
    //RATING IS FROM 0-1000
    val rating: Int?,
    val peopleVoted: Int?,
    val views: Int?,
    val synopsis: String?,
    val tags: ArrayList<String>?,
    val status: Int?, // 0 = null - implemented but not found, 1 = Ongoing, 2 = Complete, 3 = Pause/HIATUS, 4 = Dropped
)

data class  ChapterData(
    val name: String,
    val url: String,
    val dateOfRelease: String?,
    val views: Int?,
    //val index : Int,
)