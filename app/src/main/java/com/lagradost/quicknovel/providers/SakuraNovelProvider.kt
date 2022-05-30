package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import java.util.*

class SakuraNovelProvider : WPReader() {
    override val name = "SakuraNovel"
    override val mainUrl = "https://sakuranovel.id"
    override val iconId = R.drawable.ic_sakuranovel
    override val tags = listOf(
        Pair("All", ""),
        Pair("Action", "action"),
        Pair("Adult", "adult"),
        Pair("Adventure", "adventure"),
        Pair("China", "china"),
        Pair("Comedy", "comedy"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Jepang", "jepang"),
        Pair("Josei", "josei"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mystery", "mystery"),
        Pair("Original (Inggris)", "original-inggris"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School Life", "school-life"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Seinen Xuanhuan", "seinen-xuanhuan"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Smut", "smut"),
        Pair("Sports", "sports"),
        Pair("Supernatural", "supernatural"),
        Pair("Tragedy", "tragedy"),
        Pair("Xianxia", "xianxia"),
        Pair("Xuanhuan", "xuanhuan"),
    )

    override val orderBys: List<Pair<String, String>> = listOf(
        Pair("Latest Update", "update"),
        Pair("Most Views", "popular"),
        Pair("Rating", "rating"),
        Pair("A-Z", "title"),
        Pair("Latest Add", "latest"),
    )
}
