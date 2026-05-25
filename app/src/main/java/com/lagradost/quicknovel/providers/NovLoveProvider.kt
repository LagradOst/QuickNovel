package com.lagradost.quicknovel.providers
class NovLoveProvider  : MeioNovelProvider()
{
    override val name = "NovLove"
    override val mainUrl = "https://novelnice.com"
    override val hasMainPage = false
    override val usesCloudFlareKiller = true
    override val lang = "en"
}