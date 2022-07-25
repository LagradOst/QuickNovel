package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup

class NovelPassionProvider : MainAPI() {
    override val name: String get() = "Novel Passion"
    override val mainUrl: String get() = "https://www.novelpassion.com"
    override val iconId = R.drawable.big_icon_novelpassion
    override val hasMainPage = true

    override val iconBackgroundId = R.color.novelPassionColor

    override val tags = listOf(
        Pair("All", "all"),
        Pair("Shounen", "shounen"),
        Pair("Harem", "harem"),
        Pair("Comedy", "comedy"),
        Pair("Martial Arts", "martial-arts"),
        Pair("School Life", "school-life"),
        Pair("Mystery", "mystery"),
        Pair("Shoujo", "shoujo"),
        Pair("Romance", "romance"),
        Pair("Sci-fi", "sci-fi"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Mature", "mature"),
        Pair("Fantasy", "fantasy"),
        Pair("Horror", "horror"),
        Pair("Drama", "drama"),
        Pair("Tragedy", "tragedy"),
        Pair("Supernatural", "supernatural"),
        Pair("Ecchi", "ecchi"),
        Pair("Xuanhuan", "xuanhuan"),
        Pair("Adventure", "adventure"),
        Pair("Action", "action"),
        Pair("Psychological", "psychological"),
        Pair("Xianxia", "xianxia"),
        Pair("Wuxia", "wuxia"),
        Pair("Historical", "historical"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Seinen", "seinen"),
        Pair("Lolicon", "lolicon"),
        Pair("Adult", "adult"),
        Pair("Josei", "josei"),
        Pair("Sports", "sports"),
        Pair("Smut", "smut"),
        Pair("Mecha", "mecha"),
        Pair("Yaoi", "yaoi"),
        Pair("Shounen Ai", "shounen-ai"),
    )

    override val orderBys: List<Pair<String, String>>
        get() = listOf(
            Pair("Recently Updated", "1"),
            Pair("New Novel", "2"),
            Pair("Hot Novel", "3"),
            Pair("Top All", "4"),
            Pair("Top Monthly", "5"),
            Pair("Top Weekly", "6"),
            Pair("Top Daily", "7"),
            Pair("Chapter Quantities", "8"),
        )

    override val mainCategories: List<Pair<String, String>>
        get() = listOf(
            Pair("All", "1"),
            Pair("Ongoing", "2"),
            Pair("Completed", "3"),
        )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/category/$tag?p=$page&s=$mainCategory&f=$orderBy"

        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.lh1d5")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val head = h?.selectFirst("> a.c_000")
            val name = head?.attr("title") ?: continue
            val cUrl = head.attr("href") ?: continue
            var posterUrl = head.selectFirst("> i.oh > img")?.attr("src")
            if (posterUrl != null) posterUrl = mainUrl + posterUrl

            val rating =
                (h.selectFirst("> p.g_star_num > small")?.text()!!.toFloat() * 200).toInt()
            val latestChapter = h.selectFirst("> div > div.dab > a")?.attr("title")
            returnValue.add(
                SearchResponse(
                    name,
                    fixUrl(cUrl),
                    posterUrl,
                    rating,
                    latestChapter,
                    this.name,
                )
            ) //ArrayList()
        }
        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        val res = document.selectFirst("div.cha-words")

        return res?.html()
            // FUCK ADS
            ?.replace("( NovelFull )", "")
            ?.replace("NiceNovel.com", "")
            ?.replace("NovelsToday.com", "")
            ?.replace("NovelsToday", "")
            ?.replace("read online free", "")
            ?.replace("NovelWell.com", "")
            ?.textClean // FOR SOME REASON SOME WORDS HAVE DOTS IN THEM
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/search?keyword=$query")

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.lh1d5")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val head = h?.selectFirst("> a.c_000")
            val name = head?.attr("title") ?: continue
            val url = mainUrl + head.attr("href")

            var posterUrl = head.selectFirst("> i.oh > img")?.attr("src")
            if (posterUrl != null) posterUrl = mainUrl + posterUrl

            val rating =
                (h.selectFirst("> p.g_star_num > small")?.text()!!.toFloat() * 200).toInt()
            val latestChapter = h.selectFirst("> div > div.dab > a")?.attr("title")
            returnValue.add(SearchResponse(name, url, posterUrl, rating, latestChapter, this.name))
        }
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.selectFirst("h2.pt4")?.text()!!
        val author = document.selectFirst("a.stq")?.text()!!
        val posterUrl = mainUrl + document.select("i.g_thumb > img").attr("src")
        val tags: ArrayList<String> = ArrayList()

        val rating = (document.select("strong.vam").text()!!.toFloat() * 200).toInt()
        var synopsis = ""
        val synoParts = document.select("div.g_txt_over > div.c_000 > p")
        for (s in synoParts) {
            synopsis += s.text() + "\n\n"
        }

        val infoheader =
            document.select("div.psn > address.lh20 > div.dns") // 0 = Author, 1 = Tags

        val tagsHeader = infoheader[1].select("a.stq")
        for (t in tagsHeader) {
            tags.add(t.text())
        }

        val data: ArrayList<ChapterData> = ArrayList()
        val chapterHeaders = document.select("ol.content-list > li > a")
        for (c in chapterHeaders) {
            val cUrl = mainUrl + c?.attr("href")
            val cName = c.select("span.sp1").text() ?: continue
            val added = c.select("i.sp2").text()
            val views = c.select("i.sp3").text()?.toIntOrNull()
            data.add(ChapterData(cName, cUrl, added, views))
        }
        data.reverse()

        val peopleVotedText = document.selectFirst("small.fs16")?.text()!!
        val peopleVoted = peopleVotedText.substring(1, peopleVotedText.length - 9).toInt()
        val views = document.selectFirst("address > p > span")?.text()?.replace(",", "")?.toInt()

        val statusTxt =
            document.select("div.psn > address.lh20 > p.ell > a") // 0 = Author, 1 = Tags

        var status = 0
        for (s in statusTxt) {
            if (s.hasText()) {
                status = when (s.text()) {
                    "Ongoing" -> STATUS_ONGOING
                    "Completed" -> STATUS_COMPLETE
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
            posterUrl,
            rating,
            peopleVoted,
            views,
            synopsis,
            tags,
            status
        )
    }
}