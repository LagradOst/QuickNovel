package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.MainActivity.Companion.app
import java.util.*

class KolNovelProvider : MainAPI() {
    override val name = "KolNovel"
    override val mainUrl = "https://kolnovel.com"
    override val iconId = R.drawable.icon_kolnovel

    override val hasMainPage = true
    override val iconBackgroundId = R.color.kolNovelColor
    override val lang = "ar"

    override val tags = listOf(
        "أكشن" to "action",
        "أصلية" to "original",
        "إثارة" to "excitement",
        "إنتقال الى عالم أخر" to "isekai",
        "إيتشي" to "etchi",
        "بوليسي" to "policy",
        "تقمص شخصيات" to "rpg",
        "جريمة" to "crime",
        "سحر" to "magic",
        "سينن" to "senen",
        "شونين" to "shonen",
        "صيني" to "chinese",
        "غموض" to "mysteries",
        "قوى خارقة" to "superpower",
        "كوري" to "korean",
        "كوميدى" to "comedy",
        "ما بعد الكارثة" to "after-the-disaster",
        "مغامرة" to "adventure",
        "ميكا" to "mechanical",
        "ناض" to "%d9%86%d8%a7%d8%b6",
        "ناضج" to "mature",
        "ياباني" to "japanese",
        "دراما" to "drama",
        "خيالي" to "fantasy",
        "حريم" to "harem",
        "جوسى" to "josei",
        "فنون القتال" to "martial-arts",
        "تاريخي" to "historical",
        "رعب" to "horror",
        "نفسي" to "psychological",
        "رومانسي" to "romantic",
        "حياة مدرسية" to "school-life",
        "الخيال العلمي" to "sci-fi",
        "شريحة من الحياة" to "slice-of-life",
        "خارقة للطبيعة" to "supernatural",
        "مأساوي" to "tragedy",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
    )

    override val orderBys: List<Pair<String, String>>
        get() = listOf(
            "إفتراضي" to "",
            "A-Z" to "title",
            "Z-A" to "titlereverse",
            "أخر التحديثات" to "update",
            "أخر الإضافات" to "latest",
            "رائج" to "popular",
        )


    override val mainCategories: List<Pair<String, String>>
        get() = listOf(
            "الكل" to "",
            "Ongoing" to "ongoing",
            "Hiatus" to "hiatus",
            "Completed" to "completed",
        )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url = "$mainUrl/series/?page=$page&genre[]=$tag&status=$mainCategory&order=$orderBy"
        val document = app.get(url).document
        val headers = document.select("div.bsx")
        val returnValue = headers.mapNotNull { h ->
            val imageHeader = h.selectFirst("a.tip")

            val cUrl = imageHeader?.attr("abs:href") ?: return@mapNotNull null
            val name = imageHeader.select("div.tt span.ntitle").text() ?: return@mapNotNull null
            newSearchResponse(name = name, url = cUrl) {
                posterUrl = fixUrlNull(imageHeader.selectFirst("div.limit img")?.attr("src"))
                rating =
                    (imageHeader.selectFirst("> div.tt > div > div > div.numscore")?.text()
                        ?.toFloat()?.times(100))?.toInt()
                latestChapter = h.selectFirst("a.tip div.tt span.nchapter")?.text()
            }
        }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
        return document.selectFirst("div.entry-content")?.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document

        val headers = document.select("div.bsx")

        val returnValue: ArrayList<SearchResponse> = ArrayList()
        for (h in headers) {
            val head = h.selectFirst("a.tip")

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
        val document = app.get(url).document
        val name = document.select("div.thumb > img").attr("title")
            ?: return null//select("h1.entry-title")?.text()
        val authors = document.select("div.spe span:contains(المؤلف) > a")

        val data: ArrayList<ChapterData> = ArrayList()
        val chapterHeaders = document.select("li[data-id] > a")//.eplister ul
        for (c in chapterHeaders) {
            val cUrl = c.attr("href") ?: continue
            val cName = c.select("div.epl-title").text() + ":" + c.select("div.epl-num").text()
            val added = c.select("div.epl-date").text()
            val views = null
            data.add(ChapterData(cName, cUrl, added, views))
        }
        data.reverse()

        val aHeaders = document.select("span:contains(الحالة)")

        return newStreamResponse(url = url, name = name, data = data) {
            for (a in authors) {
                val atter = a.attr("href")
                if (atter.length > "$mainUrl/writer/".length && atter.startsWith("$mainUrl/writer/")
                ) {
                    author = a.text()
                    break
                }
            }
            tags = document.select("div.genxed a").map { it.text() }
            posterUrl = document.select("div.thumb > img").attr("data-lazy-src")
            synopsis = document.select("div.entry-content p:first-of-type").text()
            setStatus(aHeaders.text().replace("الحالة: ", "").lowercase(Locale.getDefault()))
            rating =
                ((document.selectFirst("div.rating > strong")?.text()?.replace("درجة", "")
                    ?.toFloat()
                    ?: 0f) * 100).toInt()
        }
    }
}
