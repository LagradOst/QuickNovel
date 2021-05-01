package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import org.jsoup.Jsoup
import java.lang.Exception

class RoyalRoadProvider : MainAPI() {
    override val name: String get() = "Royal Road"
    override val mainUrl: String get() = "https://www.royalroad.com"

    override val hasMainPage: Boolean
        get() = true

    //, "Ongoing", "Complete", "Popular this week", "Latest Updates", "New Releases", "Trending"
    override val mainCategories: ArrayList<Pair<String, String>>
        get() = arrayListOf(
            Pair("Best Rated", "best-rated"),
            Pair("Ongoing", "active-popular"),
            Pair("Complete", "complete"),
            Pair("Popular this week", "weekly-popular"),
            Pair("Latest Updates", "latest-updates"),
            Pair("New Releases", "new-releases"),
            Pair("Trending", "trending"),
        )
    override val tags: ArrayList<Pair<String, String>>
        get() = arrayListOf(
            Pair("All", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Contemporary", "contemporary"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("Satire", "satire"),
            Pair("Sci-fi", "sci_fi"),
            Pair("Short Story", "one_shot"),
            Pair("Tragedy", "tragedy")
        )

    override fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): ArrayList<MainPageResponse>? {
        val url = "$mainUrl/fictions/$mainCategory?page=$page${if (tag == null || tag == "") "" else "&genre=$tag"}"
        if (page > 1 && mainCategory == "trending") return ArrayList()

        try {
            val response = khttp.get(url)

            val document = Jsoup.parse(response.text)
            val headers = document.select("div.fiction-list-item")
            if (headers.size <= 0) return ArrayList()

            val returnValue: ArrayList<MainPageResponse> = ArrayList()
            for (h in headers) {
                val head = h.selectFirst("> div")
                val hInfo = head.selectFirst("> h2.fiction-title > a")

                val name = hInfo.text()
                val url = mainUrl + hInfo.attr("href")

                var posterUrl = h.selectFirst("> figure > a > img").attr("src")
                if (posterUrl.startsWith('/')) {
                    posterUrl = mainUrl + posterUrl
                }

                val rating = try {
                    val ratingHead =
                        head.selectFirst("> div.stats").select("> div")[1].selectFirst("> span").attr("title")
                    (ratingHead.toFloat() * 200).toInt()
                } catch (e: Exception) {
                    null
                }

                val latestChapter = try {
                    if(mainCategory == "latest-updates") {
                        head.selectFirst("> ul.list-unstyled > li.list-item > a > span").text()
                    }
                    else {
                        h.select("div.stats > div.col-sm-6 > span")[4].text()
                    }
                } catch (e: Exception) {
                    null
                }

                val tags = ArrayList(h.select("span.tags > a").map { t -> t.text() })
                returnValue.add(MainPageResponse(name, url, posterUrl, rating, latestChapter, this.name, tags))
            }
            return returnValue
        } catch (e: Exception) {
            return null
        }
    }


    override fun search(query: String): ArrayList<SearchResponse>? {
        try {
            val response = khttp.get("https://www.royalroad.com/fictions/search?title=$query")

            val document = Jsoup.parse(response.text)
            val headers = document.select("div.fiction-list-item")
            if (headers.size <= 0) return ArrayList()
            val returnValue: ArrayList<SearchResponse> = ArrayList()
            for (h in headers) {
                val head = h.selectFirst("> div.search-content")
                val hInfo = head.selectFirst("> h2.fiction-title > a")

                val name = hInfo.text()
                val url = mainUrl + hInfo.attr("href")

                var posterUrl = h.selectFirst("> figure.text-center > a > img").attr("src")
                if (posterUrl.startsWith('/')) {
                    posterUrl = mainUrl + posterUrl
                }

                val ratingHead = head.selectFirst("> div.stats").select("> div")[1].selectFirst("> span").attr("title")
                val rating = (ratingHead.toFloat() * 200).toInt()
                val latestChapter = h.select("div.stats > div.col-sm-6 > span")[4].text()
                returnValue.add(SearchResponse(name, url, posterUrl, rating, latestChapter, this.name))
            }
            return returnValue
        } catch (e: Exception) {
            return null
        }
    }

    override fun load(url: String): LoadResponse? {
        try {
            val response = khttp.get(url)

            val document = Jsoup.parse(response.text)

            val ratingAttr = document.selectFirst("span.font-red-sunglo").attr("data-content")
            val rating = (ratingAttr.substring(0, ratingAttr.indexOf('/')).toFloat() * 200).toInt()
            val name = document.selectFirst("h1.font-white").text()
            val author = document.selectFirst("h4.font-white > span > a").text()
            val tagsDoc = document.select("span.tags > a")
            val tags: ArrayList<String> = ArrayList()
            for (t in tagsDoc) {
                tags.add(t.text())
            }

            var synopsis = ""
            val synoParts = document.select("div.description > div > p")
            for (s in synoParts) {
                synopsis += s.text()!! + "\n\n"
            }

            val data: ArrayList<ChapterData> = ArrayList()
            val chapterHeaders = document.select("div.portlet-body > table > tbody > tr")
            for (c in chapterHeaders) {
                var url = c.attr("data-url")
                if (url.startsWith('/')) {
                    url = mainUrl + url
                }
                val td = c.select("> td") // 0 = Name, 1 = Upload
                val name = td[0].selectFirst("> a").text()
                val added = td[1].selectFirst("> a > time").text()
                val views = null
                data.add(ChapterData(name, url, added, views))
            }
            var posterUrl = document.selectFirst("div.fic-header > div > img").attr("src")
            if (posterUrl.startsWith('/')) {
                posterUrl = mainUrl + posterUrl
            }

            val hStates = document.select("ul.list-unstyled")[1]
            val stats = hStates.select("> li")
            val views = stats[1].text().replace(",", "").replace(".", "").toInt()
            val peopleRatedHeader = document.select("div.stats-content > div > meta")
            val peopleRated = peopleRatedHeader[2].attr("content").toInt()

            val statusTxt = document.select("div.col-md-8 > div.margin-bottom-10 > span.label")

            var status = 0
            for (s in statusTxt) {
                if (s.hasText()) {
                    status = when (s.text()) {
                        "ONGOING" -> 1
                        "COMPLETED" -> 2
                        "HIATUS" -> 3
                        else -> 0
                    }
                    if (status > 0) break
                }
            }

            return LoadResponse(name, data, author, posterUrl, rating, peopleRated, views, synopsis, tags, status)
        } catch (e: Exception) {
            return null
        }
    }

    override fun loadPage(url: String): String? {
        return try {
            val response = khttp.get(url)
            val document = Jsoup.parse(response.text)
            document.selectFirst("div.chapter-content").html()
        } catch (e: Exception) {
            null
        }
    }
}