package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.MadaraReader

class MeioNovelProvider : MadaraReader() {
    override val name = "MeioNovel"
    override val mainUrl = "https://meionovel.id"
}
