package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.newChapterData
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.collections.map

class FenrirRealProvider:  MainAPI() {
    override val name = "FenrirRealm"
    override val mainUrl = "https://fenrirealm.com"
    override val iconId = R.drawable.icon_fenrirealm
    override val hasMainPage = true

    override val mainCategories = listOf(
        "All" to "any",
        "Completed" to "completed",
        "Ongoing" to "on-going"
    )
    override val orderBys =
        listOf(
            "Popular" to "popular",
            "Latest" to "latest",
            "Updated" to "updated"
        )
    override val tags = listOf(
        "All" to "0",
        "Action" to "1",
        "Adult" to "2",
        "Adventure" to "3",
        "Comedy" to "4",
        "Drama" to "5",
        "Ecchi" to "6",
        "Fantasy" to "7",
        "Gender Bender" to "8",
        "Harem" to "9",
        "Historical" to "10",
        "Horror" to "11",
        "Josei" to "12",
        "Martial Arts" to "13",
        "Mature" to "14",
        "Mecha" to "15",
        "Mystery" to "16",
        "Psychological" to "17",
        "Romance" to "18",
        "School Life" to "19",
        "Sci-fi" to "20",
        "Seinen" to "21",
        "Shoujo" to "22",
        "Shoujo Ai" to "23",
        "Shounen" to "24",
        "Shounen Ai" to "25",
        "Slice of Life" to "26",
        "Smut" to "27",
        "Sports" to "28",
        "Supernatural" to "29",
        "Tragedy" to "30",
        "Wuxia" to "31",
        "Xianxia" to "32",
        "Xuanhuan" to "33",
        "Yaoi" to "34",
        "Yuri" to "35",
    )

    fun String.getSlugFromUrl() = this.replace("$mainUrl/series/", "")

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = if(tag != "0") "$mainUrl/api/new/v2/series?page=$page&per_page=12&status=$mainCategory&sort=$orderBy&tags%5B%5D=$tag"
        else "$mainUrl/api/new/v2/series?page=$page&per_page=24&status=$mainCategory&sort=$orderBy"
        val document = app.get(url).parsed<FenrirMainPageResponse>()
        val returnValue = document.data.map { novel ->
            newSearchResponse(
                name = novel.title,
                url = mainUrl + "/series/" + novel.slug
            ) {
                posterUrl = fixUrlNull(novel.cover)
            }

        }
        return HeadMainPageResponse(url, returnValue)
    }



    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document
        val infoDiv = document.select("div.flex.flex-col.items-center.gap-5 div.flex-1")
        val chapters = app
            .get("$mainUrl/api/new/v2/series/${url.getSlugFromUrl()}/chapters")
            .parsed<Array<ChapterInf>>()
            .mapNotNull { ch ->
                if(ch.locked.price > 0) null
                else newChapterData("${ch.name} - ${ch.title}", "$url/${ch.slug}"){
                    dateOfRelease = ch.updatedAt.split("T")[0]
                }
            }

        val title = infoDiv.selectFirst("h1")?.text() ?: ""
        return newStreamResponse(title,url, chapters) {
            infoDiv.select(" > div").forEachIndexed { index, inf ->
                when (index) {
                    1 -> {
                        setStatus(inf.selectFirst("span")?.text())
                    }
                    2 -> {
                        this.author = inf.selectFirst("a")?.text()
                    }
                    3 -> {
                        this.tags = infoDiv.select("a").mapNotNull {
                            it.text().trim().takeIf { text -> !text.isEmpty() }
                        }
                    }
                }
            }
            this.synopsis = document.selectFirst("div.synopsis")?.text()
            this.posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content")
        }
    }



    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val contentElement = document.selectFirst("div.main-area div.chapter-view div.content-area") ?: return null
        return contentElement.html()
    }




    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/new/v2/series?page=1&per_page=5&search=$query"
        val document = app.get(url).parsed<FenrirMainPageResponse>()

        return document.data.map{ novel ->
            val title = novel.title
            val novelUrl = mainUrl + "/series/" + novel.slug
            newSearchResponse(title, novelUrl){
                posterUrl = fixUrlNull(novel.cover)
            }
        }
    }



    @JsonIgnoreProperties(ignoreUnknown = true)
    data class FenrirMainPageResponse(
        val data: List<Daum>,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Daum(//Novel inf
        val title: String,
        val slug: String,
        val cover: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ChapterInf(
        val slug: String,
        val name: String,
        val title: String?,
        @JsonProperty("updated_at")
        val updatedAt: String,
        val locked: Locked,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Locked(
        val price: Int,
    )

}

