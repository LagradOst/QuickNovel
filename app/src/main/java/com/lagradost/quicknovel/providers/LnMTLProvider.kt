package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.util.AppUtils.parseJson
import org.jsoup.nodes.Document

class LnMTLProvider : MainAPI() {

    override val name = "LnMTL"
    override val mainUrl = "https://lnmtl.com"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_lnmtl
    override val iconBackgroundId = R.color.white
    private var allNovels: Array<NovelInfo> = emptyArray()

    private suspend fun prefetchAllNovels(){
        if(allNovels.isEmpty()){
            val home = app.get(mainUrl).text

            val path = Regex("prefetch: '/(.*?\\.json)'")
                .find(home)
                ?.groupValues
                ?.get(1)

            allNovels =
                app.get("$mainUrl/$path")
                    .parsed<Array<NovelInfo>>()
                    .apply { shuffle() }
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        prefetchAllNovels()

        val returnValue = allNovels.map {
                newSearchResponse(it.name, it.url) {
                    posterUrl = it.image.replace("40.","200.")
                }
            }
        return HeadMainPageResponse(mainUrl, returnValue)
    }


    suspend fun getChapters(document: Document): List<ChapterData>? {
        //All data, vols and first chapters
        val scriptContent = document.select("script")
            .firstOrNull { it.html().contains("lnmtl.volumes") }
            ?.html()
            ?: return null

        //all vols id
        val volumesRaw = scriptContent
            .substringAfter("lnmtl.volumes = ")
            .substringBefore(";lnmtl.route")
        val volumesArray = parseJson<List<Vol>>(volumesRaw).sortedBy { it.number }
        if (volumesArray.isEmpty()) return null

        //first vol
        val firstResponseRaw = scriptContent
            .substringAfter("lnmtl.firstResponse = ")
            .substringBefore(";lnmtl.volumes")
        val firstResponse = parseJson<VolChapterResponse>(firstResponseRaw)

        val allChapters = mutableListOf<ChapterData>()

        for ((index, vol) in volumesArray.withIndex()) {
            //get all vols info. only first page
            val response = if (index == 0) {
                firstResponse
            } else {
                val volUrl = "$mainUrl/chapter?page=1&volumeId=${vol.id}"
                app.get(volUrl).parsed<VolChapterResponse>()
            }

            val chaptersInVol = response.data
            if (chaptersInVol.isNotEmpty()){
                val firstChapter = chaptersInVol.first()
                val totalChaptersInVol = response.total

                val slugBase = firstChapter.slug.substringBeforeLast("-") + "-"
                firstChapter.slug.substringAfterLast("-").toIntOrNull() ?.let{ firstNum ->
                    for (i in 0 until totalChaptersInVol) {
                        val currentNum = firstNum + i
                        val chapterTitle = "Vol ${vol.number} Ch $currentNum"

                        val actualChapter = chaptersInVol.getOrNull(i)

                        allChapters.add(
                            newChapterData(
                                actualChapter?.title ?: chapterTitle,
                                "$mainUrl/chapter/$slugBase$currentNum"
                            )
                        )
                    }
                }

            }

        }

        return if (allChapters.isEmpty()) null else allChapters
    }


    override suspend fun load(url: String): LoadResponse? {

        val document = app.get(url).document

        val name = document.selectFirst("span.novel-name")?.text()
            ?: document.selectFirst(".novel-name")?.text()
            ?: return null

        val cover = fixUrlNull(
            document.selectFirst(".media-left img")?.attr("src")
                ?: document.selectFirst("img.img-rounded")?.attr("src")
        )

        val novelSynopsis =
            document.selectFirst(".description p")?.text()
                ?: document.selectFirst(".synopsis")?.text()

        val chapters = getChapters(document)

        return newStreamResponse(
            name,
            url,
            chapters ?: emptyList()
        ) {
            posterUrl = cover
            synopsis = novelSynopsis
        }
    }


    override suspend fun loadHtml(url: String): String {
        val html = app.get(url).document
        return html.select("sentence.translated")
            .joinToString("<br>") { it.html() }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        prefetchAllNovels()
        return allNovels
            .filter {
                it.name.contains(query, ignoreCase = true)
            }
            .map {
                newSearchResponse(it.name, it.url) {
                    posterUrl = it.image.replace(  "40.","200.")
                }
            }
    }

    data class VolChapterResponse(
        @JsonProperty("last_page")
        val lastPage: Int,
        @JsonProperty("data")
        val data: List<LNMTLChapterData>,
        @JsonProperty("total")
        val total:Int,
    )

    data class LNMTLChapterData(

        @JsonProperty("title")
        val title: String,

        @JsonProperty("slug")
        val slug: String,

        @JsonProperty("created_at")
        val createdAt: String,

        @JsonProperty("site_url")
        val url: String,
    )

    data class Vol(

        @JsonProperty("id")
        val id: Long,

        @JsonProperty("novel_id")
        val novelId: Long,

        @JsonProperty("number")
        val number: Long,
    )

    data class NovelInfo(

        @JsonProperty("id")
        val id: Long,

        @JsonProperty("slug")
        val slug: String,

        @JsonProperty("name")
        val name: String,

        @JsonProperty("image")
        val image: String,
        @JsonProperty("url")
        val url: String,
    )
}
