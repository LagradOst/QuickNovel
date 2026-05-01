package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.util.AppUtils.parseJson

class SkyNovelsProvider : MainAPI() {
    override val name = "SkyNovels"
    override val mainUrl = "https://www.skynovels.net"
    override val lang = "es"
    override val hasMainPage = true
    override val iconId = R.drawable.icon_skynovel
    override val iconBackgroundId = R.color.colorPrimaryWhite

    override val mainCategories = listOf(
        "Todas" to "",
        "En emisión" to "Active",
        "Finalizadas" to "Finished"
    )

    override val orderBys = listOf(
        "Recientes" to "updated",
        "Título" to "title",
        "Más capítulos" to "chapters",
        "Más vistas" to "view"
    )

    override val tags = listOf(
        "Todos" to "",
        "Acción" to "9",
        "Adulto" to "38",
        "Artes marciales" to "3",
        "Aventura" to "2",
        "BL" to "40",
        "Comedia" to "7",
        "Cosas de la Vida" to "26",
        "Cultivación" to "19",
        "Drama" to "8",
        "Ecchi" to "21",
        "Fantasía" to "4",
        "GL" to "41",
        "Gender Bender" to "10",
        "Harem" to "12",
        "Histórico" to "32",
        "Horror" to "39",
        "LitRPG" to "31",
        "Maduro" to "1",
        "Magia" to "16",
        "Misterio" to "22",
        "Mundo Moderno" to "34",
        "Psicológico" to "27",
        "Recuentos de la vida" to "36",
        "Reencarnación" to "23",
        "Romance" to "5",
        "Sci-Fi" to "17",
        "Seinen" to "18",
        "Shoujo" to "33",
        "Shounen" to "13",
        "Sin género" to "37",
        "Sobrenatural" to "20",
        "Supervivencia" to "25",
        "Suspenso" to "35",
        "Tragedia" to "14",
        "Transmigración" to "24",
        "Vida Escolar" to "29",
        "Xianxia" to "6",
        "Xuanhuan" to "11",
        "Yaoi" to "30"
    )

    private val apiBase = "https://api.skynovels.net/api/"

    private fun buildPoster(image: String?): String? {
        return image?.let { "${apiBase}get-image/$it/novels/false" }
    }

    private fun Novel.toSearchResponse(): SearchResponse? {
        val slug = slug ?: return null
        return newSearchResponse(
            name = title,
            url = "$mainUrl/novelas/$id/$slug"
        ) {
            posterUrl = buildPoster(this@toSearchResponse.image)
            rating = rating?.let { (it * 200)}
            latestChapter = chapters?.let { "Capítulos: $it" }
        }
    }

    private data class NovelListResponse(val novels: List<Novel> = emptyList())

    private data class Novel(
        val id: Int,
        @JsonProperty("nvl_title") val title: String,
        @JsonProperty("nvl_name") val slug: String? = null,
        @JsonProperty("nvl_writer") val writer: String? = null,
        @JsonProperty("nvl_content") val summary: String? = null,
        @JsonProperty("nvl_status") val status: String? = null,
        @JsonProperty("nvl_rating") val rating: Double? = null,
        @JsonProperty("image") val image: String? = null,
        @JsonProperty("genres") val genres: List<Genre> = emptyList(),
        @JsonProperty("nvl_chapters") val chapters: Int? = null
    )

    private data class Genre(@JsonProperty("genre_name") val name: String? = null)

    private data class NovelDetailResponse(@JsonProperty("novel") val novel: List<NovelDetail> = emptyList())

    private data class NovelDetail(
        val id: Int,
        @JsonProperty("nvl_title") val title: String,
        @JsonProperty("nvl_name") val slug: String? = null,
        @JsonProperty("nvl_writer") val writer: String? = null,
        @JsonProperty("nvl_content") val content: String? = null,
        @JsonProperty("nvl_status") val status: String? = null,
        @JsonProperty("image") val image: String? = null,
        @JsonProperty("genres") val genres: List<Genre> = emptyList(),
        @JsonProperty("volumes") val volumes: List<Volume> = emptyList()
    )

    private data class Volume(
        @JsonProperty("vlm_title") val title: String? = null,
        val chapters: List<Chapter> = emptyList()
    )

    private data class Chapter(
        val id: Int,
        @JsonProperty("chp_index_title") val title: String? = null,
        @JsonProperty("chp_name") val slug: String? = null,
        @JsonProperty("createdAt") val date: String? = null
    )

    private data class ChapterResponse(val chapter: List<ChapterContent> = emptyList())
    private data class ChapterContent(@JsonProperty("chp_content") val content: String? = null)


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url = buildString {
            append("${apiBase}novels?page=$page&direction=desc")
            orderBy?.takeIf { it.isNotBlank() }?.let { append("&order=$it") }
            mainCategory?.takeIf { it.isNotBlank() }?.let { append("&status=$it") }
            tag?.takeIf { it.isNotBlank() }?.let { append("&genres=$it") }
        }

        val root = app.get(url, headers = mapOf("referer" to mainUrl)).parsed<NovelListResponse>()
        return HeadMainPageResponse(url, root.novels.mapNotNull { it.toSearchResponse() })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val root = app.get("${apiBase}novels?q=$query", headers = mapOf("referer" to mainUrl)).parsed<NovelListResponse>()
        return root.novels.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.split("/").getOrNull(4)?.toIntOrNull() ?: return null
        val res = app.get("${apiBase}novel/$id/reading?&q", headers = mapOf("referer" to mainUrl))
        val root = parseJson<NovelDetailResponse>(res.text)
        val novel = root.novel.firstOrNull() ?: return null

        val chapters = novel.volumes.flatMap { volume ->
            volume.chapters.map { c ->
                newChapterData(
                    name = "${volume.title ?: "Vol"} - ${c.title ?: "Cap"}",
                    url = "$mainUrl/novelas/${novel.id}/${novel.slug}/${c.id}/${c.slug}"
                ) {
                    dateOfRelease = c.date
                }
            }
        }

        return newStreamResponse(
            name = novel.title,
            url = "$mainUrl/novelas/${novel.id}/${novel.slug}",
            data = chapters
        ) {
            author = novel.writer
            posterUrl = buildPoster(novel.image)
            synopsis = novel.content
            tags = novel.genres.mapNotNull { it.name }
            this.setStatus(novel.status)
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val chapterId = url.split("/").getOrNull(6) ?: return null
        val root = app.get("${apiBase}novel-chapter/$chapterId", headers = mapOf("referer" to mainUrl)).parsed<ChapterResponse>()
        val rawContent = root.chapter.firstOrNull()?.content ?: return null

        return buildString {
            rawContent.split("\n").filter { it.isNotBlank() }.forEach { line ->
                val txt = line.trim()
                if (txt.startsWith("![](")) {
                    val imgUrl = txt.substringAfter("![](").substringBefore(")")
                        .let { if (it.startsWith("/api/")) "$apiBase${it.removePrefix("/api/")}" else it }
                    append("<img src=\"$imgUrl\"><br>")
                } else {
                    append("<p>$txt</p>")
                }
            }
        }
    }
}