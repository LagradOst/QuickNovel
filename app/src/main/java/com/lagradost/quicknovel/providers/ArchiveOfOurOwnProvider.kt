package com.lagradost.quicknovel.providers

import android.annotation.SuppressLint
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.mvvm.debugWarning
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class ArchiveOfOurOwnProvider : MainAPI() {
    override val name = "Archive of Our Own"
    override val mainUrl = "https://archiveofourown.org"

    override val hasMainPage = true

    override val rateLimitTime: Long = 500

    override val iconId = R.drawable.ic_archive_of_our_own

    override val iconBackgroundId = R.color.archiveOfOurOwnColor

    override val orderBys = listOf(
        Pair("Latest", "latest"),
    )
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
            "$mainUrl/works"
        if (page > 1) return HeadMainPageResponse(
            url,
            ArrayList()
        ) // Latest ONLY HAS 1 PAGE

        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val works = document.select("li.work")
        if (works.size <= 0) return HeadMainPageResponse(url, ArrayList())

        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in works) {
            val workLink = h?.selectFirst("div.header.module > h4.heading > a")
            if (workLink == null){
                debugWarning { "Ao3 work has no actual work?" }
                continue
            }
            val name = workLink.text()
            val url = workLink.attr("href")

            val authorLink = h?.selectFirst("div.header.module > h4.heading > a[rel=\"author\"]")
            if (authorLink == null){
                debugWarning { "Ao3 work has no actual author?" }
                continue
            }
            val author = authorLink.attr("href")

            //val tags = ArrayList(h.select("span.tags > a").map { t -> t.text() })
            returnValue.add(
                SearchResponse(
                    name,
                    fixUrl(url),
                    fixUrlNull(author),
                    apiName = this.name
                )
            )
            //tags))
        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/works/search?work_search[query]=$query")

        val document = Jsoup.parse(response.text)
        val works = document.select("li.work")
        if (works.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in works) {
            val workLink = h?.selectFirst("div.header.module > h4.heading > a")
            if (workLink == null){
                debugWarning { "Ao3 work has no actual work?" }
                continue
            }
            val name = workLink.text()
            val url = workLink.attr("href")

            val authorLink = h?.selectFirst("div.header.module > h4.heading > a[rel=\"author\"]")
            if (authorLink == null){
                debugWarning { "Ao3 work has no actual author?" }
                continue
            }
            val author = authorLink.attr("href")

            //val tags = ArrayList(h.select("span.tags > a").map { t -> t.text() })
            returnValue.add(
                SearchResponse(
                    name,
                    fixUrl(url),
                    fixUrlNull(author),
                    apiName = this.name
                )
            )
            //tags))
        }
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get("$url/?view_adult=true")

        val document = Jsoup.parse(response.text)

        val name = document.selectFirst("h2.title.heading")?.text().toString()
        val author = document.selectFirst("h3.byline.heading > a[rel=\"author\"]")
        val peopleVoted = document.selectFirst("dd.kudos")?.text()?.replace(",","")?.toInt()
        val views = document.selectFirst("dd.hits")?.text()?.replace(",","")?.toInt()
        val synopsis = document.selectFirst("div.summary.module > blockquote.userstuff")
            ?.children()?.joinToString("\n", transform = Element::text)

        val tags = document.select("a.tag")?.map(org.jsoup.nodes.Element::text)

        val chaptersResponse = app.get("$url/navigate?view_adult=true")
        val chapterDocument = Jsoup.parse(chaptersResponse.text)

        val chapters = chapterDocument.selectFirst("ol.chapter.index.group[role=\"navigation\"]")

        val data = chapters?.children()?.map {
            val link = it.child(0)
            val date = it.child(1)
            ChapterData(
                name = link.text().toString(),
                url = link.attr("href").toString(),
                dateOfRelease = date.text()
            )
        }


        return LoadResponse(
            url,
            name,
            data ?: ArrayList(),
            author = author?.text(),
            posterUrl = fixUrlNull(author?.attr("href")),
            peopleVoted,
            views,
            synopsis = synopsis,
            tags = tags
        )
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div.chapter")?.html()
    }
}
