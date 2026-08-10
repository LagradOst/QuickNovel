package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.util.AppUtils.parseJson
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

open class RanobeHubProvider : MainAPI() {
    override val name = "RanobeHub"
    override val mainUrl = "https://ranobehub.org"
    override val iconId = R.drawable.icon_ranobehub
    override val hasMainPage = true
    override val lang = "ru"
    override val rateLimitTime = 1000L
    val novelsIdRequired = ConcurrentHashMap<String, String>()

    override val orderBys = listOf(
        "By Rating" to "computed_rating",
        "By Update Date" to "last_chapter_at",
        "By Add Date" to "created_at",
        "By Title" to "name_rus",
        "By Views" to "views",
        "By Chapter Count" to "count_chapters",
        "By Symbols Count" to "count_of_symbols"
    )

    override val tags = listOf(
        "Any Status" to "0",
        "Ongoing" to "1",
        "Completed" to "2",
        "On Hiatus" to "3",
        "Unknown" to "4"
    )

    override val hasReviews = true

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val sort = orderBy ?: "computed_rating"
        val status = tag ?: "0"

        val range = if (page == 1) {
            1..2
        } else {
            val actualPage = page + 1
            actualPage..actualPage
        }
        var url = ""
        val novels = mutableListOf<SearchResponse>()
        range.forEach { newPage ->
            url = "$mainUrl/api/search?page=$newPage&sort=$sort&status=$status&take=40"
            val response = app.get(url).parsed<SearchApiResponse>()

            novels.addAll(response.resource.map { item ->
                val title = item.names.rus?.takeIf { it.isNotBlank() }
                    ?: item.names.eng?.takeIf { it.isNotBlank() }
                    ?: item.names.original!!

                newSearchResponse(title, "/ranobe/${item.id}") {
                    this.posterUrl = fixUrlNull(item.poster?.medium)
                    this.rating = item.rating?.let { (it * 100).toInt() }
                }
            })
        }


