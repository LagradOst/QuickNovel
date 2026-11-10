package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.R

object RanovelProvider: MeioNovelAPI() {
    override val name = "Ranovel"
    override val mainUrl = "https://ranovel.com"
    override val lang = "en"
    override val iconId = R.drawable.icon_ranovel
    override val iconBackgroundId = R.color.black
}
