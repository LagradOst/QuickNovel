package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.toRate


class WtrLabProvider : MainAPI() {
    private val mapper: ObjectMapper = jacksonObjectMapper().configure(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        false
    )

    override val hasMainPage = false
    override val lang = "en"
    override val hasReviews = false
    override val mainUrl = "https://wtr-lab.com"
    override val name = "WTR-LAB"
    override val usesCloudFlareKiller = false
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/en/novel-finder?text=${query.replace(" ", "+")}"
        val doc = app.get(url).document
        return doc.select(".series-list>.serie-item").mapNotNull { select ->
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


    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val titleWrap =
            doc.selectFirst(".title-wrap") ?: throw ErrorLoadingException("No title wrapping")
        val title =
            titleWrap.selectFirst(".long-title")?.text() ?: throw ErrorLoadingException("No title")
        val jsonNode = doc.selectFirst("#__NEXT_DATA__")
        val json = jsonNode?.data() ?: throw ErrorLoadingException("no chapters")
        val chaptersJson = mapper.readValue<ResultJsonResponse.RootJson>(json)

        val chapters = chaptersJson.props.pageProps.serie.chapters.map { chapter ->
            newChapterData("#${chapter.order} ${chapter.title}" , "${url.trimEnd('/')}/chapter-${chapter.order}") {
                dateOfRelease = chapter.updatedAt
            }
        }
        /*doc.select(".toc-list > .chapter-item").map { select ->
            val href = select.attr("href") ?: throw ErrorLoadingException("No href on $select")
            val chapterTitle = select.selectFirst("span")?.text() ?: select.text()
            newChapterData(chapterTitle, href)
        }*/
        return newStreamResponse(title, url, chapters) {
            synopsis = doc.selectFirst(".lead")?.text()
            posterUrl = doc.selectFirst(".img-wrap > img")?.attr("src")
            views =
                doc.select(".detail-item").find { it.text().contains("Views") }?.text()?.split(" ")
                    ?.getOrNull(0)?.toIntOrNull()
            author = doc.select(".author-wrap>a").text()
            rating = doc.selectFirst(".rating-text")?.text()?.toRate(5)
        }
    }

