package com.lagradost.quicknovel.providers

import android.net.Uri
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.math.roundToInt

class RewayatProviderMainAPI():  MainAPI() {
    override val name = "Rewayat"
    override val mainUrl = "https://rewayat.club"
    private val secondUrl = "https://api.rewayat.club"
    override val iconId = R.drawable.icon_rewayat

    override val hasMainPage = true
    override val iconBackgroundId = R.color.rewayatARlColor
    override val lang = "ar"

    override val orderBys = listOf(
        "عدد الفصول - من أقل لأعلى" to "num_chapters",
        "عدد الفصول - من أعلى لأقل" to "-num_chapters",
        "الاسم - من أقل لأعلى" to "english",
        "الاسم - من أعلى لأقل" to "-english"
    )
    override val mainCategories = listOf(
        "جميع الروايات" to "0",
        "مترجمة" to "1",
        "مؤلفة" to "2",
        "مكتملة" to "3"
    )
    override val tags = listOf(
        "All" to "",
        "كوميديا" to "1",
        "أكشن" to "2",
        "دراما" to "3",
        "فانتازيا" to "4",
        "مهارات القتال" to "5",
        "مغامرة" to "6",
        "رومانسي" to "7",
        "خيال علمي" to "8",
        "الحياة المدرسية" to "9",
        "قوى خارقة" to "10",
        "سحر" to "11",
        "رياضة" to "12",
        "رعب" to "13",
        "حريم" to "14"
    )

    private fun getListOfPosters(document: Document):List<String>{
        val script = document.select("script")
            .firstOrNull { it.data().contains("window.__NUXT__") }
            ?.data()

        val imgRegex = Regex("""poster_url:"(.*?)"""")
        val matches = imgRegex.findAll(script ?: "")

        return matches.map {
            secondUrl + it.groupValues[1].replace("\\u002F", "/")
        }.toList()
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val url = "$mainUrl/library?type=$mainCategory&ordering=$orderBy${if(!tag.isNullOrEmpty()) "&genre=$tag" else ""}&page=$page"
        val document = app.get(url).document

        val imgUrls = getListOfPosters(document)

        var i = 0
        val returnValue = document.select("div.container div.row.row--dense > div").mapNotNull { div ->
            val title = div.selectFirst("div[role=listitem] div.v-list-item__content div.v-list-item__title")?.text()?.trim() ?: return@mapNotNull null
            val url = div.selectFirst("a")?.attr("href") ?: return@mapNotNull null


            newSearchResponse(
                name = title,
                url = url
            ) {
                posterUrl = imgUrls.getOrNull(i)
                i++
            }

        }
        return HeadMainPageResponse(url, returnValue)
    }


    private suspend fun getChapters(document: Document, url: String):List<ChapterData>{
        val newUrl = "$secondUrl/api/chapters/${url.substringAfterLast("/")}/?ordering=number&page="
        val chapter = app.get(newUrl + 1).parsed<RewayatMainResponse>()
        val totalChapters = chapter.count
            if (totalChapters > 1) {
                    return (0..< totalChapters).map { chapterNumber ->
                        val chapterUrl = "$newUrl-------$chapterNumber-------$totalChapters"
                        newChapterData("Chapter ${chapterNumber + 1}", chapterUrl)
                    }

            }

            return document.select("div.v-window-item.v-window-item--active > div[role=list] > div > a").mapIndexedNotNull { index, li ->
                val name = li.selectFirst("div.v-list-item__content")?.text() ?: "Chapter $index"
                val url = fixUrl(li.attr("href"))
                newChapterData(name, url)
            }
    }



    override suspend fun load(url: String): LoadResponse
    {
        val document = app.get(url).document
        val infoDiv = document.select("div.container")


        // Extract title
        val title = infoDiv.selectFirst("h1 > span")?.text() ?: ""

        // Extract description/synopsis
        val synopsis = infoDiv.selectFirst("div.text-body-2.font-weight-medium.font-dense.font-cairo.mb-6.text-pre-line span")?.text() ?: ""

        val chapters = getChapters(document, url)
        val imgUrls = getListOfPosters(document)[0]
        return newStreamResponse(title,fixUrl(url), chapters) {
            this.posterUrl = imgUrls
            this.synopsis = synopsis
        }
    }


    private fun getChapterParagraphs(doc: Document):List<String>?{
        val script = doc.select("script")
            .firstOrNull { it.data().contains("window.__NUXT__") }
            ?.data()
        val contentRaw = Regex("""\.content\s*=\s*"(.*?)";""")
            .find(script ?: "")
            ?.groupValues?.get(1)
        val html = contentRaw
            ?.replace("\\n","")
            ?.replace("\\u003C", "<")
            ?.replace("\\u003E", ">")
            ?.replace("\\u002F", "/")
            ?: return null

        val soup = Jsoup.parse(html)
            .select("p")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
        return soup
    }

    override suspend fun loadHtml(url: String): String? {
        //[url, chapter number]
        val chapterData = url.split("-------")
        if(chapterData.size == 1){
            val dc = app.get(url).document
            val title =  dc.selectFirst("div.v-card__subtitle.headerClassRTL.font-weight-medium.font-cairo")?.outerHtml()?:""
            val contentElement = getChapterParagraphs(dc)?:return null
            return title + contentElement.joinToString("</br>")
        }

        val baseUrl = chapterData[0]
        val chapterBigIndex = chapterData[1].toInt()
        val totalChapters = chapterData[2].toInt()
        val itemsPerPage = 24

        val remainder = totalChapters % itemsPerPage
        val offset = if(remainder == 0) 0 else itemsPerPage - remainder
        val adjustedIndex = chapterBigIndex + offset

        val normalPage = if(remainder > chapterBigIndex) (adjustedIndex / itemsPerPage) + 1 else (chapterBigIndex / itemsPerPage) + 1
        val chapterIndex = chapterBigIndex % itemsPerPage

        val document = app.get("$baseUrl$normalPage").parsed<RewayatMainResponse>()

        val chapter = document.results.getOrNull(chapterIndex) ?: return null
        val dc = app.get("$mainUrl/novel/${url.substringAfterLast("chapters/").substringBefore("/?ordering")}/${chapter.number}").document
        val title =  dc.selectFirst("h1 a")?.outerHtml()?:""
        val contentElement = getChapterParagraphs(dc)?:return null
        return title +"</br>"+ contentElement.joinToString("</br>")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/novels/search/all/?search=${Uri.encode(query)}"
        val document = app.get(url).parsed<RewayatSearchResponse>()
        return document.results.map { element ->
            val title = element.english
            val novelUrl = fixUrl("/novel/"+element.slug)
            val coverUrl = secondUrl + element.posterUrl
            newSearchResponse(title, novelUrl){
                posterUrl = coverUrl
            }
        }
    }


    data class RewayatSearchResponse(
        val count: Long,
        val next: String?,
        val previous: Any?,
        val results: List<Result>,
    )

    data class Result(
        val arabic: String,
        val english: String,
        val slug: String,
        @JsonProperty("poster_url")
        val posterUrl: String,
        @JsonProperty("num_chapters")
        val numChapters: Long,
        val id: Long,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class RewayatMainResponse(
        val count: Long,
        val results: List<Result2>,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Result2(
        val number: Long,
        val title: String,
    )
}