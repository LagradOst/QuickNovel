package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.R

class FanmtlProvider : WuxiaBoxProvider()
{
    override val name = "FanMTL"
    override val mainUrl = "https://www.fanmtl.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_fanmtl

}
