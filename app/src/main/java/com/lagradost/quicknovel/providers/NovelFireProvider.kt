package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.ChapterData
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

class NovelFireProvider:  MainAPI() {
    override val name = "NovelFire"
    override val mainUrl = "https://novelfire.net"
    override val iconId = R.drawable.icon_novelfire

    override val hasMainPage = true

    override val mainCategories = listOf(
        "All" to "status-all",
        "Completed" to "status-completed",
        "Ongoing" to "status-ongoing"
    )
    override val orderBys =
        listOf("Popular" to "sort-popular", "New" to "sort-new", "Updates" to "sort-latest-release")

    override val tags = listOf(
        "All" to "all",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Anime" to "anime",
        "Arts" to "arts",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Eastern" to "eastern",
        "Ecchi" to "ecchi",
        "Fan-fiction" to "fan-fiction",
        "Fantasy" to "fantasy",
        "Game" to "game",
        "Gender-bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Lgbt" to "lgbt",
        "Magic" to "magic",
        "Magical-realism" to "magical-realism",
        "Manhua" to "manhua",
        "Martial-arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Military" to "military",
        "Modern-life" to "modern-life",
        "Movies" to "movies",
        "Mystery" to "mystery",
        "Other" to "other",
        "Psychological" to "psychological",
        "Realistic-fiction" to "realistic-fiction",
        "Reincarnation" to "reincarnation",
        "Romance" to "romance",
        "School-life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shoujo-ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen-ai" to "shounen-ai",
        "Slice-of-life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "System" to "system",
        "Tragedy" to "tragedy",
        "Urban" to "urban",
        "Urban-life" to "urban-life",
        "Video-games" to "video-games",
        "War" to "war",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/genre-${tag}/${orderBy}/${mainCategory}/all-novel?page=$page"
        val document = app.get(url).document


        val returnValue = document.select("li.novel-item").mapNotNull { select ->
            val node = select.selectFirst("a[title]") ?: return@mapNotNull null
            val href = node.attr("href")
            val title =
                node.attr("title").takeIf { !it.isEmpty() } ?: node.selectFirst("h4.novel-title")
                    ?.text() ?: return@mapNotNull null

            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(
                    select.selectFirst("img")?.attr("data-src")
                        ?: select.selectFirst("img")?.attr("src")
                )
            }

        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document
        val infoDiv = document.select("div.novel-info")


        // Extract title
        val title = infoDiv.selectFirst("h1.novel-title")?.text() ?: ""

        // Extract author
        val author = infoDiv.selectFirst("div.author > a")?.text() ?: ""

        // Extract description/synopsis
        val synopsis = document.selectFirst("meta[itemprop=description]")?.attr("content") ?: ""

        val chapters = getChapters(url)

        return newStreamResponse(title,fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = fixUrlNull(document.selectFirst("figure.cover img")?.attr("src"))
            this.synopsis = synopsis
            this.tags = infoDiv.select("div.categories ul li").mapNotNull {
                it.text().trim().takeIf { text ->  !text.isEmpty() }
            }

            infoDiv.select("div.header-stats span").map{span ->
                if(span.text().contains("Status")){
                    setStatus(span.selectFirst("strong")?.text())
                }
                else if(span.text().contains("Views")){
                    this.views = span.selectFirst("strong")?.ownText()?.trim()?.let {
                                if(it.contains("k",true)) it.replace("k","", true).toFloatOrNull()?.times(1000)?.roundToInt()
                                else if(it.contains("m",true)) it.replace("m","", true).toFloatOrNull()?.times(1000000)?.roundToInt()
                                else it.toIntOrNull()
                            }
                }
            }

            this.peopleVoted =
                document.selectFirst("div.novel-body.container a.grdbtn.reviews-latest-container p.latest.text1row")
                    ?.text()
                    ?.replace(Regex("[^0-9]"), "")
                    ?.trim()
                    ?.toIntOrNull() ?: 0
            this.rating = document.selectFirst("div.rating strong.nub")?.text()
                ?.toFloatOrNull()?.times(20)?.times(10)?.roundToInt()

        }
    }

    suspend fun getChapters(url: String): List<ChapterData> {
        val bookId = url.substringAfterLast("/book/").substringBefore("?").substringBefore("/")
        val firstPageUrl = "$mainUrl/book/$bookId/chapters?page=1"
        val document = app.get(firstPageUrl).document

        val pagination = document.selectFirst("div.pagenav div.pagination-container nav ul.pagination")
        if (pagination != null) {
            val lastPageElement = pagination.select("li").let { it.getOrNull(it.size - 2) }
            val lastPageNumber = lastPageElement?.text()?.toIntOrNull() ?: 1

            val lastPageUrl = "$mainUrl/book/$bookId/chapters?page=$lastPageNumber"
            val lastPageDoc = app.get(lastPageUrl).document
            val lastChapterLink = lastPageDoc.select("ul.chapter-list li a").last()?.attr("href") ?: ""
            val totalChapters = lastChapterLink.substringAfterLast("/chapter-").toIntOrNull()

            if (totalChapters != null) {
                return (1..totalChapters).map { chapterNumber ->
                    val chapterUrl = "$mainUrl/book/$bookId/chapter-$chapterNumber"
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

    override suspend fun loadHtml(url: String): String? {
        val fullUrl = fixUrl(url)
        val document = app.get(fullUrl).document
        val contentElement = document.selectFirst("div#content")
        contentElement?.select("img[src*=disable-blocker.jpg]")?.forEach { it.remove() }
        return contentElement?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/?keyword=$query&page=1"
        val document = app.get(url).document

        return document.select("ul.novel-list.horizontal.col2.chapters li.novel-item").mapNotNull { element ->
            val a = element.selectFirst("a")?:return@mapNotNull null
            val title = a.attr("title").trim()
            val novelUrl = a.attr("href")
            val coverUrl = fixUrlNull(a.selectFirst("img")?.attr("src"))
            newSearchResponse(title, novelUrl){
                posterUrl = coverUrl
            }
        }
    }
}