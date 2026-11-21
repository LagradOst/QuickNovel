package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.DownloadExtractLink
import com.lagradost.quicknovel.DownloadLink
import com.lagradost.quicknovel.DownloadLinkType
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.network.utils.CookiesUtils
import com.lagradost.quicknovel.newEpubResponse
import com.lagradost.quicknovel.newSearchResponse
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object AnnasArchive : MainAPI() {
    override val lang = "en"
    override val name = "Annas Archive"
    override val mainUrl = "https://annas-archive.gl"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_annasarchive
    override val iconBackgroundId = R.color.white
    override val hasReviews = false
    override val usesCloudFlareKiller = true

    //open val searchTags = "lang=en&content=book_fiction&ext=epub&sort=&"
    override val tags = listOf(
        "Fiction" to "book_fiction",
        "Non-fiction" to "book_nonfiction",
        "Unknown" to "book_unknown",
        "Magazine" to "magazine",
        "Comic" to "book_comic",
        "Standards" to "standards_document",
    )
    override val mainCategories = listOf(
        "English" to "en",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Italian" to "it",
        "Portuguese" to "pt",
        "Chinese" to "zh",
        "Japanese" to "ja",
    )
    override val orderBys = listOf(
        "Z-Library" to "zlib",
        "Libgen (li)" to "lgli",
        "Libgen (rs)" to "lgrs",
        "Internet Archive" to "ia",
        "HathiTrust" to "hathi",
        "Uploads" to "upload",
        "Duxiu" to "duxiu",
        "Sci-Hub" to "scihub",
        "MagzDB" to "magzdb"
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val langParam = if (!mainCategory.isNullOrBlank()) "&lang=$mainCategory" else ""
        val contentParam = if (!tag.isNullOrBlank()) "&content=$tag" else ""
        val srcParam = if (!orderBy.isNullOrBlank()) "&src=$orderBy" else ""
        val url = "$mainUrl/search?index=&page=$page&sort=$langParam$contentParam$srcParam&ext=epub"

        val document = app.get(
            url,
            headers = cloudflareHeaders(waitSelector = "div.js-aarecord-list-outer")
        ).document

        val returnValue = document.select("div.js-aarecord-list-outer > div").mapNotNull { node ->
            val a = node.selectFirst("a.line-clamp-\\[3\\]")
            val href = fixUrlNull(a?.attr("href")) ?: return@mapNotNull null
            val title = a?.text() ?: return@mapNotNull null
            newSearchResponse(
                name = title,
                url = href
            ) {
                posterUrl = fixUrlNull(node.selectFirst("img")?.attr("src"))
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url =
            "$mainUrl/search?index=&page=1&sort=&ext=epub&display=&q=${query.replace(" ", "+")}"
        val text = app.get(url).text.replace(
            Regex("<!--([\\W\\w]*?)-->")
        ) { it.groupValues[1] }

        val document = Jsoup.parse(text)

        val results = document.select("div.js-aarecord-list-outer div.flex.pt-3.pb-3.border-b")

        return results.mapNotNull { element ->
            val link = element.selectFirst("a")?.attr("href") ?: ""
            if (!link.startsWith("/md5/")) {
                println("Skipping non-md5 link: $link")
                return@mapNotNull null
            }
            val title = element
                .selectFirst("div.max-w-full.overflow-hidden.flex.flex-col.justify-around a")
                ?.text()
            if (title == null) {
                return@mapNotNull null
            }
            newSearchResponse(
                name = title,
                url = fixUrlNull(link) ?: return@mapNotNull null
            ) {
                posterUrl = element.selectFirst("div img")?.attr("src")
            }
        }
    }


    private fun extract(url: String, name: String, force: Boolean = false): DownloadLinkType {
        return if (url.contains(".epub") || force) {
            DownloadLink(
                url = url,
                name = name,
                kbPerSec = 2
            )
        } else {
            DownloadExtractLink(
                url = url,
                name = name
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        var slowLink: String? = null

        return newEpubResponse(
            name = document.selectFirst("div.text-2xl")?.ownText() ?: throw ErrorLoadingException("No title found"),
            url = url,
            links = document.select("ul.mb-4 > li").mapNotNull { element ->
                val link = fixUrlNull(element.selectFirst("a.js-download-link")?.attr("href"))
                    ?: return@mapNotNull null

                if (link.contains("fast_download")) return@mapNotNull null

                if (element.text().contains("no waitlist")) {
                    if (slowLink == null) {
                        slowLink = getSlowLinkWithWebView(link)
                    }
                    return@mapNotNull if (slowLink != null) extract(
                        slowLink,
                        element.text(),
                        true
                    ) else null
                }

                if (link.endsWith("/datasets")) return@mapNotNull null
                extract(link, element.text())
            }) {
            posterUrl =
                document.selectFirst("main > div > div > div > div > div > img")?.attr("src")
            author = document.selectFirst("main > div > div > a")?.ownText()
            synopsis = document.selectFirst("main > div > div > div > div.mb-1")?.text()
        }
    }

    private suspend fun getSlowLinkWithWebView(url: String): String? {
        return app.get(
            url,
            headers = cloudflareHeaders(waitSelector = "main > div > p.mb-4.text-xs > span > span")
        ).document.selectFirst("main > div > p.mb-4.text-xs > span > span")?.text()
    }
}
