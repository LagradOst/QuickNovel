package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup

import android.util.Log


open class HiraethTranslationProvider : MainAPI() {
    override val name = "HiraethTranslation"
    override val mainUrl = "https://hiraethtranslation.com"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_hiraethtranslation
    override val iconBackgroundId = R.color.hiraethtranslation_header_color

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/page/$page/?s&post_type=wp-manga&m_orderby=latest"
        val document = app.get(url).document
        val headers = document.select("div.c-tabs-item > div.row.c-tabs-item__content")
        val returnValue =
            headers.mapNotNull { h ->
                val h3 = h.selectFirst("h3.h4 > a") ?: return@mapNotNull null
                newSearchResponse(name = h3.text(), url = h3.attr("href") ?: return@mapNotNull null) {
                    posterUrl = fixUrlNull(h.selectFirst("div.c-image-hover > a > img")?.attr("src"))
                    latestChapter = h.select("div.latest-chap > span.chapter > a").text()
                }
            }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document =
            Jsoup.parse(
                response.text
                    .replace(
                        "\uD835\uDCF5\uD835\uDC8A\uD835\uDC83\uD835\uDE67\uD835\uDE5A\uD835\uDC82\uD835\uDCED.\uD835\uDCEC\uD835\uDE64\uD835\uDE62",
                        "",
                        true
                    )
                    .replace("libread.com", "", true)
            )
        return document.selectFirst("div.text-left")?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query&post_type=wp-manga&m_orderby=latest").document

        return document.select("div.c-tabs-item > div.row.c-tabs-item__content").mapNotNull { h ->
            val h3 = h.selectFirst("h3.h4 > a") ?: return@mapNotNull null

            newSearchResponse(
                name = h3.text() ?: return@mapNotNull null,
                url = h3.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(h.selectFirst("div.c-image-hover > a > img")?.attr("src"))
                latestChapter = h.select("div.latest-chap > span.chapter > a").text()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val trimmed = url.trim().removeSuffix("/")
        val response = app.get(url)
        val document = response.document
        val name = document.selectFirst("div.post-title > h1")?.text() ?: return null

//        val aid = "[0-9]+s.jpg".toRegex().find(response.text)?.value?.substringBefore("s")
//        val chaptersDataphp = app.post("$mainUrl/api/chapterlist.php", data = mapOf("aid" to aid!!))
//        val chaptersDataphp = document.select("div.c-page__content > div")
        val data1 = document.select(
                "ul.main.version-chap > li.wp-manga-chapter.free-chap > a"
            )
                .reversed()
                .mapNotNull { c ->
                    val href = c.attr("href") ?: return@mapNotNull null
                    val cName = c.text()
                    newChapterData(name = cName, url = href)
                }

         val data2 = document.select(
                "ul.sub-chap-list > li.wp-manga-chapter.free-chap > a"
            )
                .reversed()
                .mapNotNull { c ->
                    val href = c.attr("href") ?: return@mapNotNull null
                    val cName = c.text()
                    newChapterData(name = cName, url = href)
                }

        val  data = data1 + data2

        val prefix = trimmed

        return newStreamResponse(url = url, name = name, data = data) {
            author = document.selectFirst("div.author-content > a")?.text()
            tags =
                document.selectFirst("div.tags-content")
                    ?.text()
                    ?.splitToSequence(", ")
                    ?.toList()
            posterUrl = fixUrlNull(document.select("div.summary_image > a > img.img-responsive").attr("src"))
            Log.d("myTag2", document.select("div.summary_image > a > img.img-responsive").attr("src") ?: "no data");

            synopsis = document.selectFirst("div.summary__content")?.text()
            val votes = document.select("div.summary-content.vote-details")
            if (votes != null) {
                rating = votes.select("span#averagerate").text().toFloat().times(200).toInt()
                peopleVoted = votes.select("span#countrate").text().toInt()
            }
            val statusHeader0 = document.selectFirst("div.summary-content")
            val statusHeader = document.selectFirst("div.summary-content")

            setStatus(statusHeader?.text() ?: statusHeader0?.text())
        }
    }
}