package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.toRate
import com.lagradost.quicknovel.util.AppUtils.parseJson


class WtrLabProvider : MainAPI() {
    override val hasMainPage = false
    override val lang = "en"
    override val hasReviews = false
    override val mainUrl = "https://wtr-lab.com"
    override val name = "WTR-LAB"
    override val usesCloudFlareKiller = false

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/en/novel-finder?text=${query.replace(" ", "+")}"
        val doc = app.get(url).document
        return doc.select(".series-list>div>div>.serie-item").mapNotNull { select ->
            val titleWrap = select.selectFirst(".title-wrap") ?: return@mapNotNull null
            val titleHolder = titleWrap.selectFirst("a.title") ?: return@mapNotNull null
            val href = titleHolder.attr("href") ?: return@mapNotNull null
            titleHolder.selectFirst(".rawtitle")?.remove()

            val name = titleHolder.text() ?: return@mapNotNull null
            newSearchResponse(name, href) {
                posterUrl = select.selectFirst("a > img")?.attr("src")
            }
        }
    }


    private suspend fun getChapterRange(
        url: String,
        chaptersJson: ResultJsonResponse.Root,
        start: Long,
        end: Long
    ): List<ChapterData> {
        val chapterDataUrl =
            "$mainUrl/api/chapters/${chaptersJson.props.pageProps.serie.serieData.rawId}?start=$start&end=$end"
        println(chapterDataUrl)
        val chaptersDataJson =
            app.get(chapterDataUrl).text
        val chaptersData = parseJson<ResultChaptersJsonResponse.Root>(chaptersDataJson)

        return chaptersData.chapters.map { chapter ->
            newChapterData(
                "#${chapter.order} ${chapter.title}",
                "${url.trimEnd('/')}/chapter-${chapter.order}"
            ) {
                dateOfRelease = chapter.updatedAt
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val titleWrap =
            doc.selectFirst(".title-wrap") ?: throw ErrorLoadingException("No title wrapping")
        val title =
            titleWrap.selectFirst(".text-uppercase")?.text()
                ?: throw ErrorLoadingException("No title")
        val jsonNode = doc.selectFirst("#__NEXT_DATA__")
        val json = jsonNode?.data() ?: throw ErrorLoadingException("no chapters")

        val chaptersJson = parseJson<ResultJsonResponse.Root>(json)


        val chapters = mutableListOf<ChapterData>()
        chapters.addAll(
            getChapterRange(
                url,
                chaptersJson,
                1,
                chaptersJson.props.pageProps.serie.serieData.rawChapterCount
            )
        )

        /*
        val chunks = chaptersJson.props.pageProps.serie.serieData.rawChapterCount / 250
        val tail = chaptersJson.props.pageProps.serie.serieData.rawChapterCount % 250
        for (chunk in 0 until chunks) {
            chapters.addAll(getChapterRange(url, chaptersJson, chunk * 250 + 1, chunk * 250 + 250))
        }
        if (tail > 0) {
            chapters.addAll(
                getChapterRange(
                    url,
                    chaptersJson,
                    chunks * 250 + 1,
                    chunks * 250 + tail
                )
            )
        }*/

        /*doc.select(".toc-list > .chapter-item").map { select ->
            val href = select.attr("href") ?: throw ErrorLoadingException("No href on $select")
            val chapterTitle = select.selectFirst("span")?.text() ?: select.text()
            newChapterData(chapterTitle, href)
        }*/
        return newStreamResponse(title, url, chapters) {
            synopsis = doc.selectFirst(".desc-wrap")?.text()
            posterUrl = doc.selectFirst(".image-wrap > img")?.attr("src")
            views =
                doc.select(".detail-line").find { it.text().contains("Views") }?.text()?.split(" ")
                    ?.getOrNull(0)?.toIntOrNull()
            // author = doc.select(".author-wrap>a").text()
            rating = doc.selectFirst(".rating-text")?.text()?.toRate(5)
        }
    }

    override suspend fun loadHtml(url: String): String {
        val doc = app.get(url).document

        val jsonNode = doc.selectFirst("#__NEXT_DATA__")
        val json = jsonNode?.data() ?: throw ErrorLoadingException("no chapters")
        val chaptersJson = parseJson<LoadJsonResponse.Root>(json)
        val text = StringBuilder()
        val chapter = chaptersJson.props.pageProps.serie.chapter

        val root = app.post(
            "$mainUrl/api/reader/get", data = mapOf(
                "chapter_no" to chapter.slug,
                "language" to "en",
                "raw_id" to chapter.rawId.toString(),
                "retry" to "false",
                "translate" to "web", // translate=ai just returns a job and I am too lazy to fix that
            )
        ).parsed<LoadJsonResponse2.Root>()

        for (body in root.data.data.body) {
            if (!body.contains("window._taboola")) {
                text.append("<p>")
                text.append(body)
                text.append("</p>")
            }
        }
        /*for (select in doc.select(".chapter-body>p")) {
            if (select.ownText().contains("window._taboola")) {
                select.remove()
            }
        }*/

        return text.toString()//doc.selectFirst(".chapter-body")?.html()
    }
}

object ResultChaptersJsonResponse {
    data class Root(
        val chapters: List<Chapter>,
    )

    data class Chapter(
        @JsonProperty("serie_id")
        val serieId: Long,
        val id: Long,
        val order: Long,
        val title: String,
        val name: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
    )
}

object ResultJsonResponse {
    data class Root(
        val props: Props,
        /*val page: String,
        val query: Query,
        val buildId: String,
        val isFallback: Boolean,
        val isExperimentalCompile: Boolean,
        val gssp: Boolean,
        val locale: String,
        val locales: List<String>,
        val defaultLocale: String,
        val scriptLoader: List<Any?>,*/
    )

    data class Props(
        val pageProps: PageProps,
        // @JsonProperty("__N_SSP")
        /// val nSsp: Boolean,
    )

    data class PageProps(
        val serie: Serie,
        /*val tags: List<Tag>,
        @JsonProperty("server_time")
        val serverTime: String,
        @JsonProperty("disabe_ads")
        val disabeAds: Boolean,
        @JsonProperty("_sentryTraceData")
        val sentryTraceData: String,
        @JsonProperty("_sentryBaggage")
        val sentryBaggage: String,*/
    )

    data class Serie(
        @JsonProperty("serie_data")
        val serieData: SerieData,
        /*val ranks: Ranks,
        val recommendation: List<Recommendation>,
        val raws: List<Raw3>,
        val names: List<Name>,
        @JsonProperty("other_series")
        val otherSeries: List<Series>,
        @JsonProperty("last_chapters")
        val lastChapters: List<LastChapter>,*/
        /*@JsonProperty("raw_rank")
        val rawRank: Any?,
        @JsonProperty("released_user")
        val releasedUser: Any?,*/
    )

    data class SerieData(
        @JsonProperty("raw_id")
        val rawId: Long,
        /*
        val id: Long,val slug: String,
        @JsonProperty("search_text")
        val searchText: String,
        val status: Long,
        val data: Data,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        val view: Long,
        @JsonProperty("in_library")
        val inLibrary: Long,
        val rating: Any?,
        @JsonProperty("chapter_count")
        val chapterCount: Long,
        val power: Long,
        @JsonProperty("total_rate")
        val totalRate: Long,
        @JsonProperty("user_status")
        val userStatus: Long,
        val verified: Boolean,
        val from: String,
        val author: String,
        @JsonProperty("ai_enabled")
        val aiEnabled: Boolean,
        @JsonProperty("released_by")
        val releasedBy: Any?,
        @JsonProperty("raw_status")
        val rawStatus: Long,*/
        @JsonProperty("raw_chapter_count")
        val rawChapterCount: Long,
        /*
        val genres: List<Long>,
        @JsonProperty("raw_verified")
        val rawVerified: Boolean,
        @JsonProperty("requested_by")
        val requestedBy: String,
        @JsonProperty("requested_by_name")
        val requestedByName: String,
        @JsonProperty("requested_member")
        val requestedMember: String,
        @JsonProperty("requested_role")
        val requestedRole: Long,*/
    )

    data class Data(
        val title: String,
        val author: String,
        val description: String,
        @JsonProperty("from_user")
        val fromUser: String?,
        val raw: Raw,
        val image: String,
    )

    data class Raw(
        val title: String,
        val author: String,
        val description: String,
    )

    /*data class Ranks(
        val week: Any?,
        val month: Any?,
        val all: String,
    )

    data class Recommendation(
        @JsonProperty("serie_id")
        val serieId: Long,
        @JsonProperty("recommendation_id")
        val recommendationId: Long,
        val score: Long,
        val id: Long,
        val slug: String,
        @JsonProperty("search_text")
        val searchText: String,
        val status: Long,
        val data: Data2,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        val view: Long,
        @JsonProperty("in_library")
        val inLibrary: Long,
        val rating: Double?,
        @JsonProperty("chapter_count")
        val chapterCount: Long,
        val power: Long,
        @JsonProperty("total_rate")
        val totalRate: Long,
        @JsonProperty("user_status")
        val userStatus: Long,
        val verified: Boolean,
        val from: String?,
        val author: String,
        @JsonProperty("raw_id")
        val rawId: Long,
        @JsonProperty("ai_enabled")
        val aiEnabled: Boolean,
    )

    data class Data2(
        val title: String,
        val author: String,
        val description: String,
        @JsonProperty("from_user")
        val fromUser: String?,
        val raw: Raw2,
        val image: String,
    )

    data class Raw2(
        val title: String,
        val author: String,
        val description: String,
    )

    data class Raw3(
        val id: Long,
        @JsonProperty("chapter_count")
        val chapterCount: Long,
        val view: Long,
        val slug: String,
        @JsonProperty("created_at")
        val createdAt: String,
        val default: Boolean,
        val verified: Boolean,
    )

    data class Name(
        val title: String,
        @JsonProperty("raw_title")
        val rawTitle: String,
    )

    data class Series(
        val id: Long,
        val slug: String,
        @JsonProperty("search_text")
        val searchText: String,
        val status: Long,
        val data: Data3,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        val view: Long,
        @JsonProperty("in_library")
        val inLibrary: Long,
        val rating: Double,
        @JsonProperty("chapter_count")
        val chapterCount: Long,
        val power: Long,
        @JsonProperty("total_rate")
        val totalRate: Long,
        @JsonProperty("user_status")
        val userStatus: Long,
        val verified: Boolean,
        val from: String,
        val author: String,
        @JsonProperty("raw_id")
        val rawId: Long,
    )

    data class Data3(
        val title: String,
        val author: String,
        val description: String,
        @JsonProperty("from_user")
        val fromUser: String,
        val raw: Raw4,
        val image: String,
    )

    data class Raw4(
        val title: String,
        val author: String,
        val description: String,
    )

    data class LastChapter(
        //@JsonProperty("serie_id")
        //val serieId: Long,
        //val id: Long,
        val order: Long,
        val title: String,
        //val name: String,
        @JsonProperty("updated_at")
        val updatedAt: String?,
    )

    data class Tag(
        val id: Long,
        val title: String,
        val slug: String,
    )

    data class Query(
        val sid: String,
        @JsonProperty("serie_slug")
        val serieSlug: String,
    )*/
}

object LoadJsonResponse2 {

    data class Root(
        // val success: Boolean,
        // val chapter: Chapter,
        val data: Data,
    )

    data class Chapter(
        val id: Long,
        @JsonProperty("raw_id")
        val rawId: Long,
        val order: Long,
        val title: String,
    )

    data class Data(
        /*@JsonProperty("raw_id")
        val rawId: Long,
        @JsonProperty("chapter_id")
        val chapterId: Long,
        val status: Long,
        */
        val data: Data2,
        /*@JsonProperty("created_at")
        val createdAt: String,
        val language: String,*/
    )

    data class Data2(
        val body: List<String> = emptyList(),
        /*val hans: String,
        val hash: String,
        val model: String,
        val patch: Any?,
        val title: String,
        val prompt: String,
        @JsonProperty("glossory_hash")
        val glossoryHash: String,
        @JsonProperty("glossary_build")
        val glossaryBuild: Long,*/
    )

}

object LoadJsonResponse {
    data class Root(
        val props: Props,
        /*val page: String,
        val query: Query,
        val buildId: String,
        val isFallback: Boolean,
        val isExperimentalCompile: Boolean,
        val gssp: Boolean,
        val locale: String,
        val locales: List<String>,
        val defaultLocale: String,
        val scriptLoader: List<Any?>,*/
    )

    data class Props(
        val pageProps: PageProps,
        /*@JsonProperty("__N_SSP")
        val nSsp: Boolean,
        */
    )

    data class PageProps(
        val serie: Serie,
        /*@JsonProperty("disabe_ads")
        val disabeAds: Boolean,
        @JsonProperty("server_time")
        val serverTime: String,
        @JsonProperty("active_service")
        val activeService: ActiveService,
        @JsonProperty("_sentryTraceData")
        val sentryTraceData: String,
        @JsonProperty("_sentryBaggage")
        val sentryBaggage: String,*/
    )

    data class Serie(
        /*@JsonProperty("serie_data")
        val serieData: SerieData,
        @JsonProperty("default_service")
        val defaultService: String,*/
        val chapter: Chapter,
    )

    data class SerieData(
        val id: Long,
        val slug: String,
        val data: Data,
        @JsonProperty("raw_id")
        val rawId: Long,
        @JsonProperty("user_status")
        val userStatus: Long,
        @JsonProperty("is_default")
        val isDefault: Boolean,
        @JsonProperty("chapter_count")
        val chapterCount: Long,
        @JsonProperty("ai_enabled")
        val aiEnabled: Boolean,
        @JsonProperty("raw_status")
        val rawStatus: Long,
    )

    data class Data(
        val title: String,
        val author: String,
        val description: String,
        @JsonProperty("from_user")
        val fromUser: String,
        val raw: Raw,
        val image: String,
    )

    data class Raw(
        val title: String,
        val author: String,
        val description: String,
    )

    data class Chapter(
        val id: Long,
        val slug: String,
        @JsonProperty("raw_id")
        val rawId: Long,
        /*@JsonProperty("serie_id")
        val serieId: Long,
        val status: Long,
        val slug: String,
        val name: String,
        val order: Long,
        @JsonProperty("is_update")
        val isUpdate: Boolean,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        val title: String,
        val code: String,*/
    )

    data class ActiveService(
        val id: String,
        val label: String,
    )

    data class Query(
        val sid: String,
        @JsonProperty("serie_slug")
        val serieSlug: String,
        @JsonProperty("chapter_no")
        val chapterNo: String,
    )

}