package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.R

object NovelFullProvider : AllNovelAPI() {
    override val name = "NovelFull"
    override val mainUrl = "https://novelfull.com"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_novelfull
    override val iconBackgroundId = R.color.white
    override val requireMobilUserAgent = true
}
