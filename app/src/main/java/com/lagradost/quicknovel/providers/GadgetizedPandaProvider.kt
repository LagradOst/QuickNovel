package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class GadgetizedPandaProvider : MainAPI() {
    override val name = "GadgetizedPanda"
    override val mainUrl = "https://gadgetizedpanda.net"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_gadgetizedpanda
    override val iconBackgroundId = R.color.colorPrimaryWhite
    override val lang = "en"

    companion object {
        private val KOFI_PATH_REGEX = Regex("(?i)^(?:(.*?)-)?(?:Volume|Vol)?-?(\\d+)?-?Chapter-(\\d+)(?:-(\\d+))?-(?:[A-Z0-9]+)$")
        private val CHAPTER_RANGE_REGEX = Regex("(?i)(?:Chapter\\s*)?(\\d+)\\s*[-–—]\\s*(\\d+)")
        private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9]+")

        private val PROMO_KEYWORDS = listOf(
            "amazon link", "green button above", "source material", "english translations", "support the author", "page translated", "green bar", "ln translations",
            "localizermeerkat", "galaxianarwhal", "check out", "join the membership", "gadgetizedpanda", "always read at", "table of contents", "support me", "ko-fi link",
            "buy the official release", "donation for faster release", "translation requests", "consider to donate", "faster translations", "donate for faster",
            "translators note", "translator note", "tlnote", "tl note", "scene transition", "picked up for english translation", "picked up for translation"
        )
    }

    // Extracts a trailing integer or decimal number after a specific keyword in a string.
    private fun String.extractTrailingNum(keyword: String, isDecimal: Boolean = false): Double? {
        val s = this.replace('\u00A0', ' ')
        val i = s.indexOf(keyword, ignoreCase = true)
        if (i < 0) return null
        return s.substring(i + keyword.length).trimStart(' ', '-', ':', '_')
            .takeWhile { it.isDigit() || (isDecimal && it == '.') }.toDoubleOrNull()
    }

    // Extracts the volume number following 'Volume' in the string.
    private fun String.extractVolumeNumber(): Int? = extractTrailingNum("Volume")?.toInt()

    // Extracts the chapter number (including decimals) following 'Chapter' in the string.
    private fun String.extractChapterNumber(): Double? = extractTrailingNum("Chapter", true)

    // Extracts the sub-part number following 'Part' in the string.
    private fun String.extractPartNumber(): Int? = extractTrailingNum("Part")?.toInt()

    // Formats a number to an integer string if whole (e.g. 5.0 -> '5'), or keeps decimal notation (e.g. 5.5 -> '5.5').
    private fun Double.formatNum(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

    private val categoryPages = listOf(
        "Translation Projects" to mainUrl,
        "Personal Projects" to "$mainUrl/page/2/",
        "Caught up projects" to "$mainUrl/page/3/"
    )

    override val mainCategories = listOf("All Projects" to "") + categoryPages

    // Loads novel directory pages based on selected project category or pagination index.
    override suspend fun loadMainPage(page: Int, mainCategory: String?, orderBy: String?, tag: String?): HeadMainPageResponse {
        val entry = if (!mainCategory.isNullOrEmpty()) {
            if (page > 1) return HeadMainPageResponse(mainUrl, emptyList())
            categoryPages.firstOrNull { it.second == mainCategory }
        } else {
            categoryPages.getOrNull(page - 1)
        } ?: return HeadMainPageResponse(mainUrl, emptyList())

        val (catName, pageUrl) = entry
        val novels = parseNovels(app.get(pageUrl).document, catName)
        return HeadMainPageResponse(pageUrl, novels)
    }

    // Extracts the first valid image URL from common lazy-loading and responsive image attributes.
    private fun Element?.extractImgSrc(): String? {
        if (this == null) return null
        val attrs = listOf("data-src", "data-lazy-src", "src", "data-full-url", "srcset", "data-srcset")
        return fixUrlNull(attrs.firstNotNullOfOrNull { attr ->
            attr(attr).trim().takeIf { it.isNotEmpty() }?.substringBefore(" ")
        })
    }

    // Parses novel cards and covers from WordPress page content and navigation menus.
    private fun parseNovels(doc: Document, category: String? = null): List<SearchResponse> {
        val coverMap = doc.select("div.entry-content a[href]").mapNotNull { a ->
            a.selectFirst("img").extractImgSrc()?.let { fixUrl(a.attr("href").trim()) to it }
        }.toMap()

        val menu = doc.selectFirst("ul#main-menu, nav#site-navigation ul, div.menu-menu-container ul")?.children()
            ?.filter { category.isNullOrEmpty() || category == "All Projects" || it.selectFirst("a")?.text()?.contains(category, true) == true }
            ?.flatMap { it.select("ul.sub-menu a[href]") } ?: emptyList()

        return menu.mapNotNull { a ->
            val name = a.text().trim()
            val href = fixUrl(a.attr("href").trim())
            if (name.isNotEmpty() && name != "A-G" && name != "H-Z" && href != "#" && href.isNotEmpty() &&
                !href.contains("announcement", true) && href != mainUrl && href != "$mainUrl/" &&
                !href.contains("/page/", true) && !href.contains("post_type=post", true)) {
                newSearchResponse(name, href) { posterUrl = fixUrlNull(coverMap[href] ?: coverMap[a.attr("href").trim()]) }
            } else null
        }.distinctBy { it.url }
    }

    // Filters and ranks novels by query relevance using FuzzySearch.
    fun filterAndRankNovels(novels: List<SearchResponse>, query: String): List<SearchResponse> {
        if (query.isBlank()) return novels
        val cleanQuery = query.trim().lowercase()
        return novels.mapNotNull { novel ->
            val name = novel.name.lowercase()
            val score = maxOf(FuzzySearch.partialRatio(name, cleanQuery), FuzzySearch.weightedRatio(name, cleanQuery))
            if (score > 50) novel to score else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    // Fetches novel listings across project categories and filters by query using FuzzySearch.
    override suspend fun search(query: String): List<SearchResponse> = filterAndRankNovels(
        categoryPages.flatMap { (catName, pageUrl) -> parseNovels(app.get(pageUrl).document, catName) }.distinctBy { it.url },
        query
    )

    // Checks if an element is a standard HTML heading tag (h1-h6).
    private fun Element.isHeading(): Boolean = tagName().lowercase() in listOf("h1", "h2", "h3", "h4", "h5", "h6")

    // Selects the main WordPress entry-content element across desktop and mobile layouts.
    private fun Document.entryContent(): Element? =
        selectFirst("div#page div#content div#primary main#main article div.entry-content, div.entry-content")

    // Filters out promo banners, affiliate links, pagination numbers, separator elements, and donation buttons from chapter content.
    private fun Element.isUnwanted(): Boolean {
        val tag = tagName().lowercase()
        if (hasClass("page-links") || hasClass("post-nav-links") || selectFirst(".page-links, .post-page-numbers") != null) return true
        if (tag == "hr" && (hasClass("wp-block-separator") || hasClass("has-alpha-channel-opacity") || hasClass("is-style-wide"))) return true
        if (tag == "p" && hasClass("has-black-color") && hasClass("has-text-color")) return true
        if (selectFirst("a[href*='ko-fi.com'], img[src*='image.png'], img[src*='kofi']") != null) return true
        if ((tag == "p" || tag == "div") && selectFirst("img, svg, picture, video") == null && text().none { it.isLetterOrDigit() }) return true

        val cleanText = text().lowercase().filter { c -> c.isLetterOrDigit() || c.isWhitespace() }
        if (cleanText.contains("table of contents") || cleanText == "index" || cleanText == "toc") return true
        return (tag == "p" || tag == "div" || tag == "figure" || isHeading()) && PROMO_KEYWORDS.any { cleanText.contains(it) }
    }

    // Validates whether a link is a legitimate chapter or special content link.
    fun isChapterLink(href: String, rawTitle: String): Boolean {
        if (href.isBlank() || href.startsWith("#") || href.contains("#comment", true) || href.contains("web.archive.org/web/", true)) return false
        val isAllowed = listOf("https://gadgetizedpanda.net", "gadgetizedpanda", "ko-fi.com/post/", "preview=true", "?p=").any { href.contains(it, true) }
        val keywords = listOf("chapter", "illustrations", "prologue", "part", "epilogue", "afterword", "extra", "interlude", "side story", "ss")
        val hasKeyword = keywords.any { rawTitle.contains(it, true) || href.contains(it, true) } || href.contains("?p=", true)
        return isAllowed && hasKeyword
    }

    // Expands structured Ko-fi multi-chapter post links into individual chapter entries.
    fun expandKofiLink(href: String, currentVolume: String): List<ChapterData>? {
        if (!href.contains("ko-fi.com/post/", ignoreCase = true)) return null
        val cleanHref = href.substringBefore('#').substringBefore('?')
        val postPath = cleanHref.substringAfterLast("post/", cleanHref.substringAfterLast("Post/")).trim('/').ifEmpty { return null }
        val match = KOFI_PATH_REGEX.find(postPath) ?: return null
        val volNum = match.groupValues[2].ifEmpty { currentVolume.filter(Char::isDigit).ifEmpty { "1" } }
        val startCh = match.groupValues[3].toIntOrNull() ?: return null
        val endCh = match.groupValues[4].toIntOrNull() ?: startCh
        if (startCh > endCh || (endCh - startCh) > 100) return null
        val novelSlug = match.groupValues[1].lowercase().replace(NON_ALPHANUM_REGEX, "-").trim('-')
        val prefix = if (novelSlug.isNotEmpty()) "$novelSlug-" else ""
        return (startCh..endCh).map { ch ->
            newChapterData(standardizeChapterTitle("Chapter $ch", "Volume $volNum"), "$mainUrl/${prefix}volume-$volNum-chapter-$ch")
        }
    }

    // Extracts start and end chapter numbers from range strings (e.g. 'Chapter 21-30').
    fun extractChapterRange(title: String, href: String): IntRange? {
        val (_, s, e) = (CHAPTER_RANGE_REGEX.find(title) ?: CHAPTER_RANGE_REGEX.find(href))?.groupValues ?: return null
        val (start, end) = (s.toIntOrNull() ?: return null) to (e.toIntOrNull() ?: return null)
        return if (end > start && (end - start) <= 100) start..end else null
    }

    // Queries the Wayback Machine CDX API concurrently to find the latest valid snapshot as fast as possible.
    suspend fun resolveSnapshotUrl(exactUrl: String): String? = kotlinx.coroutines.supervisorScope {
        val cleanUrl = exactUrl.trim().ifEmpty { return@supervisorScope null }
        val slug = cleanUrl.substringAfterLast("/").trim('/').ifEmpty { cleanUrl.substringBeforeLast("/").substringAfterLast("/") }
        if (slug.isEmpty()) return@supervisorScope null

        val cdx = "https://web.archive.org/cdx/search/cdx?fl=original,timestamp&filter=statuscode:200&limit=1&output=json"
        val queries = listOf("net", "com").flatMap { d ->
            val host = "gadgetizedpanda.$d"
            listOf("$cdx&url=${URLEncoder.encode(cleanUrl.replace("gadgetizedpanda.net", host), "UTF-8")}", "$cdx&url=$host&matchType=prefix&filter=original:.*/${slug}/?$")
        }

        val channel = Channel<String?>(queries.size)
        val jobs = queries.map { url ->
            launch {
                val snap = app.get(url, timeout = 20).parsedSafe<List<List<String>>>()?.getOrNull(1)?.let { "https://web.archive.org/web/${it[1]}/${it[0]}" }
                channel.send(snap)
            }
        }

        var result: String? = null
        for (i in queries.indices) channel.receive()?.takeIf { it.isNotEmpty() }?.let { result = it; break }
        jobs.forEach { it.cancel() }
        result
    }

    // Cleans and extracts HTML text paragraphs from a chapter post.
    fun fetchChapterContent(doc: Document): String {
        val entryContent = doc.entryContent() ?: return ""
        entryContent.select("script, style, iframe, svg, noscript, .sharedaddy, .jp-relatedposts, .wpcnt, #jp-post-flair, .wp-block-spacer").remove()

        val builder = StringBuilder()
        var started = false
        for (element in entryContent.children()) {
            val tag = element.tagName().lowercase()
            if (started && tag == "div" && element.hasClass("wp-block-columns")) break
            if (element.isUnwanted()) continue
            if (!started && (tag == "p" || tag == "figure" || element.isHeading())) started = true
            if (started) builder.appendLine(element.apply { select("a").unwrap() }.outerHtml())
        }
        return builder.toString().trim()
    }

    // Formats and standardizes chapter names with consistent volume, chapter, and part prefixes.
    fun standardizeChapterTitle(rawTitle: String, volume: String?): String {
        var title = rawTitle.trim()
        val chNum = title.extractChapterNumber()
        val partNum = title.extractPartNumber()
        val formatted = if (chNum != null && partNum != null && title.contains("Chapter", true) && title.contains("Part", true)) {
            "Chapter ${chNum.formatNum()} - Part $partNum"
        } else title
        return volume?.let { "$it - ${formatted.removePrefix(it).trimStart(' ', '-', ':')}" } ?: formatted
    }

    // Normalizes, numbers, and associates chapters and parts under their respective volumes.
    fun normalizeChaptersAndParts(rawElements: List<Element>): List<ChapterData> {
        val chapterList = mutableListOf<ChapterData>()
        var currentVolume = "Volume 1"
        var lastUnlinkedChapter: String? = null
        var lastLinkedChapter: String? = null

        for (element in rawElements) {
            if (element.isUnwanted()) continue
            val text = element.text().trim()
            val volNum = text.extractVolumeNumber()

            // 1. Detect Volume headers (<h*>, <p>, or .wp-block-heading) to update volume context
            if (volNum != null && (element.isHeading() || element.tagName().equals("p", ignoreCase = true) || element.hasClass("wp-block-heading"))) {
                currentVolume = "Volume $volNum"
                lastUnlinkedChapter = null
                lastLinkedChapter = null
                continue
            }
            val links = element.select("a[href]")

            // 2. Track unlinked chapter headings (e.g. "Chapter 1") that precede linked sub-parts ("Part 1", "Part 2")
            if (links.isEmpty()) { text.extractChapterNumber()?.let { lastUnlinkedChapter = text }; continue }
            for (link in links) {
                val href = link.attr("href").trim()
                val rawTitle = link.text().trim()

                // 3. Skip non-chapter links (navigation anchors, comments, archive pages) or empty links
                if (rawTitle.isEmpty() && href.isEmpty()) continue
                if (!isChapterLink(href, rawTitle)) continue

                // Check sub-parts & chapter numbers first
                val partNum = rawTitle.extractPartNumber() ?: href.extractPartNumber()
                val chNum = rawTitle.extractChapterNumber() ?: href.extractChapterNumber()
                val isKofi = href.contains("ko-fi.com", ignoreCase = true)

                // 4. Expand structured Ko-fi multi-chapter posts or batch ranges
                if (isKofi && partNum == null) {
                    expandKofiLink(href, currentVolume)?.let { kofi ->
                        chapterList.addAll(kofi)
                        kofi.last().name.extractChapterNumber()?.let { lastLinkedChapter = "Chapter ${it.formatNum()}".also { lastUnlinkedChapter = it } }
                        continue
                    }
                    extractChapterRange(rawTitle, href)?.let { range ->
                        range.forEach { ch -> chapterList.add(newChapterData(standardizeChapterTitle("Chapter $ch", currentVolume), href)) }
                        continue
                    }
                }

                var title = rawTitle
                // 6. Format chapter title if chapter number is present
                if (chNum != null) {
                    val chStr = "Chapter ${chNum.formatNum()}".also { lastLinkedChapter = it; lastUnlinkedChapter = it }
                    if (partNum != null && (rawTitle.contains("Part", true) || href.contains("part", true))) title = "$chStr - Part $partNum"
                } else if (partNum != null && !rawTitle.contains("Chapter", true)) {
                    (lastUnlinkedChapter ?: lastLinkedChapter)?.let { title = "$it - Part $partNum" }
                }

                // 8. Remove duplicate parent placeholder when sub-parts exist
                if (partNum != null) {
                    (lastUnlinkedChapter ?: lastLinkedChapter)?.let { baseCh ->
                        if (chapterList.lastOrNull()?.name == standardizeChapterTitle(baseCh, currentVolume)) chapterList.removeAt(chapterList.size - 1)
                    }
                }

                // 9. Add formatted chapter entry to list
                chapterList.add(newChapterData(standardizeChapterTitle(title, currentVolume), href))
            }
        }
        return chapterList
    }

    // Deterministic chapter sorting: Volume -> Chapter -> Part -> Special entries, filtering parent placeholders when parts exist
    fun sortChapters(chapters: List<ChapterData>): List<ChapterData> {
        val chaptersWithParts = chapters.mapNotNull { ch ->
            val vol = ch.name.extractVolumeNumber() ?: 1
            val cNum = ch.name.extractChapterNumber()
            val pNum = ch.name.extractPartNumber()
            if (cNum != null && pNum != null) vol to cNum else null
        }.toSet()

        val filtered = chapters.filterNot { ch ->
            val vol = ch.name.extractVolumeNumber() ?: 1
            val cNum = ch.name.extractChapterNumber()
            val pNum = ch.name.extractPartNumber()
            cNum != null && pNum == null && (vol to cNum) in chaptersWithParts
        }

        return filtered.mapIndexed { idx, ch -> ch to idx }.sortedWith(compareBy(
            { (ch, _) -> ch.name.extractVolumeNumber() ?: 1 },
            { (ch, idx) -> ch.name.extractChapterNumber() ?: when {
                ch.name.contains("illustrations", true) -> -2.0
                ch.name.contains("prologue", true) -> -1.0
                ch.name.contains("extra", true) -> 9980.0
                ch.name.contains("epilogue", true) -> 9990.0
                ch.name.contains("afterword", true) -> 9995.0
                else -> 5000.0 + idx
            }},
            { (ch, _) -> ch.name.extractPartNumber() ?: 0 }, { (_, idx) -> idx }
        )).map { it.first }
    }

    // Collect all TOC pages; sortChapters() determines logical order and removes duplicate parent placeholders.
    suspend fun buildTableOfContents(doc: Document, baseUrl: String): List<ChapterData> {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val maxPage = doc.select("a[href]").mapNotNull { a ->
            val href = a.attr("href").trim().trimEnd('/')
            if (href.startsWith(cleanBaseUrl)) href.substringAfterLast('/').toIntOrNull() else null
        }.maxOrNull() ?: 1
        val allRawElements = (maxPage downTo 1).flatMap { p ->
            val pageDoc = if (p == 1) doc else app.get("$cleanBaseUrl/$p/").document
            pageDoc.entryContent()?.children() ?: emptyList()
        }
        val rawChapters = normalizeChaptersAndParts(allRawElements)
        return sortChapters(rawChapters).distinctBy { it.name }
    }

    // Extracts the novel synopsis from the main novel details page.
    fun fetchSynopsis(doc: Document): String {
        val builder = StringBuilder()
        var started = false
        for (el in doc.entryContent()?.children() ?: emptyList()) {
            val text = el.text().trim()
            if (!started && text.contains("Synopsis", true)) {
                started = true
                text.substringAfter("Synopsis", "").trimStart(':', ' ').takeIf { it.isNotEmpty() }?.let { builder.appendLine(it).appendLine() }
            } else if (started) {
                if (el.tagName().equals("figure", true) || el.hasClass("wp-block-image") || text.startsWith("Index", true)) break
                if (text.isNotEmpty()) builder.appendLine(text).appendLine()
            }
        }
        return builder.toString().trim()
    }

    // Loads novel metadata, cover image, synopsis, and full chapter list for details view.
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore("–")?.substringBefore("-")?.trim()
            ?: throw ErrorLoadingException("Failed to find novel title for $url")
        val poster = doc.selectFirst("figure.wp-block-image img, div.entry-content figure img, div.entry-content img").extractImgSrc()
        return newStreamResponse(title, url, buildTableOfContents(doc, url)) {
            if (!poster.isNullOrEmpty()) posterUrl = poster
            this.synopsis = fetchSynopsis(doc)
        }
    }

    // Loads chapter content from the live site, falling back to archived Wayback Machine snapshots.
    override suspend fun loadHtml(url: String): String? {
        // 1. Fetch live page or direct archive snapshot
        if (!url.contains("web.archive.org/web/*/")) {
            val doc = app.get(url).document
            if (doc.selectFirst("section.error-404") == null) {
                fetchChapterContent(doc).takeIf { it.isNotEmpty() }?.let { return it }
            }
            // If 404 or empty, check if author embedded an archive link inside the post
            doc.select("div.page-content a[href], div.entry-content a[href]")
                .firstOrNull { it.attr("href").contains("web.archive.org", true) }?.attr("href")?.trim()
                ?.takeIf { it.isNotEmpty() }?.let { return loadHtml(it) }
        }

        // 2. Fallback: resolve latest Wayback snapshot via CDX
        val target = if (url.contains("web.archive.org/web/*/")) url.substringAfter("web/*/") else url
        val snapshot = if (target.contains("web.archive.org/web/")) target else resolveSnapshotUrl(target)
            ?: return "<p><em>[Chapter content not available in archive]</em></p>"
        val content = fetchChapterContent(app.get(snapshot).document)
        return if (content.isNotEmpty()) content else "<p><em>[Chapter content unavailable / website was offline when archived]</em></p>"
    }
}
