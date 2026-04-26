package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newSearchResponse


open class NovelBinProvider : AllNovelProvider() {
    override val name = "NovelBin"
    override val mainUrl = "https://novelbin.com"
    override val hasMainPage = true

    override val usesCloudFlareKiller = true

    override val iconId = R.drawable.icon_novelbin
    override val ajaxUrl = "ajax/chapter-archive"
    override val tags = listOf(
        "All" to "All",
        "Action" to "action",
        "Adventure" to "adventure",
        "Anime & comics" to "anime-&-comics",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Eastern" to "eastern",
        "Fanfiction" to "fanfiction",
        "Fantasy" to "fantasy",
        "Game" to "game",
        "Gender bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Litrpg" to "litrpg",
        "Magic" to "magic",
        "Magical realism" to "magical-realism",
        "Martial arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Modern life" to "modern-life",
        "Mystery" to "mystery",
        "Other" to "other",
        "Psychological" to "psychological",
        "Reincarnation" to "reincarnation",
        "Romance" to "romance",
        "School life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shoujo ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen ai" to "shounen-ai",
        "Slice of life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "System" to "system",
        "Tragedy" to "tragedy",
        "Urban life" to "urban-life",
        "Video games" to "video-games",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    )

    override val orderBys = listOf(
        "Genre" to "",
        "Latest Release" to "sort/latest",
        "Hot Novel" to "sort/top-hot-novel",
        "Completed Novel" to "sort/completed",
        "Most Popular" to "sort/top-view-novel",
        "Store" to "store",
    )

    override fun String.fullPosterFix(): String =
        this.replace(Regex("/novel_[0-9]*_[0-9]*/"), "/novel/")

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (orderBy == "" && tag != "All") "$mainUrl/genre/$tag?page=$page" else "$mainUrl/${if (orderBy.isNullOrBlank()) "sort/top-hot-novel" else orderBy}?page=$page"
        val document = app.get(url).document

        return HeadMainPageResponse(
            url,
            list = document.select("div.list>div.row").mapNotNull { element ->
                val a =
                    element.selectFirst("div > div > h3.novel-title > a") ?: return@mapNotNull null
                newSearchResponse(
                    name = a.text(),
                    url = fixUrlNull(a.attr("href")) ?: return@mapNotNull null,
                ){
                    posterUrl = fixUrlNull(element.selectFirst("div > div > img")?.attr("data-src")?.fullPosterFix())
                }
            })
    }
}