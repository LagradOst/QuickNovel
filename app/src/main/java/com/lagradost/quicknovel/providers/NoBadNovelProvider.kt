package com.lagradost.quicknovel.providers

import android.net.Uri
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.nodes.Element

class NoBadNovelProvider : MainAPI() {
    override val name = "NoBadNovel"
    override val mainUrl = "https://www.nobadnovel.com"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_nobadnovel

    private fun parseCard(card: Element): SearchResponse? {
        val titleEl = card.selectFirst("h4 a") ?: return null
        val title = titleEl.text().trim().takeIf { it.isNotBlank() } ?: return null
        val href = card.selectFirst("a[href*=/series/]")?.attr("href") ?: return null

        return newSearchResponse(name = title, url = href) {
            posterUrl = fixUrlNull(card.selectFirst("img[src]")?.attr("src"))
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url = "$mainUrl/series/page/$page"
        val document = app.get(url).document
        val items = document.select(".grid > div").mapNotNull { parseCard(it) }

        return HeadMainPageResponse(url, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/series?keyword=${Uri.encode(query, "UTF-8")}"
        val document = app.get(url).document
        return document.select(".grid > div").mapNotNull { parseCard(it) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null

        val chapters = document.select(".chapter-list a[href], .chapters-list a[href]").mapNotNull { a ->
            val chUrl = a.attr("href").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val chName = a.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            newChapterData(name = chName, url = chUrl)
        }

        return newStreamResponse(name = title, url = url, data = chapters) {
            this.posterUrl = fixUrlNull(
                document.selectFirst("img[src*=cdn.nobadnovel], div.thumb img, .series-thumb img, img.object-cover")?.attr("src")
            )

            this.synopsis = document.selectFirst("#intro .content, .description, .summary-content, .novel-description")?.text()?.trim()

            this.author = document.selectFirst("span.author, .info-item a[href*=author], body > div > main > div.bg-base-100.sm\\:bg-base-200.py-4.astro-HWRQY3JX > div > div.mx-4.sm\\:mx-0.sm\\:flex.relative.astro-HWRQY3JX > div > div.sm\\:grow.astro-HWRQY3JX > div.mt-3.sm\\:mt-4.text-sm.md\\:text-base.astro-HWRQY3JX > spna")?.text()?.trim()
            this.tags = document.select(".genres a, .tags a, a[href*='/genre/']").mapNotNull {
                it.text().trim().takeIf { text -> text.isNotEmpty() }
            }

            setStatus(document.selectFirst("span.status, .info-item:contains(Status) span, .badge, .status-value")?.text())
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = response.document

        document.select("script, style, .ads, .adblock-service, .social-share, .prev-next").remove()

        val chapterEl = document.selectFirst("div.text-base.sm\\:text-lg")
            ?: document.selectFirst("div.chapter-content")
            ?: document.selectFirst("article div.content")
            ?: document.selectFirst("div#chapter-content")

        return chapterEl?.html()?.takeIf { it.isNotBlank() }
    }
}