package com.lagradost.quicknovel.providers

import android.net.Uri
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import kotlin.math.roundToInt

class VynovelProvider :  MainAPI() {
    override val name = "VyNovel"
    override val mainUrl = "https://vynovel.com"
    override val iconId = R.drawable.icon_vynovel
    override val iconBackgroundId = R.color.vynovelColor


    override val hasMainPage = true

    override val mainCategories = listOf(
        "All" to "2",
        "Completed" to "1",
        "Ongoing" to "0"
    )

    override val orderBys = listOf(
        "Viewed" to "viewed",
        "Scored" to "scored",
        "Newest" to "created_at",
        "Latest Update" to "updated_at"
    )

    override val tags = listOf(
        "All" to "",
        "Action" to "Action-1-action",
        "Adult" to "Adult-6-adult",
        "Adventure" to "Adventure-10-adventure",
        "Billionaire" to "Billionaire-62-billionaire",
        "Chinese" to "Chinese-27-chinese",
        "Comedy" to "Comedy-2-comedy",
        "Contemporary Romance" to "Contemporary Romance-65-contemporary_romance",
        "Drama" to "Drama-3-drama",
        "Ecchi" to "Ecchi-38-ecchi",
        "Erciyuan" to "Erciyuan-59-erciyuan",
        "Faloo" to "Faloo-31-faloo",
        "fan fiction" to "fan fiction-66-fan_fiction",
        "Fan-Fiction" to "Fan-Fiction-8-fanfiction",
        "Fanfiction" to "Fanfiction-68-fanfiction",
        "Fantasy" to "Fantasy-4-fantasy",
        "Game" to "Game-16-game",
        "Games" to "Games-67-games",
        "Gender Bender" to "Gender Bender-43-gender_bender",
        "Harem" to "Harem-11-harem",
        "Historical" to "Historical-23-historical",
        "Horror" to "Horror-29-horror",
        "Isekai" to "Isekai-48-isekai",
        "Japanese" to "Japanese-42-japanese",
        "Josei" to "Josei-24-josei",
        "Korean" to "Korean-37-korean",
        "LitRPG" to "LitRPG-56-litrpg",
        "Magic" to "Magic-54-magic",
        "Magical Realism" to "Magical Realism-47-magical_realism",
        "Martial Arts" to "Martial Arts-12-martial_arts",
        "Martialarts" to "Martialarts-53-martialarts",
        "Mature" to "Mature-32-mature",
        "Mecha" to "Mecha-44-mecha",
        "Military" to "Military-45-military",
        "Modern Life" to "Modern Life-64-modern_life",
        "Modern&" to "Modern&-57-modern",
        "ModernRomance" to "ModernRomance-50-modernromance",
        "Mystery" to "Mystery-18-mystery",
        "NA" to "NA-61-na",
        "Psychological" to "Psychological-34-psychological",
        "Romance" to "Romance-5-romance",
        "Romantic" to "Romantic-49-romantic",
        "School Life" to "School Life-13-school_life",
        "Sci-fi" to "Sci-fi-9-scifi",
        "Seinen" to "Seinen-33-seinen",
        "Shoujo" to "Shoujo-25-shoujo",
        "Shoujo Ai" to "Shoujo Ai-40-shoujo_ai",
        "Shounen" to "Shounen-22-shounen",
        "Shounen Ai" to "Shounen Ai-14-shounen_ai",
        "Slice of Life" to "Slice of Life-15-slice_of_life",
        "Smut" to "Smut-39-smut",
        "Son-In-Law" to "Son-In-Law-63-soninlaw",
        "Sports" to "Sports-21-sports",
        "StrongLead" to "StrongLead-55-stronglead",
        "Supernatural" to "Supernatural-19-supernatural",
        "Thriller" to "Thriller-30-thriller",
        "Tragedy" to "Tragedy-35-tragedy",
        "Two-dimensional" to "Two-dimensional-58-twodimensional",
        "Uncategorized" to "Uncategorized-69-uncategorized",
        "Urban" to "Urban-52-urban",
        "Urban Life" to "Urban Life-26-urban_life",
        "Video Games" to "Video Games-51-video_games",
        "Virtual Reality" to "Virtual Reality-60-virtual_reality",
        "VirtualReality" to "VirtualReality-46-virtualreality",
        "Wuxia" to "Wuxia-41-wuxia",
        "Xianxia" to "Xianxia-20-xianxia",
        "Xuanhuan" to "Xuanhuan-17-xuanhuan",
        "Yaoi" to "Yaoi-28-yaoi",
        "Yuri" to "Yuri-36-yuri"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        //search?search_po=0&author_po=0&completed=1&sort=scored&sort_type=asc&genre%5B0%5D=Action-1-action&page=2
        val url = "$mainUrl/search?page=$page&completed=$mainCategory&sort=$orderBy${if(!tag.isNullOrBlank())"&genre%5B%5D=$tag" else ""}"
        val document = app.get(url).document

        val returnValue = document.select("div.novel-list > div").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("div.comic-title")?.text() ?: return@mapNotNull null


            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = card.selectFirst("div.comic-image")?.attr("data-background-image")
            }

        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document//body > div.body > div > div > div.col-lg-8
        val infoDiv = document.select("div.container div.row div.div-manga")

        // Extract title
        val title = infoDiv.selectFirst("h1")?.text() ?: ""

        // Extract description/synopsis
        val synopsis = document.selectFirst("p.content")?.text() ?: ""

        val chapters = document.select("div.list div.list-group a").reversed().mapNotNull { li ->
            val name = li.selectFirst("span")?.text()?:return@mapNotNull null
            val url = li.attr("href")?:return@mapNotNull null
            newChapterData(name, url){
                this.dateOfRelease = li.selectFirst("p")?.text()
            }
        }

        return newStreamResponse(title,fixUrl(url), chapters) {
            this.posterUrl = infoDiv.selectFirst("div.img-manga img")?.attr("src")
            this.synopsis = synopsis

            infoDiv.select("div.div-manga > div.row > div.col-md-7 > p").forEachIndexed { index, span ->
                if(span.text().contains("Authors")){
                    this.author = infoDiv.selectFirst("a")?.text() ?: ""
                }
                else if(span.text().contains("Status"))
                    setStatus(infoDiv.selectFirst("span.text-ongoing")?.text())
                else if(span.text().contains("View")){
                    this.views = span.ownText()?.trim()?.replace(",","")?.toIntOrNull()
                }
                else if(span.text().contains("Rating")){
                    this.peopleVoted = span.ownText()
                        .substringAfter("(")
                        .substringBefore(" votes)")
                        .toIntOrNull() ?: 0

                    this.rating = span.ownText()
                        .substringBefore("/")
                        .toFloatOrNull()?.times(20)?.times(10)?.roundToInt()
                }
            }

            this.tags = infoDiv.select("a.badge").mapNotNull {
                it.text().trim().takeIf { text ->  !text.isEmpty() }
            }
        }
    }


    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val contentElement = document.select("div.body div.content > p").joinToString("</br>")
        return contentElement
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?search_po=0&q=${Uri.encode(query).replace("%20","+")}"
        val document = app.get(url).document

        return document.select("div.novel-list > div").mapNotNull { card ->
            val href = card.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = card.selectFirst("div.comic-title")?.text() ?: return@mapNotNull null


            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = card.selectFirst("div.comic-image")?.attr("data-background-image")
            }

        }
    }
}