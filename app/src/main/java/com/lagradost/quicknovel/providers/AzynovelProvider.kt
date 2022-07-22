package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup

class AzynovelProvider : MainAPI() {
    override val name = "AzyNovel"
    override val mainUrl = "https://azynovel.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_azynovel

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", ""),
        Pair("Romance", "romance"),
        Pair("Thriller", "thriller"),
        Pair("Young Adult", "young-adult"),
        Pair("Historical", "historical"),
        Pair("Science Fiction", "science-fiction"),
        Pair("Christian", "christian"),
        Pair("Action", "action"),
        //Pair("Editor's choice", "Editor's choice"),
        Pair("Bender", "bender"),
        Pair("Ai", "ai"),
        Pair("Drama", "drama"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("History", "history"),
        Pair("Lolicon", "lolicon"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mecha", "mecha"),
        Pair("Psychological", "psychological"),
        Pair("School Life", "school-life"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen Ai", "shounen-ai"),
        Pair("Adventure", "adventure"),
        Pair("Fantasy", "fantasy"),
        Pair("Mystery", "mystery"),
        Pair("Horror", "horror"),
        Pair("Humorous", "humorous"),
        Pair("Western", "western"),
        Pair("Adult", "adult"),
        Pair("Arts", "arts"),
        Pair("Comedy", "comedy"),
        Pair("Ecchi", "ecchi"),
        Pair("Gender", "gender"),
        Pair("Harem", "harem"),
        Pair("Josei", "josei"),
        Pair("Martial", "martial"),
        Pair("Mature", "mature"),
        Pair("Modern Life", "modern-life"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice Of Life", "slice-of-life"),
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/latest-releases?page=$page" else "$mainUrl/category/$tag?page=$page"

        val response = app.get(url)

        val document = Jsoup.parse(response.text)

        document.selectFirst("#game-upcoming")?.remove()
        document.selectFirst("#game-release")?.remove()

        val headers = document.select("a.box.is-shadowless")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val name = h?.selectFirst("span")?.text() ?: continue
            val cUrl = mainUrl + h.attr("href")

            val posterUrl = h.selectFirst("div.media-left > figure > img")?.attr("data-src")

            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrlNull(posterUrl),
                    null,
                    null,
                    this.name
                )
            )
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div.column.is-9 > div:nth-child(5)")?.html()
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val response =
            app.get("$mainUrl/search?q=$query") // AJAX, MIGHT ADD QUICK SEARCH

        val document = Jsoup.parse(response.text)

        val headers = document.select("a.box.is-shadowless")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val name = h?.selectFirst("span")?.text() ?: continue
            val cUrl = mainUrl + h.attr("href")

            val posterUrl = h.selectFirst("div.media-left > figure > img")?.attr("data-src")
            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrlNull(posterUrl),
                    null,
                    null,
                    this.name
                )
            )
        }
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("div.media-content > div > h1")?.text() ?: return null

        val author = document.selectFirst("div.media-content > div > p:nth-child(2) > a")?.text()

        val posterUrl = document.select("div.media-left > figure > img").attr("data-src")

        val tags = document.select("div.media-content > div > p:nth-child(4) > a").map {
            it.text()
        }.plus(document.select("div.media-content > div > p:nth-child(3) > a").map { it.text() })

        val synopsis = document.selectFirst("div.column.is-9 > div.content")?.text()

        val data: ArrayList<ChapterData> = ArrayList()
        val chapters = document.select("a.button.is-light.is-fullwidth")
        for (c in chapters) {
            val href = c?.attr("href") ?: continue
            if (href.contains("category").not()) {
                val cUrl = fixUrl(href)
                val cName = c.attr("title")
                data.add(ChapterData(cName, cUrl, null, null))
            }
        }

        var rating = 0
        var peopleVoted = 0
        try {
            rating =
                (document.selectFirst("div.is-hidden-desktop.is-hidden-tablet.has-text-centered > p")!!
                    .text().substringBefore("/").toFloat() * 200).toInt()

            peopleVoted =
                document.selectFirst("div.is-hidden-desktop.is-hidden-tablet.has-text-centered > p")!!
                    .text().substringAfter("/").filter { it.isDigit() }.toInt()
        } catch (e: Exception) {
            // NO RATING
        }

        return LoadResponse(
            url,
            name,
            data,
            author,
            fixUrlNull(posterUrl),
            rating,
            peopleVoted,
            null,
            synopsis,
            tags,
            null,
        )
    }
}