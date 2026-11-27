package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.util.AppUtils.parseJson
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

object RanobesProvider : MainAPI() {
    override val name = "Ranobes"
    override val mainUrl = "https://ranobes.top"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_ranobes
    override val iconBackgroundId = R.color.white
    override val usesCloudFlareKiller = true
    override val rateLimitTime = 2000L
    override val hasReviews = true

    override val orderBys = listOf(
        "All Novels" to "novels",
        "Latest Updates" to "updates",
        "Popular" to "ranking",
        "Now Reading" to "now-reading",
        "Completed Novels" to "completed",
    )

    override val tags = listOf(
        "Any" to "",
        "Action" to "Action",
        "Adventure" to "Adventure",
        "Comedy" to "Comedy",
        "Drama" to "Drama",
        "Fantasy" to "Fantasy",
        "Harem" to "Harem",
        "Historical" to "Historical",
        "Horror" to "Horror",
        "Josei" to "Josei",
        "Martial Arts" to "Martial%20Arts",
        "Mature" to "Mature",
        "Mecha" to "Mecha",
        "Mystery" to "Mystery",
        "Psychological" to "Psychological",
        "Romance" to "Romance",
        "School Life" to "School%20Life",
        "Sci-Fi" to "Sci-Fi",
        "Seinen" to "Seinen",
        "Shoujo" to "Shoujo",
        "Shounen" to "Shounen",
        "Slice of Life" to "Slice%20of%20Life",
        "Supernatural" to "Supernatural",
        "Tragedy" to "Tragedy",
        "Xuanhuan" to "Xuanhuan",
        "Xianxia" to "Xianxia",
    )

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:101.0) Gecko/20100101 Firefox/101.0",
        "Referer" to "$mainUrl/",
    )

    private val bgUrlRegex = Regex("""url\(['"]?([^'")\s]+)['"]?\)""")
    private val cleanTitleBulletRegex = Regex("""^(.*?)\s*•\s*.+$""")

    private fun looksBlocked(html: String): Boolean {
        if (html.isBlank()) return true
        val head = html.take(4000)
        return head.contains("Just a moment") ||
            head.contains("cf-challenge") ||
            head.contains("cf_chl") ||
            head.contains("Attention Required") ||
            head.contains("Access denied") ||
            head.contains("<title>403") ||
            head.contains("<title>429")
    }

    private suspend fun fetchDocument(url: String): Document? {
        val waits = longArrayOf(0L, 4000L, 9000L, 18000L, 30000L)
        for (waitMs in waits) {
            if (waitMs > 0L) delay(waitMs)
            try {
                val res = app.get(url, headers = baseHeaders)
                val body = res.text
                if (body.isNotBlank() && !looksBlocked(body)) {
                    return Jsoup.parse(body, url)
                }
            } catch (_: Throwable) {
                // Retry on transient 403/429/network errors
            }
        }
        return null
    }

    private fun cleanTitle(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return ""
        val match = cleanTitleBulletRegex.matchEntire(s)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: s
    }

    private fun extractBgUrl(style: String?): String? {
        if (style.isNullOrBlank()) return null
        return bgUrlRegex.find(style)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun parseCatalogItems(document: Document): List<SearchResponse> {
        val seen = HashSet<String>()
        val cards = document.select(".block.story.shortstory, .shortstory, .story, div.short-cont")
        return cards.mapNotNull { card ->
            val titleEl = card.selectFirst("h2.title a, .title a, a[href*='/novels/']")
                ?: return@mapNotNull null
            val href = fixUrlNull(titleEl.attr("href")) ?: return@mapNotNull null
            if (!seen.add(href)) return@mapNotNull null

            val title = cleanTitle(titleEl.text())
            if (title.isBlank()) return@mapNotNull null

            val coverEl = card.selectFirst("figure.cover, .cover")
            val rawCover = extractBgUrl(coverEl?.attr("style"))
                ?: coverEl?.attr("data-src")?.takeIf { it.isNotBlank() }
                ?: coverEl?.attr("src")?.takeIf { it.isNotBlank() }
                ?: card.selectFirst("img")?.let { img ->
                    img.attr("data-src").takeIf { it.isNotBlank() }
                        ?: img.attr("src").takeIf { it.isNotBlank() }
                }

            val ratingStr = card.selectFirst(".rate_num, .current-rating")?.text()
                ?.let { Regex("""\d+\.?\d*""").find(it)?.value }

            newSearchResponse(name = title, url = href) {
                posterUrl = fixUrlNull(rawCover)
                score = Score.from5(ratingStr)
            }
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val path = when {
            !tag.isNullOrBlank() -> "/tags/genre/$tag/"
            orderBy == "completed" -> "/tags/status-trs/Completed/"
            orderBy in listOf("updates", "ranking", "now-reading") -> "/$orderBy/"
            else -> "/novels/"
        }
        val url = if (page <= 1) "$mainUrl$path" else "$mainUrl${path}page/$page/"
        val document = fetchDocument(url) ?: return HeadMainPageResponse(url, emptyList())
        return HeadMainPageResponse(url, parseCatalogItems(document))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()

        try {
            val postDoc = app.post(
                "$mainUrl/index.php?do=search",
                headers = mapOf(
                    "Referer" to "$mainUrl/",
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "User-Agent" to baseHeaders["User-Agent"]!!,
                ),
                data = mapOf(
                    "do" to "search",
                    "subaction" to "search",
                    "story" to q,
                    "catlist[]" to "1",
                    "search_start" to "1",
                    "full_search" to "0",
                    "result_from" to "1",
                )
            ).document
            val postItems = parseCatalogItems(postDoc)
            if (postItems.isNotEmpty()) return postItems
        } catch (_: Throwable) {
            // Fall back to GET search route below
        }

        val encoded = URLEncoder.encode(q, "UTF-8").replace("+", "%20")
        val getDoc = fetchDocument("$mainUrl/search/$encoded/") ?: return emptyList()
        return parseCatalogItems(getDoc)
    }

    private fun extractBookId(bookUrl: String, document: Document): String? {
        Regex("""/novels/(\d+)""").find(bookUrl)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("""/chapters/(\d+)""").find(bookUrl)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("""-(\d+)\.html""").find(bookUrl)?.groupValues?.getOrNull(1)?.let { return it }
        val chHref = document.selectFirst("a[href*='/chapters/']")?.attr("href").orEmpty()
        return Regex("""/chapters/(\d+)""").find(chHref)?.groupValues?.getOrNull(1)
    }

    private fun getChapters(bookUrl: String, document: Document): List<ChapterData> {
        val bookId = extractBookId(bookUrl, document)
        val chapterListUrl = fixUrlNull(document.selectFirst("a.read-continue")?.attr("href"))
            ?: bookId?.let { "$mainUrl/chapters/$it/" }

        val totalChapters =
            document.selectFirst("li[title=\"Glossary + illustrations + division of chapters, etc.\"] span")
                ?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0

        if (totalChapters > 25 && !chapterListUrl.isNullOrBlank()) {
            return (0 until totalChapters).map { chapterNumber ->
                val chapterUrl = "$chapterListUrl-------$chapterNumber-------$totalChapters"
                newChapterData("Chapter ${chapterNumber + 1}", chapterUrl)
            }
        }

        return document.select("ul.chapters-scroll-list li").reversed()
            .mapIndexedNotNull { index, li ->
                val chName = li.selectFirst("span.title")?.text() ?: "Chapter ${index + 1}"
                val chUrl = li.selectFirst("a")?.attr("href") ?: return@mapIndexedNotNull null
                newChapterData(chName, chUrl)
            }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = fetchDocument(url) ?: return null

        val titleEl = document.selectFirst("h1.title")?.clone() ?: return null
        titleEl.select("span.subtitle, span[hidden]").remove()
        val name = cleanTitle(titleEl.text()).ifEmpty {
            cleanTitle(document.selectFirst("h1.title")?.ownText())
        }
        if (name.isEmpty()) return null

        val chapters = getChapters(url, document)

        return newStreamResponse(url = url, name = name, data = chapters) {
            val specItems = document.select(".r-fullstory-spec li")

            author = specItems.firstOrNull {
                val t = it.text().trim()
                t.startsWith("Author:") || t.startsWith("Authors:")
            }?.select("a")?.mapNotNull { it.text().trim().takeIf { s -> s.isNotEmpty() } }
                ?.joinToString(", ")
                ?.takeIf { it.isNotEmpty() }
                ?: document.select("h1.title > span").lastOrNull()?.text()

            tags = (document.select("#mc-fs-genre a").takeIf { it.isNotEmpty() }
                ?: document.select(".r-fullstory-genres a"))
                .mapNotNull { it.text().trim().takeIf { s -> s.isNotEmpty() } }
                .distinct()

            val descEl = (document.selectFirst(".moreless__full")
                ?: document.selectFirst(".moreless__short, [itemprop='description']")
                ?: document.selectFirst("div.moreless"))?.clone()
            descEl?.select("a.moreless__toggle, a")?.remove()
            synopsis = descEl?.text()?.trim()?.takeIf { it.isNotEmpty() }

            val rawCover = document.selectFirst(".r-fullstory-poster a.highslide")
                ?.attr("href")?.takeIf { it.isNotBlank() }
                ?: extractBgUrl(
                    document.selectFirst(".r-fullstory-poster figure.cover, .poster figure.cover")
                        ?.attr("style")
                )
                ?: document.selectFirst(".r-fullstory-poster img, .poster img, div.poster > a > img")
                    ?.let { img ->
                        img.attr("src").takeIf { it.isNotBlank() }
                            ?: img.attr("data-src").takeIf { it.isNotBlank() }
                    }
            posterUrl = fixUrlNull(rawCover)

            try {
                val ratingStr = document.selectFirst("#rate_b span.bold")?.text()?.substringBefore("/")
                    ?: document.selectFirst(".r-fullstory-s1 .current-rating")?.text()
                score = Score.from5(ratingStr)
                peopleVoted = document.selectFirst("div.rate-stat-num > span.small.grey")?.text()
                    ?.filter { it.isDigit() }?.toIntOrNull()
            } catch (_: Throwable) {
            }

            views = document.selectFirst("#fs-info > div.r-fullstory-spec > ul:nth-child(2) > li:nth-child(1) > span")
                ?.text()?.filter { it.isDigit() }?.toIntOrNull()

            val translationStatus = specItems.firstOrNull {
                it.text().trim().startsWith("Translation:")
            }?.let { li ->
                li.selectFirst("a, span.grey")?.text()?.trim()
                    ?: li.text().removePrefix("Translation:").trim()
            }
            val cooStatus = specItems.firstOrNull {
                it.text().trim().startsWith("Status in COO:")
            }?.selectFirst("a, span.grey")?.text()?.trim()
            setStatus(translationStatus ?: cooStatus)

            related = getRelated(document)
            reviewData = document.selectFirst("input[name=newsid]")?.attr("value")
        }
    }

    private fun getRelated(dc: Document): List<SearchResponse> {
        return dc.select("div.tab-content > div.tab-pane > div.story_line").mapNotNull { element ->
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("span.title")?.text() ?: return@mapNotNull null
            newSearchResponse(name = title, url = href) {
                posterUrl = fixUrlNull(
                    element.selectFirst("i.image.cover")?.attr("style")
                        ?.substringAfter(":url(")?.substringBefore(")")
                )
            }
        }
    }

    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val id = data ?: return emptyList()
        val ajaxUrl =
            "$mainUrl/engine/ajax/controller.php?mod=comments&cstart=$page&news_id=$id&skin=Dark"

        val res = app.get(ajaxUrl, headers = baseHeaders).parsedSafe<RanobesCommentsResponse>()
        val htmlContent = res?.comments ?: return emptyList()
        val document = Jsoup.parse(htmlContent)

        return document.select("div.comment").mapNotNull { item ->
            val info = item.selectFirst("div.com_info")
            val contentElement = item.selectFirst("div.com_content div.cont-text")
            val isSpoiler = contentElement?.getElementsByClass("spoiler-cont")?.isNotEmpty() == true

            val reviewTxt = contentElement?.text().orEmpty()
            if (reviewTxt.isBlank()) return@mapNotNull null

            val scoreRaw = item.selectFirst("span.review-rating-num")?.text()?.toFloatOrNull()
            val overallScore = scoreRaw?.times(200)?.toInt()

            newReview(reviewTxt) {
                username = info?.selectFirst(".name")?.text()
                date = info?.selectFirst("time")?.text()
                avatarUrl = info?.selectFirst(".avatar .cover")?.attr("style")
                    ?.substringAfter("url(")
                    ?.substringBefore(")")
                    ?.replace("'", "")
                rating = overallScore
                containsSpoilers = isSpoiler
            }
        }
    }

    private fun getChaptersFromPage(document: Document): List<String> {
        // 1. Try <script id="chapters-data"> JSON (used on ranobes.top)
        val chaptersDataRaw = document.selectFirst("script#chapters-data")?.data()?.trim()
        if (!chaptersDataRaw.isNullOrEmpty()) {
            try {
                return parseJson<Root>(chaptersDataRaw).chapters.mapNotNull { fixUrlNull(it.link) }.reversed()
            } catch (_: Throwable) {
            }
        }

        // 2. Fallback to window.__DATA__
        val windowDataScript = document.selectFirst("script:containsData(window.__DATA__)")?.data()
        if (!windowDataScript.isNullOrEmpty()) {
            try {
                val jsonString = windowDataScript
                    .substringAfter("window.__DATA__ =")
                    .substringBeforeLast("}")
                    .trim() + "}"
                return parseJson<Root>(jsonString).chapters.mapNotNull { fixUrlNull(it.link) }.reversed()
            } catch (_: Throwable) {
            }
        }

        // 3. Fallback to server-rendered .cat_line links
        return document.select(".cat_line a, .cat_block.cat_line a")
            .mapNotNull { fixUrlNull(it.attr("href")) }
            .filter { it.endsWith(".html") }
            .reversed()
    }

    private fun getPaginationPositionAndRelativeIndexDescendant(
        arguments: List<String>,
        itemsPerPage: Int,
    ): Pair<Int, Int> {
        val chapterBigIndex = arguments[1].toInt()
        val totalChapters = arguments[2].toInt()
        val totalPages = (totalChapters + itemsPerPage - 1) / itemsPerPage
        val page = (totalChapters - 1 - chapterBigIndex) / itemsPerPage + 1
        val chaptersInLastPage = (totalChapters % itemsPerPage).let { if (it == 0) itemsPerPage else it }
        val index = if (page == totalPages) {
            chapterBigIndex % itemsPerPage
        } else {
            (chapterBigIndex - chaptersInLastPage) % itemsPerPage
        }
        return page to index
    }

    override suspend fun loadHtml(url: String): String? {
        val chapterData = url.split("-------")
        val targetUrl = if (chapterData.size < 3) {
            url
        } else {
            val baseChapterUrl = chapterData[0].removeSuffix("/")
            val (page, index) = getPaginationPositionAndRelativeIndexDescendant(chapterData, 25)
            val pageUrl = if (page <= 1) "$baseChapterUrl/" else "$baseChapterUrl/page/$page/"
            val listDoc = fetchDocument(pageUrl) ?: return null
            getChaptersFromPage(listDoc).getOrNull(index) ?: return null
        }

        val dc = fetchDocument(targetUrl) ?: return null
        val title = dc.selectFirst("#dle-content > article > div.block.story.shortstory > h1, h1.title")
            ?.html().orEmpty()
        val content = (dc.selectFirst("#arrticle") ?: dc.selectFirst(".text, .story"))?.clone()
            ?: return null

        content.select("img").forEach { img ->
            val src = img.attr("src")
            if (src.isNotBlank()) {
                fixUrlNull(src)?.let { fixed -> img.attr("src", fixed) }
            }
        }

        // Strip injected ad containers between paragraphs
        content.select("script, style, ins, aside, iframe, div[class*=ad], div[id*=ad]").remove()

        val paragraphs = content.select("p").mapNotNull { p ->
            val inner = p.html().trim()
            if (inner.isEmpty()) null else "<p>$inner</p>"
        }
        val bodyHtml = if (paragraphs.isNotEmpty()) paragraphs.joinToString("\n") else content.html()

        return if (title.isNotBlank()) "<h1>$title</h1>\n$bodyHtml" else bodyHtml
    }

    data class Root(
        @JsonProperty("book_title") val bookTitle: String? = null,
        @JsonProperty("book_link") val bookLink: String? = null,
        @JsonProperty("book_id") val bookId: Long? = null,
        @JsonProperty("chapters") val chapters: List<Chapter> = emptyList(),
        @JsonProperty("pages_count") val pagesCount: Long? = null,
        @JsonProperty("count_all") val countAll: Long? = null,
        @JsonProperty("cstart") val cstart: Long? = null,
        @JsonProperty("limit") val limit: Long? = null,
        @JsonProperty("search") val search: String? = null,
    )

    data class Chapter(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("date") val date: String? = null,
        @JsonProperty("comm_num") val commNum: String? = null,
        @JsonProperty("show_date") val showDate: String? = null,
        @JsonProperty("link") val link: String? = null,
    )

    data class RanobesCommentsResponse(
        @JsonProperty("comments") val comments: String? = null,
    )
}