package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.newChapterData

class NovelPhoenixProvider: NovelFireProvider() {
    override val name = "Novel Phoenix"
    override val mainUrl = "https://novelphoenix.com"
    override val iconId = R.drawable.icon_novelphoenix

    override suspend fun getChapters(url: String): List<ChapterData> {
        val bookId = url.substringAfterLast("/novel/").substringBefore("?").substringBefore("/")
        val firstPageUrl = "$mainUrl/novel/$bookId/chapters?page=1"
        val document = app.get(firstPageUrl).document

        val pagination = document.selectFirst("div.pagenav div.pagination-container nav ul.pagination")
        if (pagination != null) {
            val lastPageElement = pagination.select("li").let { it.getOrNull(it.size - 2) }
            val lastPageNumber = lastPageElement?.text()?.toIntOrNull() ?: 1

            val lastPageUrl = "$mainUrl/novel/$bookId/chapters?page=$lastPageNumber"
            val lastPageDoc = app.get(lastPageUrl).document
            val lastChapterLink = lastPageDoc.select("ul.chapter-list li a").last()?.attr("href") ?: ""
            val totalChapters = lastChapterLink.substringAfterLast("/chapter-").toIntOrNull()

            if (totalChapters != null) {
                return (1..totalChapters).map { chapterNumber ->
                    val chapterUrl = "$mainUrl/novel/$bookId/chapter-$chapterNumber"
                    newChapterData("Chapter $chapterNumber", chapterUrl)
                }
            }
        }

        return document.select("ul.chapter-list li").mapNotNull { li ->
            val a = li.selectFirst("a") ?: return@mapNotNull null
            val name = a.selectFirst("span.chapter-title")?.text() ?: a.text()
            val url = a.attr("href")
            val date = li.selectFirst("span.chapter-update")?.text()

            newChapterData(name, url) {
                this.dateOfRelease = date
            }
        }
    }
}