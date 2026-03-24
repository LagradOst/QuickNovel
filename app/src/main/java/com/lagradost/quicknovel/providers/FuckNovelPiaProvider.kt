package com.lagradost.quicknovel.providers

import android.net.Uri
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus


class FuckNovelPiaProvider :  MainAPI() {
    override val name = "FuckNovelPia"
    override val mainUrl = "https://fucknovelpia.com"
    override val iconId = R.drawable.icon_fucknovelpia


    override val hasMainPage = true

    override val mainCategories = listOf(
        "All" to "",
        "Completed" to "completed",
        "Ongoing" to "ongoing",
        "Hiatus" to "hiatus",
        "Dropped" to "dropped"
    )

    override val orderBys = listOf(
        "Newest" to "newest",
        "Popular" to "popular",
        "Oldest" to "oldest",
        "Title A-Z" to "title-a-z",
        "Year desc" to "year-desc",
        "Year asc" to "year-asc"
    )

    override val tags = listOf(
        "All" to "",
        "academy" to "1",
        "action" to "2",
        "adventure" to "3",
        "fantasy" to "4",
        "horror" to "5",
        "mystery" to "6",
        "romance" to "7",
        "school" to "8",
        "martial" to "9",
        "smut" to "11",
        "adult" to "12",
        "harem" to "13",
        "historical" to "14",
        "scifi" to "15",
        "sliceoflife" to "16",
        "sports" to "17",
        "Uncategorized" to "18"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        //search.php?status=ongoing&read_only=any&sort=popular&tag_mode=and&genre_mode=and&genres_include%5B%5D=1
        val url = "$mainUrl/search.php?q=&author=&uploader=&translator_group=&country=&year_from=&year_to=&page=$page&status=$mainCategory&language=&read_only=any&sort=$orderBy&tag_mode=&genre_mode=${if(!tag.isNullOrBlank()) "and&genres_include%5B%5D=$tag" else ""}"
        println(url)
        val document = app.get(url).document

        val returnValue = document.select("div.grid > article.card-book").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("div.title")?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = card.selectFirst("div.cover img")?.attr("src")
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document//body > div.body > div > div > div.col-lg-8
        val infoDiv = document.select("div.container section.hero")

        // Extract title
        val title = infoDiv.selectFirst("h1")?.text() ?: ""

        // Extract description/synopsis
        val synopsis = document.selectFirst("p.hero-summary")?.text() ?: ""

        val chapters = document.select("ul.chapter-list li").mapNotNull { li ->
            val name = li.selectFirst("span.chapter-item-main")?.text()?:return@mapNotNull null
            val url = li.selectFirst("a")?.attr("href")?:return@mapNotNull null
            newChapterData(name, url)
        }

        return newStreamResponse(title,fixUrl(url), chapters) {
            this.posterUrl = infoDiv.selectFirst("div.cover")?.attr("style")
                ?.substringAfter("url(")
                ?.substringBefore(")")
            this.synopsis = synopsis

            document.select("ul.info-list li").forEach { span ->
                if(span.text().contains("Author")){
                    this.author = span.ownText() ?: ""
                }
                else if(span.text().contains("Status"))
                    setStatus(span.ownText())
            }

            this.tags = infoDiv.select("div.tags a").mapNotNull {
                it.text().trim().takeIf { text ->  !text.isEmpty() }
            }
        }
    }


    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val contentElement = document.select("div.reader > *").joinToString("</br>")
        return contentElement
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?q=${Uri.encode(query).replace("%20","+")}"
        val document = app.get(url).document

        return document.select("div.grid > div.card").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("div.info strong.book-title")?.text() ?: return@mapNotNull null


            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = card.selectFirst("div.cover img")?.attr("src")
            }

        }
    }
}