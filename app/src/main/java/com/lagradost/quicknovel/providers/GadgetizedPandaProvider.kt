package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class GadgetizedPandaProvider : MainAPI() {
    override val name = "GadgetizedPanda"
    override val mainUrl = "https://gadgetizedpanda.net"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_gadgetizedpanda
    override val iconBackgroundId = R.color.colorPrimaryWhite
    override val lang = "en"

    data class ArchiveEntry(val timestamp: String, val originalUrl: String, val archiveUrl: String)

    companion object {
        @Volatile private var cachedSlugMap: Map<String, ArchiveEntry>? = null
        @Volatile private var lastCacheFetchTime: Long = 0L
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
        private val cacheMutex = Mutex()
        private val VOLUME_REGEX = Regex("Volume[\\s\\u00A0]+(\\d+)", RegexOption.IGNORE_CASE)
        private val CHAPTER_NUM_REGEX = Regex("(?i)^Chapter\\s+(\\d+(?:\\.\\d+)?)")
        private val PART_NUM_REGEX = Regex("(?i)^Part\\s+(\\d+)")
        private val KOFI_PATH_REGEX = Regex("(?i)^(?:(.*?)-)?(?:Volume|Vol)?-?(\\d+)?-?Chapter-(\\d+)(?:-(\\d+))?-(?:[A-Z0-9]+)$")
        private val CHAPTER_RANGE_REGEX = Regex("(?i)(?:Chapter\\s*)?(\\d+)\\s*[-–—]\\s*(\\d+)")
        private val CHAPTER_PART_REGEX = Regex("(?i)Chapter\\s+(\\d+(?:\\.\\d+)?)\\s+Part\\s+(\\d+)")
        private val DIGITS_REGEX = Regex("""\d+""")
    }

    init { ioSafe { ensureWaybackCache() } }

    // WAYBACK CDX CACHE — Downloads and caches Wayback Machine snapshot indices once per 24h for both .net and .com domains.
    private suspend fun ensureWaybackCache(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (cachedSlugMap != null && (now - lastCacheFetchTime) <= CACHE_DURATION_MS && !forceRefresh) return
        cacheMutex.withLock {
            val checkNow = System.currentTimeMillis()
            if (cachedSlugMap != null && (checkNow - lastCacheFetchTime) <= CACHE_DURATION_MS && !forceRefresh) return@withLock
            try {
                val tempMap = mutableMapOf<String, ArchiveEntry>()
                for (domain in listOf("gadgetizedpanda.net", "gadgetizedpanda.com")) {
                    try {
                        val listUrl = "https://web.archive.org/cdx/search/cdx?url=$domain/&matchType=prefix&fl=original,timestamp&filter=statuscode:200&output=json"
                        val jsonText = app.get(listUrl).text
                        if (jsonText.isNotEmpty() && jsonText.trim() != "[]") {
                            DataStore.mapper.readValue<Array<Array<String>>>(jsonText).drop(1).forEach { row ->
                                if (row.size >= 2 && row[0].isNotBlank() && row[1].isNotBlank()) {
                                    val (orig, ts) = row[0] to row[1]
                                    val slug = orig.trimEnd('/').substringAfterLast('/').lowercase()
                                    if (slug.isNotEmpty() && (tempMap[slug] == null || ts > tempMap[slug]!!.timestamp)) {
                                        tempMap[slug] = ArchiveEntry(ts, orig, "https://web.archive.org/web/$ts/$orig")
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                if (tempMap.isNotEmpty()) {
                    cachedSlugMap = tempMap
                    lastCacheFetchTime = checkNow
                }
            } catch (_: Exception) {}
        }
    }

    // MAIN PAGE CATALOG — Defines category mappings and loads main page novel lists.
    override val mainCategories = listOf(
        "All Projects" to mainUrl, "Translation Projects" to "$mainUrl/",
        "Personal Projects" to "$mainUrl/page/2/", "Caught up projects" to "$mainUrl/page/3/"
    )
    var promoKeywords = mutableListOf(
        "Amazon link", "green button above", "source material", "English translations", "SOURCE MATERIAL",
        "SUPPORT THE AUTHOR", "page translated", "green bar", "LN translations", "Localizermeerkat", "Also check out",
        "Join the membership"
    )

    override suspend fun loadMainPage(page: Int, mainCategory: String?, orderBy: String?, tag: String?): HeadMainPageResponse {
        if (page > 1) return HeadMainPageResponse("", emptyList())
        val doc = app.get(mainUrl).document
        ioSafe {
            listOf("$mainUrl/page/2/", "$mainUrl/page/3/").forEach { p ->
                try {
                    app.get(p).document.selectFirst("div.entry-content")?.select("a[href]")?.forEach { a ->
                        val href = fixUrl(a.attr("href").trim())
                        val src = extractImgSrc(a.selectFirst("img"))
                        if (href.isNotEmpty() && !src.isNullOrEmpty()) coverCache[href] = src
                    }
                } catch (_: Exception) {}
            }
        }
        return HeadMainPageResponse(mainUrl, parseNovels(doc, mainCategory))
    }

    // NOVEL CATALOG & SEARCH — Parses project listings, extracts covers, and searches across all Gadgetized Panda pages.
    private val coverCache = ConcurrentHashMap<String, String>()

    private suspend fun getCoverForUrl(url: String): String? = coverCache[url] ?: try {
        app.get(url).document.let { doc -> fetchNovelCover(doc)?.also { coverCache[url] = it } }
    } catch (_: Exception) { null }

    private fun extractImgSrc(img: Element?): String? {
        if (img == null) return null
        val attrs = listOf("data-src", "data-lazy-src", "src", "data-full-url", "srcset", "data-srcset")
        return fixUrlNull(attrs.map { img.attr(it).trim().substringBefore(" ") }.firstOrNull { it.isNotEmpty() })
    }

    private fun createNovelResponse(name: String, url: String, cover: String? = null): SearchResponse =
        newSearchResponse(name, fixUrl(url)) { if (!cover.isNullOrEmpty()) posterUrl = fixUrl(cover) }

    private fun parseNovels(doc: Document, mainCategory: String? = null): List<SearchResponse> {
        val novels = mutableListOf<SearchResponse>()
        val coverMap = mutableMapOf<String, String>()

        doc.selectFirst("div.entry-content")?.select("a[href]")?.forEach { a ->
            val href = fixUrl(a.attr("href").trim())
            val src = extractImgSrc(a.selectFirst("img"))
            if (href.isNotEmpty() && !src.isNullOrEmpty()) {
                coverMap[href] = src
                coverCache[href] = src
            }
        }

        val targetCatName = when {
            mainCategory.isNullOrEmpty() || mainCategory == mainUrl || mainCategory == "All Projects" -> "All Projects"
            mainCategory.contains("/page/2", ignoreCase = true) || mainCategory.contains("personal", ignoreCase = true) || mainCategory == "Personal Projects" -> "Personal Projects"
            mainCategory.contains("/page/3", ignoreCase = true) || mainCategory.contains("caught", ignoreCase = true) || mainCategory == "Caught up projects" -> "Caught up projects"
            mainCategory == "$mainUrl/" || mainCategory.contains("translation", ignoreCase = true) || mainCategory == "Translation Projects" -> "Translation Projects"
            else -> mainCategory
        }

        doc.selectFirst("ul#main-menu, nav#site-navigation ul, div.menu-menu-container ul")?.children()?.forEach { topLi ->
            val catText = topLi.selectFirst("a")?.text()?.trim() ?: ""
            if (targetCatName == "All Projects" || catText.contains(targetCatName, ignoreCase = true) || targetCatName.contains(catText, ignoreCase = true)) {
                topLi.select("ul.sub-menu a[href]").forEach { a ->
                    val name = a.text().trim()
                    val href = fixUrl(a.attr("href").trim())
                    val rawHref = a.attr("href").trim()
                    if (name.isNotEmpty() && name != "A-G" && name != "H-Z" && href != "#" && href.isNotEmpty() &&
                        !href.contains("announcement", ignoreCase = true) && href != mainUrl && href != "$mainUrl/" &&
                        !href.contains("/page/", ignoreCase = true) && !href.contains("post_type=post", ignoreCase = true)) {
                        val coverUrl = coverMap[href] ?: coverMap[rawHref] ?: coverCache[href] ?: coverCache[rawHref]
                        novels.add(createNovelResponse(name, href, coverUrl))
                    }
                }
            }
        }
        return novels.distinctBy { it.url }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allNovels = mutableListOf<SearchResponse>()
        listOf(mainUrl, "$mainUrl/page/2/", "$mainUrl/page/3/").forEach { pageUrl ->
            try { allNovels.addAll(parseNovels(app.get(pageUrl).document)) } catch (_: Exception) {}
        }
        val novelMap = LinkedHashMap<String, SearchResponse>()
        for (novel in allNovels) {
            val existing = novelMap[novel.url]
            if (existing == null || (existing.posterUrl.isNullOrEmpty() && !novel.posterUrl.isNullOrEmpty())) {
                novelMap[novel.url] = novel
            }
        }
        return novelMap.values.filter { it.name.contains(query, ignoreCase = true) }.onEach { novel ->
            if (novel.posterUrl.isNullOrEmpty()) getCoverForUrl(novel.url)?.let { novel.posterUrl = it }
            else coverCache[novel.url] = novel.posterUrl!!
        }
    }

    // CONTENT & PROMOTION FILTERING — Filters out unwanted HTML dividers, promotional text, and non-chapter link anchors.
    fun removeUnwantedHtml(element: Element): Boolean =
        (element.tagName() == "hr" && (element.hasClass("wp-block-separator") || element.hasClass("has-alpha-channel-opacity") || element.hasClass("is-style-wide"))) ||
        (element.tagName() == "p" && element.hasClass("has-black-color") && element.hasClass("has-text-color"))

    fun isPromotionParagraph(element: Element): Boolean {
        val tag = element.tagName()
        if (tag != "p" && !tag.matches(Regex("h[1-6]")) && tag != "div") return false
        val cleanText = element.text().replace(Regex("[^a-zA-Z0-9_\\s]"), " ")
        return promoKeywords.any { cleanText.contains(it.replace(Regex("[^a-zA-Z0-9_\\s]"), " "), ignoreCase = true) }
    }

    fun isChapterLink(href: String, rawTitle: String): Boolean {
        if (href.isEmpty() || href.startsWith("#") || href.contains("#comment", ignoreCase = true) || href.contains("web.archive.org/web/", ignoreCase = true)) return false
        val isAllowed = href.contains(mainUrl, ignoreCase = true) || href.contains("gadgetizedpanda", ignoreCase = true) || href.contains("ko-fi.com/Post/", ignoreCase = true) || href.contains("ko-fi.com/post/", ignoreCase = true)
        return isAllowed && (rawTitle.contains("Chapter", ignoreCase = true) || rawTitle.contains("Illustrations", ignoreCase = true) || rawTitle.contains("Prologue", ignoreCase = true) || rawTitle.contains("Part", ignoreCase = true) || href.contains("chapter", ignoreCase = true) || href.contains("illustrations", ignoreCase = true))
    }

    // KO-FI & RANGE EXPANSION — Expands multi-chapter Ko-fi links and numeric chapter ranges into individual entries.
    fun expandKofiLink(href: String, currentVolume: String): List<ChapterData>? {
        if (!href.contains("ko-fi.com/Post/", ignoreCase = true) && !href.contains("ko-fi.com/post/", ignoreCase = true)) return null
        val postPath = href.substringAfter("Post/").substringAfter("post/").trim('/').ifEmpty { return null }
        val match = KOFI_PATH_REGEX.find(postPath) ?: return null
        val rawSlug = match.groupValues[1].ifEmpty { "" }
        val volNum = match.groupValues[2].ifEmpty { DIGITS_REGEX.find(currentVolume)?.value ?: "1" }
        val startCh = match.groupValues[3].toIntOrNull() ?: return null
        val endCh = match.groupValues[4].toIntOrNull() ?: startCh
        if (startCh > endCh || (endCh - startCh) > 100) return null
        val slugPart = if (rawSlug.isNotEmpty()) "${rawSlug.lowercase()}-" else ""
        return (startCh..endCh).map { ch ->
            newChapterData(standardizeChapterTitle("Chapter $ch", "Volume $volNum"), "$mainUrl/${slugPart}volume-$volNum-chapter-$ch")
        }
    }

    fun convertKofiUrl(href: String): String = href

    fun extractChapterRange(title: String, href: String): IntRange? {
        val match = CHAPTER_RANGE_REGEX.find(title) ?: CHAPTER_RANGE_REGEX.find(href) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues[2].toIntOrNull() ?: return null
        return if (end > start && (end - start) <= 100) start..end else null
    }

    // WAYBACK CHAPTER RESOLUTION — Resolves chapter slugs to Wayback Machine archive URLs and handles 404 snapshot fallbacks.
    suspend fun resolveWaybackChapterUrl(chapterUrlOrSlug: String): String? {
        val cleanInput = chapterUrlOrSlug.trim().ifEmpty { return null }
        ensureWaybackCache()
        val slug = cleanInput.trimEnd('/').substringAfterLast('/').lowercase()
        return (cachedSlugMap ?: emptyMap())[slug]?.archiveUrl ?: resolveSnapshotUrl(cleanInput.takeIf { it.startsWith("http") } ?: return null)
    }

    suspend fun findNextChapterUrl(currentUrlOrSlug: String): String? {
        val currentSlug = currentUrlOrSlug.trim().ifEmpty { return null }.trimEnd('/').substringAfterLast('/').lowercase()
        val currentChNum = (Regex("""chapter-(\d+)""").find(currentSlug) ?: return null).groupValues[1].toIntOrNull() ?: return null
        return resolveWaybackChapterUrl(currentSlug.replace("chapter-$currentChNum", "chapter-${currentChNum + 1}"))
    }

    suspend fun resolveSnapshotUrl(exactUrl: String): String? = try {
        val cleanUrl = exactUrl.trim().ifEmpty { return null }
        val cdxUrl = "https://web.archive.org/cdx/search/cdx?url=${URLEncoder.encode(cleanUrl, "UTF-8")}&fl=original,timestamp&filter=statuscode:200&output=json"
        val jsonText = app.get(cdxUrl).text
        if (jsonText.isEmpty() || jsonText.trim() == "[]") null
        else DataStore.mapper.readValue<Array<Array<String>>>(jsonText).drop(1).filter { it.size >= 2 }
            .maxByOrNull { it[1] }?.let { newest -> "https://web.archive.org/web/${newest[1]}/${newest[0]}" }
    } catch (_: Exception) { null }

    suspend fun handleWaybackFallback(doc: Document): Document {
        val pageContent = doc.selectFirst("div#page div#content div#primary main#main section.error-404.not-found div.page-content")
            ?: doc.selectFirst("section.error-404 div.page-content") ?: doc.selectFirst("div.page-content")
        val is404 = doc.selectFirst("section.error-404") != null || doc.selectFirst("section.not-found") != null || doc.title().contains("404", ignoreCase = true)
        if (!is404 || pageContent == null) return doc

        val archiveLink = pageContent.select("a[href]").firstOrNull { it.attr("href").contains("web.archive.org", ignoreCase = true) } ?: return doc
        val rawArchiveUrl = archiveLink.attr("href").trim().ifEmpty { return doc }
        val exactTarget = if (rawArchiveUrl.contains("web.archive.org/web/*/")) rawArchiveUrl.substringAfter("web/*/") else rawArchiveUrl
        return app.get(resolveSnapshotUrl(exactTarget) ?: rawArchiveUrl).document
    }

    // CHAPTER CONTENT & METADATA PARSING — Extracts chapter body content and formats chapter titles and priority sorting.
    fun fetchChapterContent(doc: Document): String {
        val entryContent = doc.selectFirst("div#page div#content div#primary main#main article div.entry-content")
            ?: doc.selectFirst("div.entry-content") ?: return ""
        val builder = StringBuilder()
        var contentStarted = false
        for (element in entryContent.children()) {
            if (element.tagName() == "div" && element.hasClass("wp-block-columns")) break
            if (removeUnwantedHtml(element) || isPromotionParagraph(element)) continue
            if (!contentStarted && (element.tagName() == "p" || element.tagName().matches(Regex("h[1-6]")))) contentStarted = true
            if (contentStarted) {
                element.select("a").unwrap()
                builder.append(element.outerHtml()).append("\n")
            }
        }
        return builder.toString().trim()
    }

    fun fetchCategories(doc: Document): List<Pair<String, String>> =
        listOf("All Projects" to mainUrl) + doc.select("div#page nav#site-navigation div.menu-menu-container ul#main-menu > li > a")
            .map { it.text().trim() to it.attr("href").trim() }
            .filter { (name, _) -> name.contains("Projects", ignoreCase = true) }
            .distinctBy { it.first }

    fun standardizeChapterTitle(rawTitle: String, volume: String?): String {
        var title = rawTitle.trim().replace(CHAPTER_PART_REGEX, "Chapter $1 - Part $2")
        if (volume != null) title = title.replace(Regex("^${Regex.escape(volume)}\\s*[-:]?\\s*", RegexOption.IGNORE_CASE), "")
        return if (volume != null) "$volume - $title" else title
    }

    fun getChapterPriority(title: String): Int = when {
        title.contains("Illustrations", ignoreCase = true) -> 1
        title.contains("Prologue", ignoreCase = true) -> 2
        title.contains("Chapter", ignoreCase = true) -> 3
        else -> 4
    }

    // CHAPTER NORMALIZATION & TOC BUILDING — Aggregates and normalizes chapter listings into volumes and builds table of contents.
    fun normalizeChaptersAndParts(rawElements: List<Element>, currentVolDefault: String = "Volume 1"): List<ChapterData> {
        val chapterList = mutableListOf<ChapterData>()
        var currentVolume = currentVolDefault
        var lastUnlinkedChapter: String? = null
        var lastLinkedChapter: String? = null
        for (element in rawElements) {
            val text = element.text().trim()
            val volMatch = VOLUME_REGEX.find(text)
            if (volMatch != null && (element.tagName().matches(Regex("h[1-6]|p")) || element.hasClass("wp-block-heading"))) {
                currentVolume = "Volume ${volMatch.groupValues[1]}"
                lastUnlinkedChapter = null
                lastLinkedChapter = null
                continue
            }
            val links = element.select("a[href]")
            if (links.isEmpty()) {
                if (CHAPTER_NUM_REGEX.containsMatchIn(text)) lastUnlinkedChapter = text
                continue
            }
            for (link in links) {
                var href = link.attr("href").trim()
                val rawTitle = link.text().trim()
                if (!isChapterLink(href, rawTitle)) continue

                val kofiChapters = expandKofiLink(href, currentVolume)
                if (!kofiChapters.isNullOrEmpty()) {
                    chapterList.addAll(kofiChapters)
                    continue
                }
                val isKofi = href.contains("ko-fi.com", ignoreCase = true)
                if (isKofi) href = convertKofiUrl(href)

                val chapterRange = extractChapterRange(rawTitle, href)
                if (isKofi && chapterRange != null) {
                    chapterRange.forEach { chNum -> chapterList.add(newChapterData(standardizeChapterTitle("Chapter $chNum", currentVolume), href)) }
                    continue
                }
                var title = rawTitle
                if (CHAPTER_NUM_REGEX.containsMatchIn(rawTitle)) {
                    lastUnlinkedChapter = CHAPTER_NUM_REGEX.find(rawTitle)?.value ?: rawTitle
                    lastLinkedChapter = lastUnlinkedChapter
                } else if (PART_NUM_REGEX.containsMatchIn(rawTitle) && lastUnlinkedChapter != null && !rawTitle.contains("Chapter", ignoreCase = true)) {
                    title = "$lastUnlinkedChapter - ${PART_NUM_REGEX.find(rawTitle)?.value ?: rawTitle}"
                } else if (PART_NUM_REGEX.containsMatchIn(rawTitle) && lastLinkedChapter != null && !rawTitle.contains("Chapter", ignoreCase = true)) {
                    title = "$lastLinkedChapter - ${PART_NUM_REGEX.find(rawTitle)?.value ?: rawTitle}"
                    if (chapterList.isNotEmpty() && chapterList.last().name.contains(lastLinkedChapter, ignoreCase = true)) {
                        chapterList.removeAt(chapterList.size - 1)
                    }
                }
                chapterList.add(newChapterData(standardizeChapterTitle(title, currentVolume), href))
            }
        }
        return chapterList.distinctBy { it.name }.distinctBy { it.url }
    }

    suspend fun buildTableOfContents(doc: Document, baseUrl: String): List<ChapterData> {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val pageRegex = Regex("""${Regex.escape(cleanBaseUrl)}/(\d+)/?""")
        val pageNumbers = mutableSetOf(1)
        doc.select("a[href]").forEach { a ->
            pageRegex.find(a.attr("href").trim())?.groupValues?.get(1)?.toIntOrNull()?.let { pageNumbers.add(it) }
        }
        val maxPage = pageNumbers.maxOrNull() ?: 1
        val allRawElements = mutableListOf<Element>()
        for (p in maxPage downTo 1) {
            val pageDoc = if (p == 1) doc else try { app.get("$cleanBaseUrl/$p/").document } catch (_: Exception) { continue }
            val entryContent = pageDoc.selectFirst("div#page div#content div#primary main#main article div.entry-content")
                ?: pageDoc.selectFirst("div.entry-content") ?: continue
            allRawElements.addAll(entryContent.children())
        }
        val chaptersByVolume = normalizeChaptersAndParts(allRawElements).groupBy { chapter ->
            Regex("^(Volume\\s+\\d+)").find(chapter.name)?.groupValues?.get(1) ?: "Volume 1"
        }
        return chaptersByVolume.flatMap { (_, volChapters) -> volChapters.sortedWith(compareBy { getChapterPriority(it.name) }) }.distinctBy { it.name }.distinctBy { it.url }
    }

    // NOVEL DETAILS & READER API — Loads novel details, covers, synopses, and chapter HTML for QuickNovel reader.
    fun fetchNovelCover(doc: Document): String? = extractImgSrc(
        doc.selectFirst("figure.wp-block-image.size-large img")
            ?: doc.selectFirst("figure.wp-block-image img")
            ?: doc.selectFirst("div.entry-content figure img")
            ?: doc.selectFirst("div.entry-content img")
    )

    fun fetchSynopsis(doc: Document): String {
        val entryContent = doc.selectFirst("div#page div#content div#primary main#main article div.entry-content")
            ?: doc.selectFirst("div.entry-content") ?: return ""
        val synopsisBuilder = StringBuilder()
        var synopsisStarted = false
        for (element in entryContent.children()) {
            val text = element.text().trim()
            if (!synopsisStarted && (text.startsWith("Synopsis", ignoreCase = true) || text.contains("Synopsis :", ignoreCase = true))) {
                synopsisStarted = true
                val afterColon = text.substringAfter("Synopsis :", "").substringAfter("Synopsis:", "").trim()
                if (afterColon.isNotEmpty()) synopsisBuilder.append(afterColon).append("\n\n")
                continue
            }
            if (synopsisStarted) {
                val isFigure = element.tagName() == "figure" || element.hasClass("wp-block-image") || element.selectFirst("figure.wp-block-image") != null
                val isIndex = text.equals("Index", ignoreCase = true) || text.startsWith("Index", ignoreCase = true)
                if (isFigure || isIndex) break
                if (text.isNotEmpty()) synopsisBuilder.append(text).append("\n\n")
            }
        }
        return synopsisBuilder.toString().trim()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore("–")?.substringBefore("-")?.trim() ?: return null
        val posterUrl = coverCache[url] ?: fetchNovelCover(doc)?.also { coverCache[url] = it }
        val synopsis = fetchSynopsis(doc)
        val chapters = buildTableOfContents(doc, url)
        return newStreamResponse(title, url, chapters) {
            if (!posterUrl.isNullOrEmpty()) this.posterUrl = posterUrl
            this.synopsis = synopsis
        }
    }

    override suspend fun loadHtml(url: String): String? {
        if (url.contains("web.archive.org/web/", ignoreCase = true)) {
            try { fetchChapterContent(app.get(url).document).takeIf { it.isNotEmpty() }?.let { return it } } catch (_: Exception) {}
        }
        if (url.startsWith("http")) {
            try { fetchChapterContent(handleWaybackFallback(app.get(url).document)).takeIf { it.isNotEmpty() }?.let { return it } } catch (_: Exception) {}
        }
        val snapshotUrl = resolveWaybackChapterUrl(url) ?: resolveSnapshotUrl(url) ?: return null
        return fetchChapterContent(handleWaybackFallback(app.get(snapshotUrl).document)).ifEmpty { null }
    }
}
