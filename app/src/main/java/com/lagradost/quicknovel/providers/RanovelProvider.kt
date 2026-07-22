package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import java.util.Locale

class RanovelProvider: MeioNovelProvider() {
    override val name = "Ranovel"
    override val mainUrl = "https://ranovel.com"
    override val lang = "en"
    override val iconId = R.drawable.icon_ranovel
    override val iconBackgroundId = R.color.black
}
