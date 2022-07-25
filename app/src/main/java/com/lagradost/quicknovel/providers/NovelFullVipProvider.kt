package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.R

class NovelFullVipProvider : MNovelFreeProvider() {
    override val name = "NovelFullVip"
    override val mainUrl = "https://novelfullvip.com"
    override val hasMainPage = true
    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor
    override val iconId = R.drawable.icon_mnovel
}