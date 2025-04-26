package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.R

/*
// https://boxnovel.com/
// https://morenovel.net/
// https://meionovel.id/
 */
class MeioNovelProvider : BoxNovelProvider() {
    override val name = "MeioNovel"
    override val mainUrl = "https://meionovels.com"
    override val iconId = R.drawable.ic_meionovel
    override val iconBackgroundId = R.color.colorPrimaryBlue
}
