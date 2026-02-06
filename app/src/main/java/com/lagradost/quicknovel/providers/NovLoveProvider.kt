package com.lagradost.quicknovel.providers

class NovLoveProvider : NovelBinProvider()
{
    override val name = "NovLove"
    override val mainUrl = "https://novlove.com"
    override val hasMainPage = false
}