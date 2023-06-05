package com.lagradost.quicknovel.providers

import android.annotation.SuppressLint
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class ArchiveOfOurOwnProvider : MainAPI() {
    override val name = "Archive of Our Own"
    override val mainUrl = "https://archiveofourown.org/"

    override val hasMainPage = true

    override val iconId = R.drawable.ic_archive_of_our_own

    override val iconBackgroundId = R.color.royalRoadColor
        
    val searchFilters = listOf(
        Pair("Best Match", "_score"),
        Pair("Author", "authors_to_sort_on"),
        Pair("Title", "title_to_sort_on"),
        Pair("Date Posted", "created_at"),
        Pair("Date Updated", "revised_at"),
        Pair("Word Count", "word_count"),
        Pair("Hits", "hits"),
        Pair("Kudos", "kudos_count"),
        Pair("Comments", "comments_count"),
        Pair("Bookmarks", "bookmarks_count")

    )
    override val tags = listOf(
        "Creator Chose Not To Use Archive Warnings",
        "Graphic Depictions Of Violence",
        "Major Character Death",
        "Rape/Non-con",
        "Underage",
        "Creator Chose Not To Use Archive Warnings"
    ).map { Pair(it, java.net.URLEncoder.encode(it.replace("/","*s*"), "utf-8")) }

    
    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url =
            "$mainUrl/fictions/$orderBy?page=$page${if (tag == null || tag == "") "" else "&genre=$tag"}"
        if (page > 1 && orderBy == "trending") return HeadMainPageResponse(
            url,
            ArrayList()
        ) // TRENDING ONLY HAS 1 PAGE

        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.fiction-list-item")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())

        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val head = h?.selectFirst("> div")
            val hInfo = head?.selectFirst("> h2.fiction-title > a")

            val name = hInfo?.text() ?: continue
            val cUrl = mainUrl + hInfo.attr("href")

            val posterUrl = h.selectFirst("> figure > a > img")?.attr("src")

            val rating =
                head.selectFirst("> div.stats")?.select("> div")?.get(1)?.selectFirst("> span")
                    ?.attr("title")?.toFloatOrNull()?.times(200)?.toInt()


            val latestChapter = try {
                if (orderBy == "latest-updates") {
                    head.selectFirst("> ul.list-unstyled > li.list-item > a > span")?.text()
                } else {
                    h.select("div.stats > div.col-sm-6 > span")[4].text()
                }
            } catch (e: Exception) {
                null
            }

            //val tags = ArrayList(h.select("span.tags > a").map { t -> t.text() })
            returnValue.add(
                SearchResponse(
                    name,
                    fixUrl(cUrl),
                    fixUrlNull(posterUrl),
                    rating,
                    latestChapter,
                    this.name
                )
            )
            //tags))
        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/fictions/search?title=$query")

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.fiction-list-item")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val head = h?.selectFirst("> div.search-content")
            val hInfo = head?.selectFirst("> h2.fiction-title > a")

            val name = hInfo?.text() ?: continue
            val url = mainUrl + hInfo.attr("href")

            val posterUrl = h.selectFirst("> figure.text-center > a > img")?.attr("src")

            val rating =
                head.selectFirst("> div.stats")?.select("> div")?.get(1)?.selectFirst("> span")
                    ?.attr("title")?.toFloatOrNull()?.times(200)?.toInt()
            val latestChapter = h.select("div.stats > div.col-sm-6 > span")[4].text()
            returnValue.add(
                SearchResponse(
                    name,
                    url,
                    fixUrlNull(posterUrl),
                    rating,
                    latestChapter,
                    this.name
                )
            )
        }
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)

        val ratingAttr = document.selectFirst("span.font-red-sunglo")?.attr("data-content")
        val rating =
            (ratingAttr?.substring(0, ratingAttr.indexOf('/'))?.toFloat()?.times(200))?.toInt()
        val name = document.selectFirst("h1.font-white")?.text() ?: return null
        val author = document.selectFirst("h4.font-white > span > a")?.text()
        val tagsDoc = document.select("span.tags > a")
        val tags: ArrayList<String> = ArrayList()
        for (t in tagsDoc) {
            tags.add(t.text())
        }

        var synopsis = ""
        val synoDescript = document.select("div.description > div")
        val synoParts = synoDescript.select("> p")
        if (synoParts.size == 0 && synoDescript.hasText()) {
            synopsis = synoDescript.text().replace("\n", "\n\n") // JUST IN CASE
        } else {
            for (s in synoParts) {
                synopsis += s.text() + "\n\n"
            }
        }

        val data: ArrayList<ChapterData> = ArrayList()
        val chapterHeaders = document.select("div.portlet-body > table > tbody > tr")
        for (c in chapterHeaders) {
            val cUrl = c?.attr("data-url") ?: continue
            val td = c.select("> td") // 0 = Name, 1 = Upload
            val cName = td[0].selectFirst("> a")?.text() ?: continue
            val added = td[1].selectFirst("> a > time")?.text()
            val views = null
            data.add(ChapterData(cName, fixUrl(cUrl), added, views))
        }
        val posterUrl =
            document.selectFirst("div.fic-header > div > .cover-art-container > img")?.attr("src")

        val hStates = document.select("ul.list-unstyled")[1]
        val stats = hStates.select("> li")
        val views = stats[1]?.text()?.replace(",", "")?.replace(".", "")?.toInt()
        //val peopleRatedHeader = document.select("div.stats-content > div > ul > li")
        //val peopleRated = peopleRatedHeader[1]?.attr("data-content")?.takeWhile { c -> c != '/' }?.toIntOrNull()

        val statusTxt = document.select("div.col-md-8 > div.margin-bottom-10 > span.label")

        var status = 0
        for (s in statusTxt) {
            if (s.hasText()) {
                status = when (s.text()) {
                    "ONGOING" -> STATUS_ONGOING
                    "COMPLETED" -> STATUS_COMPLETE
                    "HIATUS" -> STATUS_PAUSE
                    "STUB" -> STATUS_DROPPED
                    "DROPPED" -> STATUS_DROPPED
                    else -> STATUS_NULL
                }
                if (status > 0) break
            }
        }

        return LoadResponse(
            url,
            name,
            data,
            author,
            fixUrlNull(posterUrl),
            rating,
            null,//peopleRated,
            views,
            synopsis,
            tags,
            status
        )
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div.chapter-content")?.html()
    }
}