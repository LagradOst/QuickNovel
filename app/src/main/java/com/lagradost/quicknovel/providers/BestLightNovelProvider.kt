package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.STATUS_COMPLETE
import com.lagradost.quicknovel.STATUS_NULL
import com.lagradost.quicknovel.STATUS_ONGOING
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.textClean

class BestLightNovelProvider : MainAPI() {
    override val name = "BestLightNovel"
    override val mainUrl = "https://bestlightnovel.com"

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        val res = document.selectFirst("div.vung_doc")
        return res?.html().textClean?.replace("[Updated from F r e e w e b n o v e l. c o m]", "")
            ?.replace(
                "Find authorized novels in Webnovel，faster updates, better experience，Please click for visiting. ",
                ""
            )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search_novels/${query.replace(' ', '_')}").document

        val headers = document.select("div.danh_sach > div.list_category")
        return headers.mapNotNull {
            val head = it.selectFirst("> a")
            val name = head?.attr("title") ?: return@mapNotNull null
            val url = head.attr("href") ?: return@mapNotNull null

            newSearchResponse(name = name, url = url) {
                latestChapter = it.selectFirst("> a.chapter")?.text()
                posterUrl = fixUrlNull(head.selectFirst("> img")?.attr("src"))
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val infoHeaders = document.select("ul.truyen_info_right > li")

        val name = infoHeaders[0].selectFirst("> h1")?.text() ?: return null

        val chapterHeaders = document.select("div.chapter-list > div").mapNotNull {
            val spans = it.select("> span")
            val text = spans[0].selectFirst("> a")
            val cUrl = text?.attr("href") ?: return@mapNotNull null
            val cName = text.text() ?: return@mapNotNull null
            newChapterData(name = cName, url = cUrl) {
                dateOfRelease = spans[1].text()
            }
        }.reversed()

        return newStreamResponse(url = url, name = name, data = chapterHeaders) {
            for (a in infoHeaders[1].select("> a")) {
                val href = a?.attr("href")
                if (a.hasText() && (href?.length
                        ?: continue) > "$mainUrl/search_author/".length && href.startsWith("$mainUrl/search_author/")
                ) {
                    author = a.text()
                    break
                }
            }
            posterUrl = document.select("span.info_image > img").attr("src")
            tags = infoHeaders[2].select("> a").map { it.text() }
            synopsis = document.select("div.entry-header > div")[1].text().textClean
            status =
                when (infoHeaders[3].selectFirst("> a")?.text()?.lowercase()) {
                    "ongoing" -> STATUS_ONGOING
                    "completed" -> STATUS_COMPLETE
                    else -> STATUS_NULL
                }
            views = infoHeaders[6].text()
                .replace(",", "")
                .replace("\"", "").substring("View : ".length).toInt()
            try {
                val ratingHeader = infoHeaders[9].selectFirst("> em > em")?.select("> em")
                rating = (ratingHeader?.get(1)?.selectFirst("> em > em")?.text()?.toFloat()
                    ?.times(200))?.toInt() ?: 0

                peopleVoted = ratingHeader?.get(2)?.text()?.replace(",", "")?.toInt() ?: 0
            } catch (_: Throwable) {
            }
        }
    }
}