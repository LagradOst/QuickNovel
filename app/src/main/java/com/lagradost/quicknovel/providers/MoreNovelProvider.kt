package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*

class MoreNovelProvider : MadaraReader() {
    override val name = "MoreNovel"
    override val mainUrl = "https://morenovel.net"
    override val iconId = R.drawable.ic_morenovel
    override val mainCategories: List<Pair<String, String>>
        get() = listOf(
            Pair("All", ""),
            Pair("Novel Korea", "korean"),
            Pair("Novel China", "chinese"),
            Pair("Novel Jepang", "japanese"),
            Pair("Novel Philipina", "philippines")
        )
        
    override val tags: List<Pair<String, String>>
        get() = listOf(
            Pair("All", ""),
            Pair("Fantasy", "fantasy"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Xuanhuan", "xuanhuan"),
            Pair("Xianxia", "xianxia"),
            Pair("Wuxia", "wuxia"),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Harem", "harem"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Josei", "josei"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("School Life", "school-life"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        )
}