        return HeadMainPageResponse(url, novels)
    }

    override suspend fun load(url: String): LoadResponse {
        val id = url.removeSuffix("/").substringAfterLast("/").substringBefore("-")

        val document = app.get("$mainUrl/ranobe/$id").document
        document.selectFirst("comments-section")?.attr("model-encrypted-key")?.let {
            novelsIdRequired[url] = it
        }

        val detailsUrl = "$mainUrl/api/ranobe/$id"
        val response = app.get(detailsUrl).parsed<NovelDetailsResponse>().data

        val title = response.names.rus?.takeIf { it.isNotBlank() }
            ?: response.names.eng?.takeIf { it.isNotBlank() }
            ?: response.names.original!!


        val chapters = mutableListOf<ChapterData>()
        val chaptersUrl = "$mainUrl/api/ranobe/$id/contents"
        val chaptersResponse = app.get(chaptersUrl).parsed<ChaptersResponse>()

        chaptersResponse.volumes.forEach { volume ->
            val volNum = volume.num
            volume.chapters.forEach { chapter ->
                val chapNum = chapter.num
                val chapName = chapter.name
                val timestamp = chapter.changedAt

                val dateStr = if (timestamp > 0) {
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp * 1000))
                } else null

                chapters.add(
                    newChapterData(name = chapName, url = "$id/$volNum/$chapNum", fix = false) {
                        this.dateOfRelease = dateStr
                    }
                )
            }
        }

        return newStreamResponse(title, url, chapters) {
            this.posterUrl = fixUrlNull(response.posters?.medium)
            this.author = response.authors?.firstOrNull()?.nameEng
            this.synopsis = response.description
            this.rating = response.rating?.let { (it * 100).toInt() }
            this.tags = response.tags?.let { tags ->
                (tags.genres.map { it.names.rus ?: it.names.eng ?: it.title } +
                        tags.events.map { it.names.rus ?: it.names.eng ?: it.title })
            }
            setStatus(response.status?.id?.let {
                when (it) {
                    1 -> "ongoing"
                    2 -> "completed"
                    3 -> "hiatus"
                    else -> null
                }
            })
            related = getRelated(id)
        }
    }

    suspend fun getRelated(id: String): List<SearchResponse> {
        return try {
            val url = "$mainUrl/api/ranobe/similar/$id/methods/ai/jaccard"
            val response = app.get(url).parsed<SimilarResponse>()
            response.data?.mapNotNull { item ->
                val title = item.names?.rus?.takeIf { it.isNotBlank() }
                    ?: item.names?.eng?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null

                newSearchResponse(title, "/ranobe/${item.id}") {
                    this.posterUrl = fixUrlNull(item.posters?.medium)
                }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
    override suspend fun loadHtml(url: String): String? {
        val body = app.get("$mainUrl/ranobe/$url").document
        val content = body.select("div.ui.text.container").first{ it.selectFirst("div.title-wrapper") != null}
        content.select("div.chapter-hoticons").remove()
        content.select("img[data-media-id]").forEach { img ->
            val mediaId = img.attr("data-media-id")
            img.attr("src", "$mainUrl/api/media/$mediaId")
        }

        return content.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/api/fulltext/global?query=${URLEncoder.encode(query, "UTF-8")}&take=10"
        val text = app.get(url).text
        val response = try {
            parseJson<Array<GlobalSearchResponse>>(text)
        } catch (_: Exception) {
            return emptyList()
        }

        val novelsMap = mutableMapOf<Int, SearchResponse>()

        response.forEach { result ->
            if (result.meta?.key != "ranobe") return@forEach

            val items = (result.data ?: emptyList()) + (result.collections ?: emptyList())

            items.forEach { item ->
                val id = item.id ?: return@forEach
                if (novelsMap.containsKey(id)) return@forEach

                val title = item.names?.rus?.takeIf { it.isNotBlank() }
                    ?: item.names?.eng?.takeIf { it.isNotBlank() }
                    ?: item.names?.original
                    ?: item.name

                val image = when (val img = item.image) {
                    is String -> img
                    is Map<*, *> -> img["medium"] as? String ?: img["small"] as? String
                    else -> null
                }?.replace("/small", "/medium")

                novelsMap[id] = newSearchResponse(title ?: "Unknown", "/ranobe/$id") {
                    this.posterUrl = fixUrlNull(image)
                }
            }
        }

        return novelsMap.values.toList()
    }

    override suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean
    ): List<UserReview> {
        val key = novelsIdRequired[url] ?: return emptyList()
        val apiUrl = "$mainUrl/api/comments?commentable_encrypted_key=$key&order_by=rating&order_direction=desc"
        val response = app.get(apiUrl).parsed<CommentsResponse>()

        val commentersMap = response.commenters?.associateBy { it.id } ?: emptyMap()

        return response.comments?.map { comment ->
            val user = commentersMap[comment.commenterId]
            UserReview(
                review = comment.comment?.let { Jsoup.parse(it).text() } ?: "",
                username = user?.name ?: "User",
                reviewDate = comment.createdAt?.let {
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(it * 1000L))
                },
                avatarUrl = user?.avatar?.thumb?.let { fixUrlNull(it) },
                rating = comment.rating?.times(10)
            )
        } ?: emptyList()
    }

    data class SearchApiResponse(
        @JsonProperty("resource") val resource: List<SearchItem>
    )

    data class SearchItem(
        @JsonProperty("id") val id: Int,
        @JsonProperty("names") val names: Names,
        @JsonProperty("poster") val poster: Poster? = null,
        @JsonProperty("rating") val rating: Double? = null
    )

    data class Names(
        @JsonProperty("rus") val rus: String? = null,
        @JsonProperty("eng") val eng: String? = null,
        @JsonProperty("original") val original: String? = null
    )

    data class Poster(
        @JsonProperty("medium") val medium: String? = null
    )

    data class GlobalSearchResponse(
        @JsonProperty("meta") val meta: Meta? = null,
        @JsonProperty("data") val data: List<GlobalSearchItem>? = null,
        @JsonProperty("collections") val collections: List<GlobalSearchItem>? = null
    )

    data class GlobalSearchItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("names") val names: Names? = null,
        @JsonProperty("image") val image: Any? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("description") val description: String? = null
    )

    data class Meta(
        @JsonProperty("key") val key: String? = null
    )

    data class NovelDetailsResponse(
        @JsonProperty("data") val data: NovelData
    )

    data class NovelData(
        @JsonProperty("names") val names: Names,
        @JsonProperty("posters") val posters: Poster? = null,
        @JsonProperty("description") val description: String,
        @JsonProperty("authors") val authors: List<Author>? = null,
        @JsonProperty("status") val status: Status? = null,
        @JsonProperty("tags") val tags: Tags? = null,
        @JsonProperty("rating") val rating: Double? = null
    )

    data class Author(
        @JsonProperty("name_eng") val nameEng: String
    )

    data class Status(
        @JsonProperty("id") val id: Int
    )

    data class Tags(
        @JsonProperty("genres") val genres: List<TagItem>,
        @JsonProperty("events") val events: List<TagItem>
    )

    data class TagItem(
        @JsonProperty("title") val title: String,
        @JsonProperty("names") val names: Names
    )

    data class ChaptersResponse(
        @JsonProperty("volumes") val volumes: List<Volume>
    )

    data class Volume(
        @JsonProperty("num") val num: Int,
        @JsonProperty("chapters") val chapters: List<ChapterItem>
    )

    data class ChapterItem(
        @JsonProperty("num") val num: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("changed_at") val changedAt: Long
    )

    data class CommentsResponse(
        @JsonProperty("success") val success: Boolean? = null,
        @JsonProperty("comments") val comments: List<CommentItem>? = null,
        @JsonProperty("commenters") val commenters: List<Commenter>? = null
    )

    data class CommentItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("comment") val comment: String? = null,
        @JsonProperty("created_at") val createdAt: Long? = null,
        @JsonProperty("rating") val rating: Int? = null,
        @JsonProperty("commenterId") val commenterId: Int? = null,
        @JsonProperty("children") val children: List<CommentItem>? = null
    )

    data class Commenter(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("avatar") val avatar: Avatar? = null
    )

    data class Avatar(
        @JsonProperty("thumb") val thumb: String? = null
    )

    data class SimilarResponse(
        @JsonProperty("data") val data: List<SimilarItem>? = null
    )

    data class SimilarItem(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("names") val names: Names? = null,
        @JsonProperty("posters") val posters: Poster? = null
    )
}
