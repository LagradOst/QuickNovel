package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.util.amap
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class GadgetizedPandaProvider : MainAPI() {
    override val name = "GadgetizedPanda"
    override val mainUrl = "https://gadgetizedpanda.net"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_gadgetizedpanda
    override val iconBackgroundId = R.color.colorPrimaryWhite
    override val lang = "en"

    private val METADATA = Regex("(?i)(?:chapter[-_](\\d+)[-_.]?5[-_]part[-_](\\d+)|chapter[-_](\\d{1,3})5[-_]part[-_](\\d+)|[?&]page=(\\d+)|\\b(vol(?:ume)?|chapter|ch|part)[-_\\s]*(\\d+(?:\\.\\d+)?)(?:[-_](\\d+))?|^\\s*(\\d+(?:\\.\\d+)?)[-_](\\d+)\\s*$)")

    private fun CharSequence.containsAny(vararg kw: String) = kw.any { contains(it, true) }
    private fun CharSequence.containsAny(kw: Iterable<String>) = kw.any { contains(it, true) }
    private fun String.cleanUrl() = substringBefore('#').trimEnd('/').let { if (it.contains("?p=")) it.substringBefore('&') else it.substringBefore('?') }
    private fun String.toSlug() = lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
    private fun String.toCleanSlug() = substringAfterLast('/').takeIf { it.isNotEmpty() && !it.startsWith('?') }
    private fun String.extractNovelSlug() = cleanUrl().split('/').dropLastWhile { it.toIntOrNull() != null }.lastOrNull()?.replace("-ln", "")?.toSlug().orEmpty()
    private fun Double.formatNum() = if (this % 1.0 == 0.0) toInt().toString() else toString()
    private fun Int.isValidSubPart() = this <= 20 && this !in listOf(25, 50, 75)
    private fun String.isSpecialTitle() = containsAny("afterword", "epilogue", "prologue", "illust", "extra", "side story", "interlude", "excerpt")
    fun extractKofiSlug(url: String) = Regex("(?i)post/(.*?)(?:-[A-Z0-9]{6,15})?/?(?:[?#].*)?$").find(url)?.groupValues?.get(1)?.toSlug()

    private fun String.extractVolumeNumber(): Int? =
        METADATA.findAll(replace('\u00A0', ' ')).firstNotNullOfOrNull { m ->
            m.groupValues[6].takeIf { it.startsWith("vol", true) }?.let { m.groupValues[7].toDoubleOrNull()?.toInt() }
        }

    private fun String.extractChapterNumber(): Double? = takeUnless { it.contains("final chapter", true) }?.let { str ->
        METADATA.findAll(str.replace('\u00A0', ' ')).firstNotNullOfOrNull { m ->
            (m.groupValues[1].ifEmpty { m.groupValues[3] }).takeIf(String::isNotEmpty)?.let { "$it.5".toDoubleOrNull() }
                ?: m.groupValues[6].takeIf { it.startsWith("chapter", true) || it.equals("ch", true) }?.let { m.groupValues[7].toDoubleOrNull() }
                ?: m.groupValues[9].takeIf(String::isNotEmpty)?.let { ch ->
                    m.groupValues[10].toIntOrNull()?.takeIf { it.isValidSubPart() }?.let { ch.toDoubleOrNull() }
                }
        }
    }

    private fun String.extractPartNumber(): Int? = replace('\u00A0', ' ').let { clean ->
        METADATA.findAll(clean).firstNotNullOfOrNull { m ->
            listOf(2, 4, 5).firstNotNullOfOrNull { m.groupValues[it].takeIf(String::isNotEmpty)?.toIntOrNull() }
                ?: m.groupValues[6].takeIf { it.startsWith("part", true) }?.let { m.groupValues[7].toDoubleOrNull()?.toInt() }
                ?: m.groupValues[6].takeIf { it.startsWith("chapter", true) || it.equals("ch", true) }?.let {
                    m.groupValues[8].toIntOrNull()?.takeIf { it.isValidSubPart() }
                }
                ?: m.groupValues[9].takeIf(String::isNotEmpty)?.let {
                    m.groupValues[10].toIntOrNull()?.takeIf { it.isValidSubPart() }
                }
        }
    }

    private val categoryPages = listOf(
        "Translation Projects" to mainUrl,
        "Personal Projects" to "$mainUrl/page/2/",
        "Caught up projects" to "$mainUrl/page/3/"
    )

    override val mainCategories = listOf("All Projects" to "") + categoryPages

    override suspend fun loadMainPage(page: Int, mainCategory: String?, orderBy: String?, tag: String?): HeadMainPageResponse {
        if (page > 1) return HeadMainPageResponse(mainCategory?.ifEmpty { mainUrl } ?: mainUrl, emptyList())
        if (mainCategory.isNullOrEmpty() || mainCategory == "All Projects") {
            return HeadMainPageResponse(mainUrl, categoryPages.amap { (_, u) -> parseNovels(app.get(u).document) }.flatten().distinctBy { it.url }.sortedBy { it.name.lowercase() })
        }
        val targetUrl = categoryPages.firstOrNull { it.second == mainCategory }?.second ?: return HeadMainPageResponse(mainUrl, emptyList())
        return HeadMainPageResponse(targetUrl, parseNovels(app.get(targetUrl).document))
    }

    // Extracts the first valid image URL from common lazy-loading and responsive image attributes.
    private fun Element?.extractImgSrc(): String? = this?.let { el ->
        listOf("data-src", "data-lazy-src", "src", "data-full-url", "srcset", "data-srcset")
            .firstNotNullOfOrNull { el.attr(it).trim().takeIf(String::isNotEmpty)?.substringBefore(" ") }?.let(::fixUrlNull)
    }

    // Parses novel cards and covers from WordPress page content and navigation menus.
    private fun parseNovels(doc: Document): List<SearchResponse> {
        val elements = doc.select("div.entry-content > *"); val coverMap = mutableMapOf<String, String>()
        elements.forEachIndexed { i, el ->
            val src = el.selectFirst("img").extractImgSrc()?.takeUnless { it.containsAny("kofi", "button", "w=139") } ?: return@forEachIndexed
            listOfNotNull(el, elements.getOrNull(i - 1), elements.getOrNull(i + 1)).forEach { cand ->
                cand.select("a[href]").forEach { a ->
                    val h = fixUrl(a.attr("href").trim()).cleanUrl()
                    coverMap[h] = src; h.toCleanSlug()?.let { coverMap[it] = src }
                    a.text().filter(Char::isLetterOrDigit).lowercase().takeIf(String::isNotEmpty)?.let { coverMap[it] = src }
                }
            }
        }
        val menuNames = doc.select("ul#main-menu ul.sub-menu a[href], nav#site-navigation ul.sub-menu a[href], div.menu-menu-container ul.sub-menu a[href]")
            .mapNotNull { a -> fixUrl(a.attr("href").trim()).cleanUrl().toCleanSlug()?.to(a.text().trim()) }.toMap()

        return elements.mapNotNull { el ->
            val a = el.selectFirst("a[href]") ?: return@mapNotNull null
            val href = fixUrl(a.attr("href").trim()); val rawName = a.text().trim(); val cleanHref = href.cleanUrl()
            if (rawName.isEmpty() || rawName in listOf("A-G", "H-Z") || href in listOf("#", mainUrl, "$mainUrl/") || cleanHref in listOf(mainUrl, "$mainUrl/") ||
                href.containsAny("/page/", "post_type=post", "announcement", "ko-fi.com")) return@mapNotNull null

            val slug = cleanHref.toCleanSlug()
            val name = slug?.let { menuNames[it] } ?: rawName
            val poster = coverMap[cleanHref] ?: slug?.let { coverMap[it] } ?: coverMap[rawName.filter(Char::isLetterOrDigit).lowercase()] ?: coverMap[href]
            newSearchResponse(name, href) { posterUrl = fixUrlNull(poster) }
        }.distinctBy { it.url }.sortedBy { it.name.lowercase() }
    }

    // Filters and ranks novels by query relevance using FuzzySearch.
    fun filterAndRankNovels(novels: List<SearchResponse>, query: String): List<SearchResponse> =
        if (query.isBlank()) novels else query.trim().lowercase().let { clean ->
            novels.mapNotNull { n ->
                val score = maxOf(FuzzySearch.partialRatio(n.name.lowercase(), clean), FuzzySearch.weightedRatio(n.name.lowercase(), clean))
                if (score > 50) n to score else null
            }.sortedByDescending { it.second }.map { it.first }
        }

    // Fetches novel listings across project categories and filters by query using FuzzySearch.
    override suspend fun search(query: String): List<SearchResponse> = filterAndRankNovels(
        categoryPages.amap { (_, pageUrl) -> parseNovels(app.get(pageUrl).document) }.flatten().distinctBy { it.url }, query
    )

    private fun Element.isHeading() = tagName().lowercase() in listOf("h1", "h2", "h3", "h4", "h5", "h6")
    private fun Document.entryContent() = selectFirst("div#page div#content div#primary main#main article div.entry-content, div.entry-content")
    private fun Document.isDeleted404() = selectFirst("body.error404, section.error-404") != null
    fun Document.isDomainDown() = selectFirst("p.site-label, div.card h1, a.btn-archive, a.btn-kofi") != null

    // Filters out promo banners, affiliate links, pagination numbers, separator elements, and donation buttons from chapter content.
    private fun Element.isUnwanted(): Boolean {
        val tag = tagName().lowercase(); val cleanText = text().lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }
        return hasClass("page-links") || hasClass("post-nav-links") || selectFirst(".page-links, .post-page-numbers") != null ||
            (tag == "hr" && (hasClass("wp-block-separator") || hasClass("has-alpha-channel-opacity") || hasClass("is-style-wide"))) ||
            (tag == "p" && hasClass("has-black-color") && hasClass("has-text-color")) ||
            selectFirst("img[src*='image.png'], img[src*='kofi']") != null || select("a[href*='ko-fi.com']").any { !it.attr("href").contains("/post/", true) } ||
            ((tag in listOf("p", "div")) && selectFirst("img, svg, picture, video") == null && text().none(Char::isLetterOrDigit)) ||
            cleanText.contains("table of contents") || cleanText in listOf("index", "toc") ||
            ((tag in listOf("p", "div", "figure") || isHeading()) && cleanText.containsAny(
                "amazon link", "green button above", "source material", "english translations", "support the author", "page translated", "green bar", "ln translations",
                "localizermeerkat", "galaxianarwhal", "check out", "join the membership", "gadgetizedpanda", "always read at", "table of contents", "support me", "ko-fi link",
                "buy the official release", "donation for faster release", "translation requests", "consider to donate", "faster translations", "donate for faster",
                "translators note", "translator note", "tlnote", "tl note", "scene transition", "picked up for english translation", "picked up for translation"
            ))
    }

    fun isChapterLink(href: String, rawTitle: String, baseUrl: String = ""): Boolean {
        val h = href.lowercase().trim(); val t = rawTitle.lowercase().trim()
        val cleanH = h.cleanUrl(); val cleanB = baseUrl.lowercase().cleanUrl()
        if (h.isEmpty() || h.startsWith("#") || h.contains("#comment") || t.matches(Regex("""^[\d/\s\-_.:]+$""")) ||
            (cleanB.isNotEmpty() && (cleanH == cleanB || (cleanH.startsWith(cleanB) && cleanH.removePrefix(cleanB).trim('/').toIntOrNull() != null)))) return false

        val sideStory = Regex("(?i)(?:\\b(?:ss|side[-_\\s]?story)\\b|[-_/]ss[-_/])")
        val chapterKeywords = listOf("chapter", "illust", "prologue", "part", "epilogue", "afterword", "extra", "interlude", "side story", "ending", "final", "episode", "last")
        if (!h.containsAny("gadgetizedpanda.net", "gadgetizedpanda", "ko-fi.com/post/", "web.archive.org/web/", "preview=true", "?p=") ||
            (!t.containsAny(chapterKeywords) && !h.containsAny(chapterKeywords) && !sideStory.containsMatchIn(t) && !sideStory.containsMatchIn(h) && !h.contains("?p="))) return false

        val novelSlug = baseUrl.extractNovelSlug()
        if (novelSlug.length >= 4 && !h.containsAny("ko-fi.com", "?p=", "preview=true")) {
            val slugParts = novelSlug.split('-').filter { it.length >= 3 }
            if (slugParts.isNotEmpty() && !slugParts.any { cleanH.contains(it) || t.contains(it) }) return false
        }
        return true
    }

    // Expands structured Ko-fi multi-chapter post links into individual chapter entries.
    fun expandKofiLink(href: String, currentVolume: String): List<ChapterData>? {
        val slug = extractKofiSlug(href) ?: return null
        val range = extractChapterRange(slug, href) ?: return null
        val volNum = slug.extractVolumeNumber() ?: currentVolume.filter(Char::isDigit).toIntOrNull() ?: 1
        val novelSlug = slug.substringBefore("-volume", slug.substringBefore("-vol", slug.substringBefore("-chapter", "")))
            .takeIf { !it.startsWith("vol") && !it.startsWith("chapter") }?.toSlug().orEmpty()
        val prefix = if (novelSlug.isNotEmpty()) "$novelSlug-" else ""
        return range.map { newChapterData(standardizeChapterTitle("Chapter $it", "Volume $volNum"), "$mainUrl/${prefix}volume-$volNum-chapter-$it") }
    }

    // Extracts start and end chapter numbers from range strings (e.g. 'Chapter 21-30').
    fun extractChapterRange(title: String, href: String): IntRange? =
        Regex("(?i)(?:Chapter\\s*)?(\\d+)\\s*[-–—]\\s*(\\d+)").let { re ->
            (re.find(title) ?: if (title.extractChapterNumber() == null) re.find(href) else null)?.let { m ->
                val start = m.groupValues[1].toIntOrNull() ?: return null
                val end = m.groupValues[2].toIntOrNull() ?: return null
                if (end in (start + 1)..(start + 10)) start..end else null
            }
        }

    // Queries the Wayback Machine CDX API concurrently using amap to find the latest valid snapshot as fast as possible.
    suspend fun resolveSnapshotUrl(exactUrl: String): String? {
        val slug = extractKofiSlug(exactUrl) ?: exactUrl.substringAfter('#', "").takeIf { it.isNotEmpty() && !it.startsWith("comment") } ?: exactUrl.cleanUrl().toCleanSlug()?.toSlug() ?: return null
        val cdx = "https://web.archive.org/cdx/search/cdx?fl=original,timestamp&filter=statuscode:200&limit=1&output=json"
        return listOf("gadgetizedpanda.com", "gadgetizedpanda.net").map { "$cdx&url=$it&matchType=prefix&filter=original:.*/$slug/?$" }
            .amap { app.get(it).parsedSafe<List<List<String>>>()?.getOrNull(1)?.let { row -> "https://web.archive.org/web/${row[1]}/${row[0]}" } }
            .firstOrNull { !it.isNullOrEmpty() }
    }

    // Cleans and extracts HTML text paragraphs from a chapter post.
    fun fetchChapterContent(doc: Document): String = doc.entryContent()?.let { entry ->
        entry.select("script, style, iframe, svg, noscript, .sharedaddy, .jp-relatedposts, .wpcnt, #jp-post-flair, .wp-block-spacer").remove()
        buildString {
            var started = false
            for (element in entry.children()) {
                val tag = element.tagName().lowercase()
                if (started && tag == "div" && element.hasClass("wp-block-columns")) break
                if (element.isUnwanted()) continue
                if (!started && (tag in listOf("p", "figure") || element.isHeading())) started = true
                if (started) appendLine(element.apply { select("a").unwrap() }.outerHtml())
            }
        }.trim()
    } ?: ""

    // Formats and standardizes chapter names with consistent Title Casing for TOC entries.
    fun standardizeChapterTitle(rawTitle: String, volume: String?): String {
        val title = rawTitle.trim(); val chNum = title.extractChapterNumber(); val partNum = title.extractPartNumber()
        val titleWords = listOf("prologue", "epilogue", "afterword", "interlude", "extra", "side story", "part", "chapter")
        val formatted = when {
            chNum != null && partNum != null -> "Chapter ${chNum.formatNum()} - Part $partNum"
            chNum == null && title.contains("illust", true) -> "Illustrations"
            titleWords.take(4).any { title.equals(it, true) } -> title.lowercase().replaceFirstChar(Char::titlecase)
            titleWords.any { title.startsWith(it, true) } -> {
                val prefix = titleWords.first { title.startsWith(it, true) }
                title.replaceFirst(prefix, prefix.split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }, true)
            }
            else -> title
        }
        return volume?.let { "$it - ${formatted.removePrefix(it).trimStart(' ', '-', ':')}" } ?: formatted
    }

    // Normalizes, numbers, and associates chapters and parts under their respective volumes.
    fun normalizeChaptersAndParts(rawElements: List<Element>, novelSlug: String = "", baseUrl: String = ""): List<ChapterData> {
        val chapterList = mutableListOf<ChapterData>()
        var currentVolume = "Volume 1"; var lastUnlinkedChapter: String? = null; var lastLinkedChapter: String? = null
        val sectionHeader = Regex("(?i)^\\s*(?:extra|side\\s*story|episode|prologue|epilogue|afterword)\\s*\\d*.*$")

        for (element in rawElements) {
            if (element.isUnwanted()) continue
            val text = element.text().trim(); val volNum = text.extractVolumeNumber()

            if (volNum != null && (element.isHeading() || element.hasClass("wp-block-heading") || text.matches(Regex("(?i)^\\s*(?:light\\s+novel\\s+)?(?:vol(?:ume)?|book)\\s*\\d+.*$"))) &&
                !text.containsAny("bestseller", "contest", "licensed", "released", "amazon", "published")) {
                currentVolume = "Volume $volNum"; lastUnlinkedChapter = null; lastLinkedChapter = null; continue
            }

            val links = element.select("a[href]")
            // 2. Track unlinked chapter headings (e.g. "Chapter 1", "Extra 1") that precede linked sub-parts ("Part 1", "Part 2")
            if (links.isEmpty()) {
                val chNum = text.extractChapterNumber()
                if (chNum != null) lastUnlinkedChapter = "Chapter ${chNum.formatNum()}"
                else if (text.matches(sectionHeader)) lastUnlinkedChapter = text
                continue
            }

            for (link in links) {
                val href = link.attr("href").trim()
                val rawTitle = link.text().trim().ifEmpty {
                    extractKofiSlug(href) ?: href.cleanUrl().split('/').dropLastWhile { it.toIntOrNull() != null }.lastOrNull().orEmpty()
                }

                // 3. Skip non-chapter links (navigation anchors, comments, archive pages) or empty links
                if (rawTitle.isEmpty() || !isChapterLink(href, rawTitle, baseUrl)) continue

                val rawCh = rawTitle.extractChapterNumber(); val partNum = rawTitle.extractPartNumber() ?: href.extractPartNumber()
                val baseCh = lastUnlinkedChapter ?: lastLinkedChapter
                val isRelativePart = rawTitle.matches(Regex("(?i)^\\s*part[-_\\s]*\\d+.*$"))

                // Check sub-parts & chapter numbers: if rawTitle is a relative part like "part 2", inherit baseCh
                val chNum = if (baseCh != null && partNum != null && (rawCh == null || isRelativePart)) baseCh.extractChapterNumber()
                    else if (rawCh != null && !rawTitle.contains("final chapter", true)) rawCh
                    else if (!rawTitle.isSpecialTitle()) href.extractChapterNumber() ?: rawCh else null

                // 4. Expand structured Ko-fi multi-chapter posts or batch ranges
                if (href.contains("ko-fi.com", true) && partNum == null) {
                    expandKofiLink(href, currentVolume)?.let { kofi ->
                        chapterList.addAll(kofi)
                        kofi.last().name.extractChapterNumber()?.let { num -> lastLinkedChapter = "Chapter ${num.formatNum()}".also { lastUnlinkedChapter = it } }
                        continue
                    }
                    extractChapterRange(rawTitle, href)?.let { range ->
                        val base = extractKofiSlug(href)?.let { slug -> "$mainUrl/$slug" } ?: href
                        range.forEach { ch -> chapterList.add(newChapterData(standardizeChapterTitle("Chapter $ch", currentVolume), base)) }
                        continue
                    }
                }

                // 6. Format chapter title: special titles stay as is, relative parts inherit baseCh, otherwise format with chNum
                val title = when {
                    rawTitle.isSpecialTitle() -> rawTitle
                    baseCh != null && partNum != null && (rawCh == null || isRelativePart) -> "$baseCh - Part $partNum"
                    chNum != null -> "Chapter ${chNum.formatNum()}".also { lastLinkedChapter = it; lastUnlinkedChapter = it }
                        .let { chStr -> if (partNum != null) (if (rawTitle.contains("part", true)) "$chStr - Part $partNum" else rawTitle) else if (!rawTitle.contains("Chapter", true) || rawTitle.contains("final", true)) chStr else rawTitle }
                    else -> rawTitle.also { if (it.matches(sectionHeader)) { lastLinkedChapter = it; lastUnlinkedChapter = it } }
                }

                // 8. Remove duplicate parent placeholder when sub-parts exist
                if (partNum != null) {
                    val parentName = standardizeChapterTitle(chNum?.let { "Chapter ${it.formatNum()}" } ?: baseCh ?: "", currentVolume)
                    if (parentName.isNotEmpty()) {
                        chapterList.removeAll {
                            it.name.extractPartNumber() == null && (
                                (it.url.contains("ko-fi.com", true) && (it.name == parentName || it.name.startsWith("$parentName:") || it.name.startsWith("$parentName "))) ||
                                it.name in listOf("$currentVolume - Final Chapter", "$currentVolume - Another Ending")
                            )
                        }
                    }
                }

                // 9. Add formatted chapter entry to list (converting Ko-fi links to canonical blog URLs)
                val baseSlug = extractKofiSlug(href)
                val chapterUrl = when {
                    baseSlug != null -> "$mainUrl/${if (partNum != null && !baseSlug.contains("part")) "$baseSlug-part-$partNum" else baseSlug}"
                    (href.contains("?p=", true) || href.contains("preview=true", true)) && novelSlug.isNotEmpty() -> {
                        val chTitleSlug = standardizeChapterTitle(title, currentVolume).toSlug()
                        "$href#${if (chTitleSlug.startsWith(novelSlug)) chTitleSlug else "$novelSlug-$chTitleSlug"}"
                    }
                    else -> href
                }
                chapterList.add(newChapterData(standardizeChapterTitle(title, currentVolume), chapterUrl))
            }
        }
        return chapterList
    }

    private fun specialRank(name: String, idx: Int): Double = when {
        name.contains("illust", true) -> -2.0
        name.contains("prologue", true) -> -1.0
        name.containsAny("extra", "side story", "interlude") -> 9980.0 + idx * 0.001
        name.contains("epilogue", true) -> 9990.0
        name.containsAny("episode", "ending", "final") -> 9992.0 + idx * 0.001
        name.contains("afterword", true) -> 9995.0
        else -> 100.0 + idx * 0.01
    }

    // Deterministic chapter sorting: Volume -> Chapter -> Part -> Special entries, filtering parent placeholders when parts exist
    fun sortChapters(chapters: List<ChapterData>): List<ChapterData> {
        val chaptersWithParts = chapters.mapNotNull { ch ->
            val vol = ch.name.extractVolumeNumber() ?: ch.url.extractVolumeNumber() ?: 1
            val cNum = ch.name.extractChapterNumber() ?: ch.url.extractChapterNumber()
            val pNum = ch.name.extractPartNumber() ?: ch.url.extractPartNumber()
            if (cNum != null && pNum != null) vol to cNum else null
        }.toSet()

        return chapters.filterNot { ch ->
            val vol = ch.name.extractVolumeNumber() ?: ch.url.extractVolumeNumber() ?: 1
            val cNum = ch.name.extractChapterNumber() ?: ch.url.extractChapterNumber()
            val pNum = ch.name.extractPartNumber() ?: ch.url.extractPartNumber()
            (cNum != null && pNum == null && (vol to cNum) in chaptersWithParts && ch.url.contains("ko-fi.com", true)) ||
            (ch.name.contains("final chapter", true) && chaptersWithParts.any { it.first == vol && it.second == 7.0 }) ||
            (ch.name.contains("another ending", true) && chaptersWithParts.any { it.first == vol && it.second == 8.0 })
        }.mapIndexed { idx, ch -> ch to idx }.sortedWith(compareBy(
            { (ch, _) -> ch.name.extractVolumeNumber() ?: ch.url.extractVolumeNumber() ?: 1 },
            { (ch, idx) -> ch.name.extractChapterNumber() ?: ch.url.extractChapterNumber() ?: specialRank(ch.name, idx) },
            { (ch, _) -> ch.name.extractPartNumber() ?: ch.url.extractPartNumber() ?: 0 },
            { (_, idx) -> idx }
        )).map { it.first }
    }

    fun Document.extractRawElements(): List<Element> =
        (entryContent()?.children() ?: select("article.post > *, div.entry-content > *"))
            .filterNot { el -> el.tagName() in listOf("header", "footer", "nav", "aside") || el.id() in listOf("comments", "secondary") || el.hasClass("entry-meta") || el.hasClass("entry-header") || el.hasClass("widget-area") || el.hasClass("widget") }
            .distinct()

    // Collect all TOC pages; sortChapters() determines logical order and removes duplicate parent placeholders.
    suspend fun buildTableOfContents(doc: Document, baseUrl: String): List<ChapterData> {
        val canonical = doc.selectFirst("link[rel='canonical']")?.attr("href")?.cleanUrl()
            ?: doc.selectFirst("meta[property='og:url']")?.attr("content")?.cleanUrl()
        val cleanBaseUrl = if (canonical != null && !canonical.endsWith(".net") && !canonical.endsWith(".com")) canonical else baseUrl.cleanUrl()

        val maxPage = doc.select("a[href]").mapNotNull { a ->
            a.attr("href").cleanUrl().takeIf { it.startsWith(cleanBaseUrl) }?.substringAfterLast('/')?.toIntOrNull()
        }.maxOrNull() ?: 1

        val archiveLinks = doc.select("div.entry-content a[href], div#content a[href]").mapNotNull { a ->
            val href = a.attr("href").trim(); val text = a.text().trim().lowercase()
            if (href.contains("web.archive.org/web/", true) && href.contains("gadgetizedpanda", true) &&
                (text.contains("archive") || href.contains("-ln", true)) && !isChapterLink(href, text, cleanBaseUrl)) {
                href
            } else null
        }.distinct()

        val pagesToFetch = (archiveLinks + (maxPage downTo 1).map { if (it == 1) cleanBaseUrl else "$cleanBaseUrl/$it/" }).distinct()
        val allRawElements = pagesToFetch.flatMap { url -> (if (url == cleanBaseUrl) doc else app.get(url).document).extractRawElements() }
        return sortChapters(normalizeChaptersAndParts(allRawElements, cleanBaseUrl.extractNovelSlug(), cleanBaseUrl)).distinctBy { it.name }
    }

    // Extracts the novel synopsis from the main novel details page.
    fun fetchSynopsis(doc: Document): String = buildString {
        var started = false
        for (el in doc.entryContent()?.children().orEmpty()) {
            val text = el.text().trim()
            if (!started && text.contains("Synopsis", true)) {
                started = true
                text.substringAfter("Synopsis", "").trimStart(':', ' ').takeIf(String::isNotEmpty)?.let { appendLine(it).appendLine() }
            } else if (started) {
                if (el.tagName().equals("figure", true) || el.hasClass("wp-block-image") || text.startsWith("Index", true)) break
                if (text.isNotEmpty()) appendLine(text).appendLine()
            }
        }
    }.trim()

    // Loads novel metadata, cover image, synopsis, and full chapter list for details view.
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim()
            ?: doc.selectFirst("title")?.text()?.substringBefore("–")?.substringBefore("-")?.trim()
            ?: throw ErrorLoadingException("Failed to find novel title for $url")
        return newStreamResponse(title, url, buildTableOfContents(doc, url)) {
            posterUrl = doc.selectFirst("figure.wp-block-image img, div.entry-content figure img, div.entry-content img").extractImgSrc()
            synopsis = fetchSynopsis(doc)
        }
    }

    // Loads chapter content from the live site, falling back to archived Wayback Machine snapshots.
    override suspend fun loadHtml(url: String): String? {
        if (!url.contains("ko-fi.com", true) && !url.contains("web.archive.org", true)) {
            val doc = app.get(url).document
            if (!doc.isDeleted404()) fetchChapterContent(doc).takeIf(String::isNotEmpty)?.let { return it }
            doc.select("div.page-content a[href], div.entry-content a[href]")
                .firstOrNull { it.attr("href").contains("web.archive.org", true) }?.attr("href")?.trim()
                ?.takeIf(String::isNotEmpty)?.let { embedded ->
                    return loadHtml(if (url.contains('#') && !embedded.contains('#')) "$embedded#${url.substringAfter('#')}" else embedded)
                }
        }

        val snapshot = (if (url.contains("web.archive.org", true) && !url.contains("/web/*/")) url else resolveSnapshotUrl(url.substringAfter("/web/*/")))
            ?: throw ErrorLoadingException("No archive snapshot found for $url")
        val doc = app.get(snapshot).document
        if (doc.isDomainDown()) throw ErrorLoadingException("Archived snapshot for $url was offline")
        return fetchChapterContent(doc).takeIf(String::isNotEmpty) ?: throw ErrorLoadingException("Failed to load chapter content for $url")
    }
}
