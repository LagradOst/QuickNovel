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
        Pair("Comedy", "comedy"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Mystery", "mystery"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School Life", "school-life"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Supernatural", "supernatural"),
        Pair("Tragedy", "tragedy"),
    )
}
