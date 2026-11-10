package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.R

//It has content that novelfull.com  doesn’t have
object NovelFullNETProvider: AllNovelAPI() {
    override val name = "NovelFull-Net"
    override val mainUrl = "https://novelfull.net"
    override val hasMainPage = false
    override val iconId = R.drawable.icon_novelfull
    override val iconBackgroundId = R.color.white
    override val requireMobilUserAgent = true
}