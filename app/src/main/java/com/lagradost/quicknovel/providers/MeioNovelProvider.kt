package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.nodes.Document
import java.util.Locale

open class MeioNovelProvider : MainAPI() {
    override val name = "MeioNovel"
    override val mainUrl = "https://meionovels.com"
    override val lang = "id"
    override val iconId = R.drawable.icon_meionovel
    override val iconBackgroundId = R.color.colorPrimaryBlue
    override val hasMainPage = true
    override val usesCloudFlareKiller = true

    override val tags = listOf(
        "All" to "",
        "Fantasy" to "fantasy",
        "Martial Arts" to "martial-arts",
        "Xuanhuan" to "xuanhuan",
        "Xianxia" to "xianxia",
        "Wuxia" to "wuxia",
        "Action" to "action",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Harem" to "harem",
        "Gender Bender" to "gender-bender",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "School Life" to "school-life",
        "Shoujo" to "shoujo",
        "Shoujo Ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    )

    override val orderBys = listOf(
        "Nothing" to "",
        "New" to "new-manga",
        "Most Views" to "views",
        "Trending" to "trending",
        "Rating" to "rating",
        "A-Z" to "alphabet",
        "Latest" to "latest",
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val order = when (tag) {
            "", null -> "novel"
            "completed" -> "manga-tag/$tag"
            else -> "novel-genre/$tag"
        }
        val url = "$mainUrl/$order/page/$page/${if (orderBy.isNullOrBlank()) "" else "?m_orderby=$orderBy"}"
        val document = app.get(url).document
        println(document)
        val returnValue = document.select("div.page-item-detail").mapNotNull { h ->
            val imageHeader = h.selectFirst("div.item-thumb > a") ?: return@mapNotNull null
            val name = imageHeader.attr("title")
            if (name.contains("Comic", ignoreCase = true)) return@mapNotNull null

            newSearchResponse(name, imageHeader.attr("href") ?: return@mapNotNull null) {
                val sum = h.selectFirst("div.item-summary")
                latestChapter = sum?.selectFirst("> div.list-chapter > div.chapter-item > span > a")?.text()
                rating = sum?.selectFirst("> div.rating > div.post-total-rating > span.score")?.text()
                    ?.toFloatOrNull()?.times(200)?.toInt()
                posterUrl = imageHeader.selectFirst("> img")?.let {
                    it.attr("data-src").takeIf { src -> src.isNotBlank() } ?: it.attr("src")
                }
            }
        }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val name = document.selectFirst("div.post-title > h1")?.text()?.clean() ?: return null

        return newStreamResponse(url, name, getChapters(url)) {
            tags = document.select("div.genres-content > a").map { it.text() }
            author = document.selectFirst("div.author-content > a")?.text()

            synopsis = document.select("#editdescription > p, div.j_synopsis > p, div.summary__content > p")
                .filter { it.hasText() && !it.text().lowercase().contains(mainUrl) }
                .joinToString("\n\n") { it.text() }

            setStatus(document.select("div.post-status > div.post-content_item > div.summary-content").last()?.text())

            posterUrl = fixUrlNull(document.selectFirst("div.summary_image > a > img")?.let {
                it.attr("data-src").takeIf { src -> src.isNotBlank() } ?: it.attr("src")
            })

            rating = (document.selectFirst("span#averagerate")?.text()?.toFloatOrNull()?.times(200))?.toInt()
            peopleVoted = document.selectFirst("span#countrate")?.text()?.let { text ->
                text.replace("K", if (text.contains(".")) "00" else "000")
                    .replace(".", "").toIntOrNull()
            } ?: 0

            related = getRelated(document)
        }
    }

    suspend fun getChapters(url: String): List<ChapterData> {
        val document = app.post("${url.removeSuffix("/")}/ajax/chapters/").document
        return document.select("ul.version-chap li.wp-manga-chapter").mapNotNull { c ->
            val header = c.selectFirst("> a") ?: return@mapNotNull null
            ChapterData(
                name = header.text().clean(),
                url = header.attr("href") ?: return@mapNotNull null,
                dateOfRelease = c.selectFirst("> span.chapter-release-date > i")?.text(),
                views = 0
            )
        }.reversed()
    }

    fun getRelated(dc: Document): List<SearchResponse> {
        return dc.select("div.related-manga > div.col-md-3").mapNotNull { element ->
            val a = element.selectFirst("a") ?: return@mapNotNull null
            newSearchResponse(element.selectFirst("h5")?.text() ?: "No Title", a.attr("href")) {
                posterUrl = fixUrlNull(element.selectFirst("img")?.let {
                    it.attr("data-src").takeIf { src -> src.isNotBlank() } ?: it.attr("src")
                })
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val res = app.get(url).document.selectFirst("div.text-left")
        if (res == null || res.html().isBlank()) return null

        return res.html()
            .replace(Regex("\\(If you have problems with this website.*?THANKS!\\)", RegexOption.IGNORE_CASE), "")
            .replace("Read latest Chapters at BoxNovel.Com Only", "")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query&post_type=wp-manga").document
        return document.select("div.c-tabs-item__content").mapNotNull { h ->
            val title = h.selectFirst("div.tab-summary div.post-title h3 a") ?: return@mapNotNull null
            val name = title.text()
            if (name.contains("Comic", ignoreCase = true)) return@mapNotNull null

            newSearchResponse(name, title.attr("href")) {
                posterUrl = fixUrlNull(h.selectFirst("div.tab-thumb a img")?.let {
                    it.attr("data-src").takeIf { src -> src.isNotBlank() } ?: it.attr("src")
                })
                rating = h.selectFirst("div.tab-meta div.rating span.total_votes")?.text()?.toFloatOrNull()?.times(200)?.toInt()
                latestChapter = h.selectFirst("div.tab-meta div.latest-chap span.chapter a")?.text()
            }
        }
    }

    fun String.clean() = this.replace(Regex("\\s+"), " ").trim()
}