package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import org.jsoup.Jsoup
import java.util.*
import kotlin.collections.ArrayList

class KolNovelProvider : MainAPI() {
    override val name = "KolNovel"
    override val mainUrl = "https://kolnovel.com"
    override val iconId = R.drawable.icon_kolnovel

    override val hasMainPage = true
    override val iconBackgroundId = R.color.kolNovelColor
    override val lang = "ar"

    override val tags = listOf(
        Pair("أكشن", "action"),
        Pair("أصلية", "original"),
        Pair("إثارة", "excitement"),
        Pair("إنتقال الى عالم أخر", "isekai"),
        Pair("إيتشي", "etchi"),
        Pair("بوليسي", "policy"),
        Pair("تقمص شخصيات", "rpg"),
        Pair("جريمة", "crime"),
        Pair("سحر", "magic"),
        Pair("سينن", "senen"),
        Pair("شونين", "shonen"),
        Pair("صيني", "chinese"),
        Pair("غموض", "mysteries"),
        Pair("قوى خارقة", "superpower"),
        Pair("كوري", "korean"),
        Pair("كوميدى", "comedy"),
        Pair("ما بعد الكارثة", "after-the-disaster"),
        Pair("مغامرة", "adventure"),
        Pair("ميكا", "mechanical"),
        Pair("ناض", "%d9%86%d8%a7%d8%b6"),
        Pair("ناضج", "mature"),
        Pair("ياباني", "japanese"),
        Pair("دراما", "drama"),
        Pair("خيالي", "fantasy"),
        Pair("حريم", "harem"),
        Pair("جوسى", "josei"),
        Pair("فنون القتال", "martial-arts"),
        Pair("تاريخي", "historical"),
        Pair("رعب", "horror"),
        Pair("نفسي", "psychological"),
        Pair("رومانسي", "romantic"),
        Pair("حياة مدرسية", "school-life"),
        Pair("الخيال العلمي", "sci-fi"),
        Pair("شريحة من الحياة", "slice-of-life"),
        Pair("خارقة للطبيعة", "supernatural"),
        Pair("مأساوي", "tragedy"),
        Pair("Wuxia", "wuxia"),
        Pair("Xianxia", "xianxia"),
        Pair("Xuanhuan", "xuanhuan"),
    )

    override val orderBys: List<Pair<String, String>>
        get() = listOf(
            Pair("إفتراضي", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("أخر التحديثات", "update"),
            Pair("أخر الإضافات", "latest"),
            Pair("رائج", "popular"),
        )


    override val mainCategories: List<Pair<String, String>>
        get() = listOf(
            Pair("الكل", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Hiatus", "hiatus"),
            Pair("Completed", "completed"),
        )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url = "$mainUrl/series/?page=$page&genre[]=$tag&status=$mainCategory&order=$orderBy"

        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.bsx")
        if (headers.size <= 0) return HeadMainPageResponse(url, ArrayList())

        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val imageHeader = h?.selectFirst("a.tip")

            val cUrl = imageHeader?.attr("abs:href") ?: continue
            val posterUrl = imageHeader.selectFirst("div.limit img")?.attr("src")
            val name = imageHeader.select("div.tt span.ntitle").text() ?: continue
            val rating =
                (imageHeader.selectFirst("> div.tt > div > div > div.numscore")?.text()
                    ?.toFloat()?.times(100))?.toInt()
            val latestChap = h.selectFirst("a.tip div.tt span.nchapter")?.text()
            returnValue.add(SearchResponse(name, cUrl, posterUrl, rating, latestChap, this.name))
        }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = app.get(url)
        val document = Jsoup.parse(response.text)
        return document.selectFirst("div.entry-content")?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/?s=$query")

        val document = Jsoup.parse(response.text)
        val headers = document.select("div.bsx")
        if (headers.size <= 0) return ArrayList()
        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val head = h?.selectFirst("a.tip")

            val url = head?.attr("abs:href") ?: continue

            val posterUrl = h.select("div.limit img").attr("src")

            val meta = h.selectFirst("a.tip")

            val name = meta?.select("div.tt span.ntitle")?.text() ?: continue

            val ratingTxt = meta.selectFirst("div.tt div.rt div.rating div.numscore")?.text()

            val rating = if (ratingTxt != null) {
                (ratingTxt.toFloat() * 100).toInt()
            } else {
                null
            }

            val latestChapter = meta.selectFirst("div.tt span.nchapter")?.text()
            returnValue.add(SearchResponse(name, url, posterUrl, rating, latestChapter, this.name))
        }
        return returnValue
    }

    override suspend fun load(url: String): LoadResponse? {
        val response = app.get(url)

        val document = Jsoup.parse(response.text)
        val name = document.select("div.thumb > img").attr("title")
            ?: return null//select("h1.entry-title")?.text()
        val authors = document.select("div.spe span:contains(المؤلف) > a")
        var author = ""
        for (a in authors) {
            val atter = a?.attr("href")
            if ((atter?.length
                    ?: continue) > "$mainUrl/writer/".length && atter.startsWith("$mainUrl/writer/")
            ) {
                author = a.text()
                break
            }
        }

        val posterUrl = document.select("div.thumb > img").attr("data-lazy-src")

        val tags: ArrayList<String> = ArrayList()
        val tagsHeader = document.select("div.genxed a")
        for (t in tagsHeader) {
            tags.add(t.text())
        }

        val synopsis = document.select("div.entry-content p:first-of-type").text()

        val data: ArrayList<ChapterData> = ArrayList()
        val chapterHeaders = document.select("li[data-id] > a")//.eplister ul
        for (c in chapterHeaders) {
            val cUrl = c?.attr("href") ?: continue
            val cName = c.select("div.epl-title").text() + ":" + c.select("div.epl-num").text()
            val added = c.select("div.epl-date").text()
            val views = null
            data.add(ChapterData(cName, cUrl, added, views))
        }
        data.reverse()

        val rating =
            ((document.selectFirst("div.rating > strong")?.text()?.replace("درجة", "")?.toFloat()
                ?: 0f) * 100).toInt()
        val peopleVoted = null

        val views = null

        val aHeaders = document.select("span:contains(الحالة)")

        val status =
            when (aHeaders.text().replace("الحالة: ", "").lowercase(Locale.getDefault())) {
                "ongoing" -> STATUS_ONGOING
                "completed" -> STATUS_COMPLETE
                else -> STATUS_NULL
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
