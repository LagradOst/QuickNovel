package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
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

        private val PROMO_KEYWORDS = listOf(
            "amazon link", "green button above", "source material", "english translations",
            "support the author", "page translated", "green bar", "ln translations",
            "localizermeerkat", "also check out", "join the membership"
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

        val menu = doc.selectFirst("ul#main-menu, nav#site-navigation ul, div.menu-menu-container ul")
        val menuLinks = menu?.children()?.filter { topLi ->
            val catText = topLi.selectFirst("a")?.text()?.trim().orEmpty()
            category.isNullOrEmpty() || category == "All Projects" || catText.contains(category, ignoreCase = true)
        }?.flatMap { it.select("ul.sub-menu a[href]") } ?: emptyList()

        return menuLinks.mapNotNull { a ->
            val name = a.text().trim()
            val href = fixUrl(a.attr("href").trim())
            val rawHref = a.attr("href").trim()
            if (name.isNotEmpty() && name != "A-G" && name != "H-Z" && href != "#" && href.isNotEmpty() &&
                !href.contains("announcement", ignoreCase = true) && href != mainUrl && href != "$mainUrl/" &&
                !href.contains("/page/", ignoreCase = true) && !href.contains("post_type=post", ignoreCase = true)) {
                val coverUrl = coverMap[href] ?: coverMap[rawHref]
                newSearchResponse(name, href) { posterUrl = fixUrlNull(coverUrl) }
            } else null
        }.distinctBy { it.url }
    }

    // Fetches novel listings across project categories for QuickNovel search indexing.
    override suspend fun search(query: String): List<SearchResponse> = categoryPages.flatMap { (catName, pageUrl) ->
        parseNovels(app.get(pageUrl).document, catName)
    }.distinctBy { it.url }

    // Checks if an element is a standard HTML heading tag (h1-h6).
    private fun Element.isHeading(): Boolean = tagName().lowercase() in listOf("h1", "h2", "h3", "h4", "h5", "h6")

    // Selects the main WordPress entry-content element across desktop and mobile layouts.
    private fun Document.entryContent(): Element? =
        selectFirst("div#page div#content div#primary main#main article div.entry-content, div.entry-content")

    // Filters out promo banners, affiliate links, and separator elements from chapter content.
    private fun Element.isUnwanted(): Boolean {
        val tag = tagName().lowercase()
        if (tag == "hr" && (hasClass("wp-block-separator") || hasClass("has-alpha-channel-opacity") || hasClass("is-style-wide"))) return true
        if (tag == "p" && hasClass("has-black-color") && hasClass("has-text-color")) return true
        if (tag != "p" && tag != "div" && !isHeading()) return false
        val clean = text().filter { it.isLetterOrDigit() || it.isWhitespace() }
        return PROMO_KEYWORDS.any { clean.contains(it, ignoreCase = true) }
    }

    // Validates whether a link is a legitimate chapter or special content link.
    fun isChapterLink(href: String, rawTitle: String): Boolean {
        if (href.isBlank() || href.startsWith("#") || href.contains("#comment", ignoreCase = true) || href.contains("web.archive.org/web/", ignoreCase = true)) return false
        val isAllowed = listOf("https://gadgetizedpanda.net", "gadgetizedpanda", "ko-fi.com/post/", "preview=true").any { href.contains(it, ignoreCase = true) }
        val keywords = listOf("chapter", "illustrations", "prologue", "part", "epilogue", "afterword", "extra")
        return isAllowed && keywords.any { rawTitle.contains(it, ignoreCase = true) || href.contains(it, ignoreCase = true) }
    }

    // Expands structured Ko-fi multi-chapter post links into individual chapter entries.
    fun expandKofiLink(href: String, currentVolume: String): List<ChapterData>? {
        if (!href.contains("ko-fi.com/post/", ignoreCase = true)) return null
        val postPath = href.substringAfterLast("post/", href.substringAfterLast("Post/")).trim('/').ifEmpty { return null }
        val match = KOFI_PATH_REGEX.find(postPath) ?: return null
        val rawSlug = match.groupValues[1]
        val volNum = match.groupValues[2].ifEmpty { currentVolume.filter(Char::isDigit).ifEmpty { "1" } }
        val startCh = match.groupValues[3].toIntOrNull() ?: return null
        val endCh = match.groupValues[4].toIntOrNull() ?: startCh
        if (startCh > endCh || (endCh - startCh) > 100) return null
        val slug = if (rawSlug.isNotEmpty()) "${rawSlug.lowercase()}-" else ""
        return (startCh..endCh).map { ch ->
            newChapterData(standardizeChapterTitle("Chapter $ch", "Volume $volNum"), "$mainUrl/${slug}volume-$volNum-chapter-$ch")
        }
    }

    // Extracts start and end chapter numbers from range strings (e.g. 'Chapter 21-30').
    fun extractChapterRange(title: String, href: String): IntRange? {
        val (_, s, e) = (CHAPTER_RANGE_REGEX.find(title) ?: CHAPTER_RANGE_REGEX.find(href))?.groupValues ?: return null
        val start = s.toIntOrNull() ?: return null
        val end = e.toIntOrNull() ?: return null
        return if (end > start && (end - start) <= 100) start..end else null
    }

    // Queries the Wayback Machine CDX API to find the latest valid snapshot for a chapter URL.
    suspend fun resolveSnapshotUrl(exactUrl: String): String? {
        val cleanUrl = exactUrl.trim().ifEmpty { return null }
        val slug = cleanUrl.trimEnd('/').substringAfterLast('/')
        val queries = listOf(
            "https://web.archive.org/cdx/search/cdx?url=${URLEncoder.encode(cleanUrl, "UTF-8")}&fl=original,timestamp&filter=statuscode:200&output=json",
            "https://web.archive.org/cdx/search/cdx?url=gadgetizedpanda.com&matchType=prefix&fl=original,timestamp&filter=statuscode:200&filter=original:.*$slug.*&limit=10&output=json"
        )
        return queries.firstNotNullOfOrNull { queryUrl ->
            app.get(queryUrl).parsedSafe<List<List<String>>>()
                ?.drop(1)?.filter { it.size >= 2 }?.maxByOrNull { it[1] }
                ?.let { "https://web.archive.org/web/${it[1]}/${it[0]}" }
        }
    }

    // Cleans and extracts HTML text paragraphs from a chapter post.
    fun fetchChapterContent(doc: Document): String {
        val entryContent = doc.entryContent() ?: return ""
        entryContent.select("script, style, iframe, svg, noscript, .sharedaddy, .jp-relatedposts, .wpcnt, #jp-post-flair").remove()

        val builder = StringBuilder()
        var started = false
        for (element in entryContent.children()) {
            val tag = element.tagName().lowercase()
            if (tag == "div" && element.hasClass("wp-block-columns")) break
            if (element.isUnwanted()) continue
            if (!started && (tag == "p" || element.isHeading())) started = true
            if (started) builder.appendLine(element.apply { select("a").unwrap() }.outerHtml())
        }
        return builder.toString().trim()
    }

    // Formats and standardizes chapter names with consistent volume, chapter, and part prefixes.
    fun standardizeChapterTitle(rawTitle: String, volume: String?): String {
        var title = rawTitle.trim()
        val chNum = title.extractChapterNumber()
        val partNum = title.extractPartNumber()
        if (chNum != null && partNum != null && title.contains("Chapter", ignoreCase = true) && title.contains("Part", ignoreCase = true)) {
            title = "Chapter ${chNum.formatNum()} - Part $partNum"
        }
        if (volume != null) {
            if (title.startsWith(volume, ignoreCase = true)) title = title.substring(volume.length).trimStart(' ', '-', ':')
            return "$volume - $title"
        }
        return title
    }

    // Normalizes, numbers, and associates chapters and parts under their respective volumes.
    fun normalizeChaptersAndParts(rawElements: List<Element>): List<ChapterData> {
        val chapterList = mutableListOf<ChapterData>()
        var currentVolume = "Volume 1"
        var lastUnlinkedChapter: String? = null
        var lastLinkedChapter: String? = null

        for (element in rawElements) {
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

                // 4. Expand structured Ko-fi multi-chapter posts only when the link itself is not a specific sub-part
                if (isKofi && partNum == null) {
                    val kofiChapters = expandKofiLink(href, currentVolume)
                    if (!kofiChapters.isNullOrEmpty()) {
                        chapterList.addAll(kofiChapters)
                        kofiChapters.last().name.extractChapterNumber()?.let { num ->
                            lastUnlinkedChapter = "Chapter ${num.formatNum()}"
                            lastLinkedChapter = lastUnlinkedChapter
                        }
                        continue
                    }

                    // 5. Expand batch Ko-fi posts matching range regex in title/URL (e.g. "Chapter 21 - 30")
                    val chapterRange = extractChapterRange(rawTitle, href)
                    if (chapterRange != null) {
                        chapterRange.forEach { ch -> chapterList.add(newChapterData(standardizeChapterTitle("Chapter $ch", currentVolume), href)) }
                        continue
                    }
                }

                var title = rawTitle
                // 6. Format chapter title if chapter number is present
                if (chNum != null) {
                    val chStr = "Chapter ${chNum.formatNum()}"
                    lastUnlinkedChapter = chStr
                    lastLinkedChapter = chStr
                    // Format as "Chapter X - Part Y" if sub-part is present
                    if (partNum != null && (rawTitle.contains("Part", ignoreCase = true) || href.contains("part", ignoreCase = true))) {
                        title = "$chStr - Part $partNum"
                    }
                } else if (partNum != null && !rawTitle.contains("Chapter", ignoreCase = true)) {
                    // 7. Associate orphaned sub-parts ("Part 1") with the preceding parent chapter
                    (lastUnlinkedChapter ?: lastLinkedChapter)?.let { title = "$it - Part $partNum" }
                }

                // 8. Remove duplicate parent placeholder when sub-parts exist (e.g. remove "Chapter 1" if "Chapter 1 - Part 1" is added)
                if (partNum != null) {
                    (lastUnlinkedChapter ?: lastLinkedChapter)?.let { baseCh ->
                        val parentName = standardizeChapterTitle(baseCh, currentVolume)
                        if (chapterList.lastOrNull()?.name == parentName) chapterList.removeAt(chapterList.size - 1)
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
            { (ch, idx) ->
                ch.name.extractChapterNumber() ?: when {
                    ch.name.contains("illustrations", ignoreCase = true) -> -2.0
                    ch.name.contains("prologue", ignoreCase = true) -> -1.0
                    ch.name.contains("extra", ignoreCase = true) -> 9980.0
                    ch.name.contains("epilogue", ignoreCase = true) -> 9990.0
                    ch.name.contains("afterword", ignoreCase = true) -> 9995.0
                    else -> 5000.0 + idx
                }
            },
            { (ch, _) -> ch.name.extractPartNumber() ?: 0 },
            { (_, idx) -> idx }
        )).map { it.first }
    }

    // Collect all TOC pages; sortChapters() determines logical order and removes duplicate parent placeholders.
    suspend fun buildTableOfContents(doc: Document, baseUrl: String): List<ChapterData> {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val pageRegex = Regex("""${Regex.escape(cleanBaseUrl)}/(\d+)/?""")
        val maxPage = doc.select("a[href]").mapNotNull { a ->
            pageRegex.find(a.attr("href").trim())?.groupValues?.get(1)?.toIntOrNull()
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
        val entryContent = doc.entryContent() ?: return ""
        val builder = StringBuilder()
        var started = false
        for (el in entryContent.children()) {
            val text = el.text().trim()
            if (!started && text.contains("Synopsis", ignoreCase = true)) {
                started = true
                val after = text.substringAfter("Synopsis", "").trimStart(':', ' ')
                if (after.isNotEmpty()) builder.append(after).append("\n\n")
                continue
            }
            if (started) {
                if (el.tagName().equals("figure", ignoreCase = true) || el.hasClass("wp-block-image") || text.startsWith("Index", ignoreCase = true)) break
                if (text.isNotEmpty()) builder.append(text).append("\n\n")
            }
        }
        return builder.toString().trim()
    }

    // Loads novel metadata, cover image, synopsis, and full chapter list for details view.
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore("–")?.substringBefore("-")?.trim() ?: return null
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
                val content = fetchChapterContent(doc)
                if (content.isNotEmpty()) return content
            }
            // If 404 or empty, check if author embedded an archive link inside the post
            val embedded = doc.select("div.page-content a[href], div.entry-content a[href]")
                .firstOrNull { it.attr("href").contains("web.archive.org", ignoreCase = true) }?.attr("href")?.trim()
            if (!embedded.isNullOrEmpty()) return loadHtml(embedded)
        }

        // 2. Fallback: resolve latest Wayback snapshot via CDX
        val target = if (url.contains("web.archive.org/web/*/")) url.substringAfter("web/*/") else url
        val snapshot = if (target.contains("web.archive.org/web/")) target else resolveSnapshotUrl(target) ?: return null
        return fetchChapterContent(app.get(snapshot).document).takeIf { it.isNotEmpty() }
    }
}
