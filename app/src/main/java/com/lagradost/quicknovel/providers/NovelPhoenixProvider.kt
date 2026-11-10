package com.lagradost.quicknovel.providers
import com.lagradost.quicknovel.R
object NovelPhoenixProvider: NovelFireAPI() {
    override val name = "Novel Phoenix"
    override val mainUrl = "https://novelphoenix.com"
    override val iconId = R.drawable.icon_novelphoenix
    override val sectionUrl = "novel"
}