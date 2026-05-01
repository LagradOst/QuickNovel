package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.nodes.Element

class SonicMTLProvider : MainAPI() {
    override val name = "SonicMTL"
    override val mainUrl = "https://www.sonicmtl.com"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_sonicmtl
    override val iconBackgroundId = R.color.colorPrimaryWhite

    override val tags = listOf(
        "All" to "",
        "Action" to "action",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Drama" to "genre",
        "Ecchi" to "ecchi",
        "Fantasy" to "fantasy",
        "Harem" to "harem",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Gender Bender" to "gender-bender",
        "Historical" to "historical",
        "Horror" to "horror",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shounen" to "shounen",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
    )

    override val orderBys = listOf(
        "New" to "new-manga",
        "Most Views" to "views",
        "Trending" to "trending",
        "Rating" to "rating",
        "A-Z" to "alphabet",
        "Latest" to "latest",
    )

    private fun Element?.getImage(): String? {
        return this?.let {
            if (it.hasAttr("data-src")) it.attr("data-src") else it.attr("src")
        }
    }

    private fun String.clean() = this.replace(Regex("\\s+"), " ").trim()

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val order = if (tag.isNullOrBlank()) "novel" else "novel-genre/$tag"
        val orderQuery = if (orderBy.isNullOrBlank()) "" else "?m_orderby=$orderBy"
        val url = "$mainUrl/$order/page/$page/$orderQuery"

        val document = app.get(url).document
        val returnValue = document.select("div.page-item-detail").mapNotNull { h ->
            val a = h.selectFirst("div.item-thumb > a") ?: return@mapNotNull null
            val name = a.attr("title")

            if (name.contains("Comic", ignoreCase = true)) return@mapNotNull null

            newSearchResponse(name = name, url = a.attr("href")) {
                posterUrl = a.selectFirst("img").getImage()
            }
        }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query&post_type=wp-manga").document
        return document.select("div.c-tabs-item__content").mapNotNull { h ->
            val titleA = h.selectFirst("div.post-title h3 a") ?: return@mapNotNull null
            val name = titleA.text()

            if (name.contains("Comic", ignoreCase = true)) return@mapNotNull null

            val ratingTxt = h.selectFirst("span.total_votes")?.text()

            newSearchResponse(name = name, url = titleA.attr("href")) {
                posterUrl = h.selectFirst("div.tab-thumb img").getImage()
                rating = ratingTxt?.toFloatOrNull()?.let { (it * 200).toInt() }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val name = document.selectFirst("div.post-title > h1")?.text()?.clean() ?: return null

        val chapterDoc = app.post("${url.removeSuffix("/")}/ajax/chapters/").document
        val chapters = chapterDoc.select("li.wp-manga-chapter").mapNotNull { c ->
            val a = c.selectFirst("a") ?: return@mapNotNull null
            val cName = a.text().clean()
            val added = c.selectFirst("span.chapter-release-date")?.text()
            newChapterData(name = cName, url = a.attr("href")) {
                dateOfRelease = added
            }
        }.reversed()

        return newStreamResponse(url = url, name = name, data = chapters) {
            tags = document.select("div.genres-content > a").map { it.text() }
            author = document.selectFirst("div.author-content > a")?.text()

            // Selección robusta de sinopsis
            val synopsisParts = document.select(".summary__content p, #editdescription p, .j_synopsis p")
            synopsis = synopsisParts
                .map { it.text().trim() }
                .filter { it.isNotEmpty() && !it.contains(mainUrl, ignoreCase = true) }
                .joinToString("\n\n")

            setStatus(document.select("div.post-status .summary-content").lastOrNull()?.text())
            posterUrl = document.selectFirst("div.summary_image img").getImage()

            rating = (document.selectFirst("#averagerate")?.text()?.toFloatOrNull()?.times(200))?.toInt()

            // Lógica de conteo de votos (9.3K -> 9300)
            document.selectFirst("#countrate")?.text()?.let { votes ->
                peopleVoted = votes.uppercase()
                    .replace(".", "")
                    .replace("K", if (votes.contains(".")) "00" else "000")
                    .toIntOrNull() ?: 0
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val content = document.selectFirst("div.text-left") ?: return null

        if (content.text().isBlank()) return null

        val ads = listOf(
            "myboxnovel.com",
            "BoxNovel.Com",
            "Read latest Chapters at",
            "If you have problems with this website"
        )

        // Limpiar párrafos que contienen spam/ads
        content.select("p").forEach { p ->
            if (ads.any { ad -> p.text().contains(ad, ignoreCase = true) }) {
                p.remove()
            }
        }

        return content.html()
    }
}