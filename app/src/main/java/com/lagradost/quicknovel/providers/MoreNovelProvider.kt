package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*

class MoreNovelProvider : BoxNovelProvider() {
    override val name = "MoreNovel"
    override val mainUrl = "https://risenovel.com" // domain change
    override val iconId = R.drawable.ic_morenovel
    override val mainCategories = listOf(
        "All" to "",
        "Novel Korea" to "korean",
        "Novel China" to "chinese",
        "Novel Jepang" to "japanese",
        "Novel Philipina" to "philippines"
    )

    override val tags = listOf(
        "All" to "",
        "Fantasy" to "fantasy",
        "Martial Arts" to "martial-arts",
        "Xuanhuan" to "xuanhuan",
        "Xianxia" to "xianxia",
        "Wuxia" to "wuxia",
        "Action" to "action",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Harem" to "harem",
        "Gender Bender" to "gender-bender",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "School Life" to "school-life",
        "Shoujo" to "shoujo",
        "Shoujo Ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    )
}
