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
                    posterUrl = it.image
                }
            }
        return HeadMainPageResponse(mainUrl, returnValue)
    }


    suspend fun getChapters(document: Document): List<ChapterData>? {

        val scriptContent = document.select("script")
            .firstOrNull { it.html().contains("lnmtl.volumes") }
            ?.html()
            ?: return null

        val volumesRaw = scriptContent
            .substringAfter("lnmtl.volumes = ")
            .substringBefore(";lnmtl.route")

        val volumesArray = parseJson<List<Vol>>(volumesRaw)

        val firstResponseRaw = scriptContent
            .substringAfter("lnmtl.firstResponse = ")
            .substringBefore(";lnmtl.volumes")

        val firstResponse =
            parseJson<VolChapterResponse>(firstResponseRaw)

        val firstChapter =
            firstResponse.data.firstOrNull()
                ?: return null

        val firstChapterNumber =
            firstChapter.slug
                .substringAfterLast("-")
                .toIntOrNull()
                ?: return null


        if (volumesArray.isEmpty()) return null

        val lastVolume = volumesArray.maxByOrNull { it.number } ?: return null
        val lastVolumeId = lastVolume.id

        var volUrl = "$mainUrl/chapter?volumeId=$lastVolumeId"

        var volResponse = app.get(volUrl)
            .parsed<VolChapterResponse>()

        val lastPage = volResponse.lastPage

        if (lastPage > 1) {
            volUrl =
                "$mainUrl/chapter?page=$lastPage&volumeId=$lastVolumeId"

            volResponse = app.get(volUrl)
                .parsed()
        }

        val lastChapter =
            volResponse.data.lastOrNull()
                ?: return null

        val lastChapterNumber =
            lastChapter.slug
                .substringAfterLast("-")
                .toIntOrNull()
                ?: return null

        val slug =
            lastChapter.slug
                .substringBeforeLast("-chapter-")

        return (firstChapterNumber..lastChapterNumber).map { chapterNumber ->

            val chapterUrl =
                "$mainUrl/chapter/${slug}-chapter-$chapterNumber"

            newChapterData(
                "Chapter $chapterNumber",
                chapterUrl
            )
        }
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
                    posterUrl = it.image
                }
            }
    }

    data class VolChapterResponse(
        @JsonProperty("last_page")
        val lastPage: Int,
        @JsonProperty("data")
        val data: List<LNMTLChapterData>
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
