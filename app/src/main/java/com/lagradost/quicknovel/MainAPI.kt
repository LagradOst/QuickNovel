package com.lagradost.quicknovel

import org.jsoup.Jsoup

open class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"

    // DECLARE HAS ACCESS TO MAIN PAGE INFORMATION
    open val hasMainPage = false

    open val mainCategories: ArrayList<Pair<String, String>> = ArrayList()
    open val orderBys: ArrayList<Pair<String, String>> = ArrayList()
    open val tags: ArrayList<Pair<String, String>> = ArrayList()

    open val iconId: Int? = null

    open fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): ArrayList<MainPageResponse>? {
        return null
    }

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

data class MainPageResponse(
    val name: String,
    val url: String,
    val posterUrl: String?,
    val rating: Int?,
    val latestChapter: String?,
    val apiName: String,
    val tags: ArrayList<String>,
)

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
    val synopsis: String?,
    val tags: ArrayList<String>?,
    val status: Int?, // 0 = null - implemented but not found, 1 = Ongoing, 2 = Complete, 3 = Pause/HIATUS, 4 = Dropped
)

data class ChapterData(
    val name: String,
    val url: String,
    val dateOfRelease: String?,
    val views: Int?,
    //val index : Int,
)