    override suspend fun loadHtml(url: String): String {
        val doc = app.get(url).document

        val jsonNode = doc.selectFirst("#__NEXT_DATA__")
        val json = jsonNode?.data() ?: throw ErrorLoadingException("no chapters")
        val chaptersJson = mapper.readValue<LoadJsonResponse.RootJson>(json)
        val text = StringBuilder()

        for (body in chaptersJson.props.pageProps.serie.chapterData.data.body) {
            if(!body.contains("window._taboola")) {
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

object ResultJsonResponse {
    data class RootJson(
        val props: PropsJson,
        /*val page: String,
    val query: QueryJson,
    val buildId: String,
    val isFallback: Boolean,
    val gssp: Boolean,
    val customServer: Boolean,
    val locale: String,
    val locales: List<String>,
    val defaultLocale: String,
    val scriptLoader: List<Any?>,*/
    )

    data class PropsJson(
        val pageProps: PagePropsJson,
        //@JsonProperty("__N_SSP")
        //val nSsp: Boolean,
    )

    data class PagePropsJson(
        val serie: SerieJson,
        //val tags: List<TagJson>,
        //@JsonProperty("server_time")
        //val serverTime: String,
    )

    data class SerieJson(
        //@JsonProperty("serie_data")
        //val serieData: SerieDataJson,
        val chapters: List<ChapterJson>,
        //val ranks: RanksJson,
        //val recommendation: List<RecommendationJson>,
        //val raws: List<Raw3Json>,
        //val names: List<NameJson>,
        //@JsonProperty("other_series")
        //val otherSeries: List<Any?>,
    )

    data class SerieDataJson(
        val id: Long,
        val slug: String,
        @JsonProperty("search_text")
        val searchText: String,
        val status: Long,
        val data: DataJson,
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
        val from: Any?,
        val author: String,
        @JsonProperty("raw_id")
        val rawId: Long,
        val genres: List<Long>,
        @JsonProperty("raw_verified")
        val rawVerified: Boolean,
    )

    data class DataJson(
        val title: String,
        val author: String,
        val description: String,
        val raw: RawJson,
        val image: String,
    )

    data class RawJson(
        val title: String,
        val author: String,
        val description: String,
    )

    data class ChapterJson(
        @JsonProperty("serie_id")
        val serieId: Long,
        val id: Long,
        val order: Long,
        val title: String,
        val name: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
    )

    data class RanksJson(
        val week: String,
        val month: String,
        val all: String,
    )

    data class RecommendationJson(
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
        val data: Data2Json,
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
        val from: Any?,
        val author: String,
        @JsonProperty("raw_id")
        val rawId: Long,
    )

    data class Data2Json(
        val title: String,
        val author: String,
        val description: String,
        val raw: Raw2Json,
        val image: String,
        @JsonProperty("chapter_count")
        val chapterCount: Long?,
    )

    data class Raw2Json(
        val title: String,
        val author: String,
        val description: String,
    )

    data class Raw3Json(
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

    data class NameJson(
        val title: String,
        @JsonProperty("raw_title")
        val rawTitle: String,
    )

    data class TagJson(
        val id: Long,
        val title: String,
        val slug: String,
    )

    data class QueryJson(
        val sid: String,
        @JsonProperty("serie_slug")
        val serieSlug: String,
    )
}


object LoadJsonResponse {
    data class RootJson(
        val props: PropsJson,
        /*val page: String,
        val query: QueryJson,
        val buildId: String,
        val isFallback: Boolean,
        val gssp: Boolean,
        val customServer: Boolean,
        val locale: String,
        val locales: List<String>,
        val defaultLocale: String,
        val scriptLoader: List<Any?>,*/
    )

    data class PropsJson(
        val pageProps: PagePropsJson,
        //@JsonProperty("__N_SSP")
        //val nSsp: Boolean,
    )

    data class PagePropsJson(
        val serie: SerieJson,
        /*@JsonProperty("disabe_ads")
        val disabeAds: String,
        @JsonProperty("server_time")
        val serverTime: String,*/
    )

    data class SerieJson(
       /* @JsonProperty("serie_data")
        val serieData: SerieDataJson,
        val chapter: Chapter,*/
        @JsonProperty("chapter_data")
        val chapterData: ChapterDataJson,
    )

    data class SerieDataJson(
        val id: Long,
        val slug: String,
        val data: DataJson,
        @JsonProperty("raw_id")
        val rawId: Long,
        @JsonProperty("user_status")
        val userStatus: Long,
        @JsonProperty("is_default")
        val isDefault: Boolean,
        @JsonProperty("chapter_count")
        val chapterCount: Long,
    )

    data class DataJson(
        val title: String,
        val author: String,
        val description: String,
        val raw: RawJson,
        val image: String,
    )

    data class RawJson(
        val title: String,
        val author: String,
        val description: String,
    )
/*
    data class ChapterJson(
        val id: Long,
        @JsonProperty("serie_id")
        val serieId: Long,
        @JsonProperty("raw_id")
        val rawId: Long,
        val status: Long,
        val slug: String,
        val name: String,
        val source: String,
        val order: Long,
        @JsonProperty("is_update")
        val isUpdate: Boolean,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        val title: String,
        val code: String,
        @JsonProperty("pre_slug")
        val preSlug: Any?,
        @JsonProperty("next_slug")
        val nextSlug: Long,
    )*/

    data class ChapterDataJson(
        /*@JsonProperty("chapter_id")
        val chapterId: Long,
        val language: String,
        val translate: String,
        val status: Long,*/
        val data: Data2Json,
        /*@JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("updated_at")
        val updatedAt: String,*/
    )

    data class Data2Json(
       // val task: String,
        //val title: String,
        val body: List<String>,
    )

    /*data class QueryJson(
        val sid: String,
        @JsonProperty("serie_slug")
        val serieSlug: String,
        @JsonProperty("chapter_slug")
        val chapterSlug: String,
    )*/

}