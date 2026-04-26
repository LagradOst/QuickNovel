package com.lagradost.quicknovel.providers

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.util.AppUtils.parseJson
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt


class WtrLabProvider : MainAPI() {
    override val mainUrl = "https://wtr-lab.com"
    override val name = "WTR-LAB"
    override val lang = "en"
    override val iconId = R.drawable.icon_wtrlab
    override val hasMainPage = true
    override val hasReviews = false
    override val usesCloudFlareKiller = true

    //&status=
    override val mainCategories = listOf(
        "All" to "all",
        "Ongoing" to "ongoing",
        "Completed" to "completed"
    )
    //&orderBy=
    override val orderBys =listOf(
        "Date" to "date",
        "Name" to "name",
        "View" to "view",
        "Reader" to "reader",
        "Chapter" to "chapter"
    )
    override val tags = listOf(
        "All" to "",
        "Action" to "1",
        "Adult" to "2",
        "Adventure" to "3",
        "Anime" to "4",
        "Arts" to "5",
        "Comedy" to "6",
        "Drama" to "7",
        "Eastern" to "8",
        "Ecchi" to "ecchi",
        "Fan-fiction" to "9",
        "Fantasy" to "10",
        "Game" to "11",
        "Gender-bender" to "12",
        "Harem" to "13",
        "Historical" to "14",
        "Horror" to "15",
        "Isekai" to "16",
        "Josei" to "17",
        "Lgbt" to "18",
        "Magic" to "19",
        "Magical-realism" to "20",
        "Manhua" to "21",
        "Martial-arts" to "22",
        "Mature" to "23",
        "Mecha" to "24",
        "Military" to "25",
        "Modern-life" to "26",
        "Movies" to "27",
        "Mystery" to "28",
        "Other" to "29",
        "Psychological" to "30",
        "Realistic-fiction" to "31",
        "Reincarnation" to "32",
        "Romance" to "33",
        "School-life" to "34",
        "Sci-fi" to "35",
        "Seinen" to "36",
        "Shoujo" to "37",
        "Shoujo-ai" to "38",
        "Shounen" to "39",
        "Shounen-ai" to "40",
        "Slice-of-life" to "41",
        "Smut" to "42",
        "Sports" to "43",
        "Supernatural" to "44",
        "System" to "45",
        "Tragedy" to "46",
        "Urban" to "47",
        "Urban-life" to "48",
        "Video-games" to "49",
        "War" to "50",
        "Wuxia" to "51",
        "Xianxia" to "52",
        "Xuanhuan" to "53",
        "Yaoi" to "54",
        "Yuri" to "55"
    )


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url = "$mainUrl/en/novel-list?page=$page&status=$mainCategory&orderBy=$orderBy&genre=$tag"
        val doc = app.get(url).document
        val returnValue =  doc.select(".series-list>div>div>.serie-item").mapNotNull { select ->
            val titleWrap = select.selectFirst(".title-wrap") ?: return@mapNotNull null
            val titleHolder = titleWrap.selectFirst("a.title") ?: return@mapNotNull null
            val href = titleHolder.attr("href") ?: return@mapNotNull null
            titleHolder.selectFirst(".rawtitle")?.remove()

            val name = titleHolder.text() ?: return@mapNotNull null
            newSearchResponse(name, href) {
                posterUrl = fixUrlNull(select.selectFirst("a img")?.attr("src"))
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }

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
                posterUrl = fixUrlNull(select.selectFirst("a img")?.attr("src"))
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
        return newStreamResponse(title, url, chapters) {
            synopsis = doc.selectFirst(".desc-wrap")?.text()
            posterUrl = fixUrlNull(doc.selectFirst(".image-wrap > img")?.attr("src"))
            val details = doc.select("div.detail-buttons div")
            details.map{div ->
                if(div.text().contains("Views")){
                    val text = div.ownText().split(" ")
                    this.views = text.getOrNull(2)?.trim()?.toIntOrNull()
                    setStatus(text.getOrNull(0)?.trim())
                }
            }
            val ratingElement = details.selectFirst("div.rating")
            val ratingText = ratingElement?.text()

            peopleVoted = ratingText?.let { text ->
                Regex("""\((\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            }
            rating = ratingText?.let { text ->
                Regex("""([\d.]+)""").find(text)?.groupValues?.get(1)?.toFloatOrNull()?.let {
                    it.times(20).times(10).roundToInt()
                }
            }
        }
    }


    override suspend fun loadHtml(url: String): String {
        val doc = app.get(url).document
        val jsonNode = doc.selectFirst("#__NEXT_DATA__")
        val json = jsonNode?.data() ?: throw ErrorLoadingException("no chapters")
        val chaptersJson = parseJson<LoadJsonResponse.Root>(json)
        val text = StringBuilder()
        val chapter = chaptersJson.props.pageProps.serie

        val root = app.post(
                "$mainUrl/api/reader/get", data = mapOf(
                    "chapter_id" to chapter.chapter.id.toString(),
                    "chapter_no" to chapter.serieData.slug,
                    "force_retry" to "false",
                    "language" to "en",
                    "raw_id" to chapter.serieData.rawId.toString(),
                    "retry" to "false",
                    "translate" to "web",
                )
            ).parsed<LoadJsonResponse2.Root>()

        val paragraphs = decryptContent(root.data.data.body)

        for (p in paragraphs) {
            text.append("<p>")
            text.append(p)
            text.append("</p>")
        }

        return text.toString()
    }

    fun decryptContent(encryptedText: String): List<String> {
        if (encryptedText.isEmpty()) return emptyList()

        var isArray = false
        var rawText = encryptedText

        if (encryptedText.startsWith("arr:")) {
            isArray = true
            rawText = encryptedText.removePrefix("arr:")
        } else if (encryptedText.startsWith("str:")) {
            rawText = encryptedText.removePrefix("str:")
        }

        val parts = rawText.split(":")
        if (parts.size != 3) throw IllegalArgumentException("Invalid format")

        val ivBytes = Base64.decode(parts[0], Base64.DEFAULT)
        val shortCipher = Base64.decode(parts[1], Base64.DEFAULT)
        val longCipher = Base64.decode(parts[2], Base64.DEFAULT)

        val cipherBytes = ByteArray(longCipher.size + shortCipher.size)
        System.arraycopy(longCipher, 0, cipherBytes, 0, longCipher.size)
        System.arraycopy(shortCipher, 0, cipherBytes, longCipher.size, shortCipher.size)

        val keyString = "IJAFUUxjM25hyzL2AZrn0wl7cESED6Ru"
        val keyBytes = keyString.substring(0, 32).toByteArray(Charsets.UTF_8)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, ivBytes)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        val decryptedText = decryptedBytes.toString(Charsets.UTF_8)

        return if (isArray) {
            parseJson<List<String>>(decryptedText)
        } else {
            listOf(decryptedText)
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
            val body: String = "",
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
        data class Terms(
            val terms: List<List<String>>,
        )
    }

    object LoadJsonResponse {
        data class Root(
            val props: Props,
            val page: String,
            val query: Query,
            val buildId: String,
            val isFallback: Boolean,
            val isExperimentalCompile: Boolean,
            val gssp: Boolean,/*
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

        data class Chapter(
            val id: Long,
            val slug: String?,
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
        data class Serie(
            @JsonProperty("serie_data")
            val serieData: SerieData,
            /*
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
            val fromUser: String?,
            val raw: Raw,
            val image: String,
        )

        data class Raw(
            val title: String,
            val author: String,
            val description: String,
        )



        data class ActiveService(
            val id: String,
            val label: String,
        )

        data class Query(
            val locale: String,
            @JsonProperty("serie_slug")
            val serieSlug: String,
            @JsonProperty("chapter_no")
            val chapterNo: String,
        )

    }
}
