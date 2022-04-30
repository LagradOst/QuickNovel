package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max


class ReadfromnetProvider : MainAPI() {
    override val name = "ReadFrom.Net"
    override val mainUrl = "https://readfrom.net/"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_wuxiaworldonline

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", "allbooks"),
        Pair("Add-here", "add-here"),
    )

    override fun loadMainPage(page: Int, mainCategory: String?, orderBy: String?, tag: String?): HeadMainPageResponse {
        val url = mainUrl+tag
        val response = khttp.get(url)

        val document = Jsoup.parse(response.text)

        val headers = document.select("div.box_in")
            if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val name = h.selectFirst("h2").text()
            val cUrl = h.selectFirst(" div > h2.title > a ").attr("href")

            val posterUrl = h.selectFirst("div > a.highslide > img").attr("src")

            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrl(posterUrl),
                    null,
                    null,
                    this.name
                )
            )
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override fun loadHtml(url: String): String? {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("#textToRead").html()
    }


    override fun search(query: String): List<SearchResponse> {
        val response =
            khttp.get("https://readfrom.net/build_in_search/?q=$query") // AJAX, MIGHT ADD QUICK SEARCH

        val document = Jsoup.parse(response.text)


        val headers = document.select("div.box_in")
        if (headers.size <= 3) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()

        for (h in headers.take(headers.size-3) ){
            val name = h.selectFirst(" div > h2.title > a > b").text()
            val cUrl = mainUrl.substringBeforeLast("/")+h.selectFirst(" div > h2.title > a ").attr("href")

            val posterUrl = h.selectFirst("div > a.highslide > img").attr("src")

            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrl(posterUrl),
                    null,
                    null,
                    this.name
                )
            )
        }
        return returnValue
    }

    override fun load(url: String): LoadResponse {
        val response = khttp.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst(" h2 ").text().substringBefore(", page").substringBefore("#")

        val author = document.selectFirst("#dle-speedbar > div > div > ul > li:nth-child(3) > a > span").text()

        val posterUrl = document.selectFirst("div.box_in > center:nth-child(1) > div > a > img").attr("src")

        val data: ArrayList<ChapterData> = ArrayList()
        val chapters = document.select("div.splitnewsnavigation2.ignore-select > center > div > a")
        data.add(ChapterData("page 1", url.substringBeforeLast("/")+"/page,1,"+url.substringAfterLast("/"), null, null))
        for (c in 0..(chapters.size/2)) {
            if (chapters[c].attr("href").contains("category").not()) {
                val cUrl = chapters[c].attr("href")
                val cName = "page "+chapters[c].text()
                data.add(ChapterData(cName, cUrl, null, null, null ))
            }
        }
        data.sortWith { first, second ->
            if (first.name.substringAfter(" ") != second.name.substringAfter(" ")) {
                first.name.substringAfter(" ").toInt() - second.name.substringAfter(" ").toInt()
            } else {
                first.name.compareTo(second.name)
            }
        }



        return LoadResponse(
            url,
            name,
            data,
            author,
            fixUrl(posterUrl),
            null,
            null,
            null,
            null,
            null,
            null,
        )
    }
}