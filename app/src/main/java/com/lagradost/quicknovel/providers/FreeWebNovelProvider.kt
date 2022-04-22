package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import java.util.*
import kotlin.collections.ArrayList

class FreeWebNovelProvider : MainAPI() {
    override val name = "FreeWebNovel"
    override val mainUrl = "https://freewebnovel.com/"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_wuxiaworldonline

    override val iconBackgroundId = R.color.wuxiaWorldOnlineColor

    override val tags = listOf(
        Pair("All", ""),
        Pair("Chinese", "Chinese"),
        Pair("Action", "Action"),
        Pair("Adventure", "Adventure"),
        Pair("Fantasy", "Fantasy"),
        Pair("Martial Arts", "Martial Arts"),
        Pair("Romance", "Romance"),
        Pair("Xianxia", "Xianxia"),
        //Pair("Editor's choice", "Editor's choice"),
        Pair("Original", "Original"),
        Pair("Korean", "Korean"),
        Pair("Comedy", "Comedy"),
        Pair("Japanese", "Japanese"),
        Pair("Xuanhuan", "Xuanhuan"),
        Pair("Mystery", "Mystery"),
        Pair("Supernatural", "Supernatural"),
        Pair("Drama", "Drama"),
        Pair("Historical", "Historical"),
        Pair("Horror", "Horror"),
        Pair("Sci-Fi", "Sci-Fi"),
        Pair("Thriller", "Thriller"),
        Pair("Futuristic", "Futuristic"),
        Pair("Academy", "Academy"),
        Pair("Completed", "Completed"),
        Pair("Harem", "Harem"),
        Pair("School Life", "Schoollife"),
        Pair("Martial Arts", "Martialarts"),
        Pair("Slice of Life", "Sliceoflife"),
        Pair("English", "English"),
        Pair("Reincarnation", "Reincarnation"),
        Pair("Psychological", "Psychological"),
        Pair("Sci-fi", "Scifi"),
        Pair("Mature", "Mature"),
        Pair("Ghosts", "Ghosts"),
        Pair("Demons", "Demons"),
        Pair("Gods", "Gods"),
        Pair("Cultivation", "Cultivation"),
    )

    override fun loadMainPage(page: Int, mainCategory: String?, orderBy: String?, tag: String?): HeadMainPageResponse {
        val url = mainUrl+"genre/$tag"
        val response = khttp.get(url)

        val document = Jsoup.parse(response.text)

        val headers = document.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h.selectFirst("h3.tit > a")
            val cUrl = mainUrl.substringBeforeLast("/")+h3.attr("href")

            val name = h3.attr("title")
            val posterUrl = h.selectFirst("div.pic > a > img").attr("src")

            val latestChap = h.select("div.item")[2].selectFirst("> div > a").text()
            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrl(posterUrl),
                    null,
                    latestChap,
                    this.name
                )
            )
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override fun loadHtml(url: String): String? {
        val response = khttp.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div.txt").html()
    }


    override fun search(query: String): List<SearchResponse> {
        val response = khttp.post(
            mainUrl+"search/",
            headers = mapOf(
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*",
                "user-agent" to USER_AGENT
            ),
            data = mapOf("searchkey" to query)
        )
        val document = Jsoup.parse(response.text)


        val headers = document.select("div.li-row")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val h3 = h.selectFirst("h3.tit > a")
            val cUrl = mainUrl.substringBeforeLast("/")+h3.attr("href")

            val name = h3.attr("title")
            val posterUrl = h.selectFirst("div.pic > a > img").attr("src")

            val latestChap = h.select("div.item")[2].selectFirst("> div > a").text()
            returnValue.add(
                SearchResponse(
                    name,
                    cUrl,
                    fixUrl(posterUrl),
                    null,
                    latestChap,
                    this.name
                )
            )
        }
        return returnValue
    }

    override fun load(url: String): LoadResponse {
        val response = khttp.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("h1.tit").text()
        val informations = document.select("div.txt")
        fun getInfoHeader(tag: String): Elements? {
            for (a in informations) {
                val sel = a.selectFirst("> div.right >a")
                if (sel != null && sel.attr("href").contains(tag)) return a.select("> div.right >a")
            }
            return null
        }
        val auth = getInfoHeader("author")
        var author: String? = null

        if (auth != null) {
            for (a in auth){
                author += a.select("> div.right >a").text()+" "
            }
        }

        val posterUrl = document.select(" div.pic > img").attr("src")

        val tags: ArrayList<String> = ArrayList()

        val gen = getInfoHeader("genre")
        if (gen != null) {
            val tagsHeader = gen.select("> div.right > a")
            for (t in tagsHeader) {
                tags.add(t.text())
            }
        }

        val synopsis = document.selectFirst("div.inner").text()

        val data: ArrayList<ChapterData> = ArrayList()
        val chapternumber0 = document.select("div.m-newest1 > ul.ul-list5 > li")[1]
        val chapternumber1 = chapternumber0.selectFirst("a").attr("href")
        val chapternumber = chapternumber1.substringAfterLast("-").filter{ it.isDigit() }.toInt()


        for (c in 1..chapternumber) {

            val cUrl = url.substringBeforeLast(".")+"/chapter-$c.html"
            val cName = "chapter $c"
            data.add(ChapterData(cName, cUrl, null, null))
        }





        val statusHeader0 = document.selectFirst("span.s1.s2")
        val statusHeader = document.selectFirst("span.s1.s3")

        val status = if (statusHeader != null) {when (statusHeader.selectFirst("> a").text()
            .toLowerCase(Locale.getDefault())) {
            "OnGoing" -> STATUS_ONGOING
            "Completed" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }}
        else
        {when (statusHeader0.selectFirst("> a").text()
            .toLowerCase(Locale.getDefault())) {
            "OnGoing" -> STATUS_ONGOING
            "Completed" -> STATUS_COMPLETE
            else -> STATUS_NULL
        }}

        return LoadResponse(
            url,
            name,
            data,
            author,
            fixUrl(posterUrl),
            null,
            null,
            null,
            synopsis,
            tags,
            status
        )
    }
}