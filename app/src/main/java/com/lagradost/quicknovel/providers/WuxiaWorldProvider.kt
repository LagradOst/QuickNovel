package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newReview
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.util.AppUtils.parseJson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSource

class WuxiaWorldProvider : MainAPI() {
    override val name = "WuxiaWorld"
    override val mainUrl = "https://www.wuxiaworld.com"
    override val iconId = R.drawable.icon_wuxiaworld
    override val iconBackgroundId = R.color.white
    val apiUrl = "https://api2.wuxiaworld.com/wuxiaworld.api.v2"
    val searchNovelsUrl = "${apiUrl}.Novels/SearchNovels"
    val getChaptersList = "${apiUrl}.Chapters/GetChapterList"
    val getReviewsList = "${apiUrl}.Reviews/SearchReviews"
    override val lang = "en"
    override val hasMainPage = true
    override val hasReviews = true
    override val mainCategories = listOf(
        "All" to "-1",
        "Finished" to "0",
        "Active" to "1",
        "Hiatus" to "2",

        )
    override val orderBys = listOf(
        "Popular" to "1",
        "New" to "2",
        "Chapters" to "3",
        "Name" to "4",
        "Rating" to "6",
        "Trending" to "7",
    )
    override val tags = listOf(
        "All" to "",
        "Romance" to "Romance",
        "Fantasy" to "Fantasy",
        "Comedy" to "Comedy",
        "Mystery" to "Mystery",
        "Thriller" to "Thriller",
        "Sci-fi" to "Sci-fi",
        "Cultivation" to "Cultivation",
        "Cheat Systems" to "Cheat Systems",
        "LitRPG" to "LitRPG",
        "Sports" to "Sports",
        "Slice of Life" to "Slice of Life",
    )

    private fun String.cleanSynopsis(): String {
        return this.replace(Regex("""style\s*=\s*["'][^"']*["']""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""class\s*=\s*["'][^"']*["']""", RegexOption.IGNORE_CASE), "")
    }


    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        if (page > 1) return HeadMainPageResponse("", emptyList())
        val request = buildARequestBody(
            status = mainCategory?.toLongOrNull(),
            sortType = orderBy?.toIntOrNull(),
            genres = listOf(tag)
        )

        val novels = mutableListOf<SearchResponse>()
        try {
            grpcPost(searchNovelsUrl, request, "$mainUrl/").use { source ->
                source.processGrpcStream {
                    while (!exhausted()) {
                        val tagNum = readVarint().toInt()
                        if (tagNum shr 3 == 1) { // Field 1: List of novel results
                            readMessage {
                                decodeNovelItem()?.let { novels.add(it) }
                            }
                        } else skipField(tagNum and 0x7)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return HeadMainPageResponse("$searchNovelsUrl?page=$page", novels)
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(url).text
        val reactRoot =
            extractReactQuery(res) ?: throw ErrorLoadingException("Failed to extract state")

        // Find the 'novel' data entry in the React Query state
        val query = reactRoot.queries.find {
            it.queryKey.firstOrNull() == "novel"
        } ?: throw ErrorLoadingException("Novel data not found")

        val item = parseJson<ReactNovelResponse>(query.state.data.toString()).item

        val related = mutableListOf<SearchResponse>()
        item.genres?.let { genres ->
            val requestMap = buildARequestBody(
                count = 12,
                genres = genres,
            )
            try {
                grpcPost(searchNovelsUrl, requestMap, url).use { source ->
                    source.processGrpcStream {
                        while (!exhausted()) {
                            val tagNum = readVarint().toInt()
                            if (tagNum shr 3 == 1) {
                                readMessage {
                                    decodeNovelItem()?.let { related.add(it) }
                                }
                            } else skipField(tagNum and 0x7)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val chapters = getChapters(item, url)

        return newStreamResponse(item.name, url, chapters) {
            this.posterUrl = this@WuxiaWorldProvider.fixUrlNull(item.coverUrl?.value)
            this.synopsis = (item.synopsis?.value ?: item.description?.value)?.cleanSynopsis()
            this.author = item.authorName?.value
            this.tags = item.genres
            this.related = related
            reviewData = item.id.toString()
            setStatus(getStatus(item.status))
        }
    }

    suspend fun getChapters(item: NovelDetail, url: String): List<ChapterData> {
        val chapters = mutableListOf<ChapterData>()
        grpcPost(getChaptersList, mapOf(1 to item.id), url).use { source ->
            source.processGrpcStream {
                while (!exhausted()) {
                    val tagNum = readVarint().toInt()
                    if (tagNum shr 3 == 1) { // Field 1: List of chapter groups
                        readMessage {
                            while (!exhausted()) {
                                val groupTagNum = readVarint().toInt()
                                if (groupTagNum shr 3 == 6) { // Field 6: Individual chapter message
                                    readMessage {
                                        var name = ""
                                        var chSlug = ""
                                        var isFree = false
                                        while (!exhausted()) {
                                            val chTagNum = readVarint().toInt()
                                            when (chTagNum shr 3) {
                                                2 -> name = readString() // Field 2: Chapter title
                                                3 -> chSlug = readString() // Field 3: Chapter slug
                                                20 -> readMessage { // Field 20: Access Info
                                                    while (!exhausted()) {
                                                        val accTagNum = readVarint().toInt()
                                                        if (accTagNum shr 3 == 1) isFree =
                                                            readVarint() == 1L // Sub-field 1: Is Accessible/Free
                                                        else skipField(accTagNum and 0x7)
                                                    }
                                                }

                                                else -> skipField(chTagNum and 0x7)
                                            }
                                        }
                                        if (name.isNotEmpty() && isFree) {
                                            chapters.add(
                                                newChapterData(
                                                    name,
                                                    "$mainUrl/novel/${item.slug}/$chSlug"
                                                )
                                            )
                                        }
                                    }
                                } else skipField(groupTagNum and 0x7)
                            }
                        }
                    } else skipField(tagNum and 0x7)
                }
            }
        }
        return chapters
    }

    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val novelId = data ?: return emptyList()
        val request = mapOf(
            1 to novelId,
            3 to page,
            4 to mapOf(1 to 2, 2 to 20)
        )

        val reviews = mutableListOf<UserReview>()
        grpcPost(getReviewsList, request, url).use { source ->
            source.processGrpcStream {
                while (!exhausted()) {
                    val tagNum = readVarint().toInt()
                    if (tagNum shr 3 == 1) { // Field 1: List of review results
                        readMessage {
                            decodeReviewItem()?.let { reviews.add(it) }
                        }
                    } else skipField(tagNum and 0x7)
                }
            }
        }
        return reviews
    }

    override suspend fun loadHtml(url: String): String? {
        val document = app.get(url).document
            .selectFirst("div.chapter-content")
            ?: return null
        document.select("img").forEach { img ->
            val src = img.attr("src")
            if (src.isNotEmpty()) {
                img.attr("src", fixUrl(src))
            }
        }

        return document.html()
    }

    /** Performs a novel search using the gRPC API. */
    override suspend fun search(query: String): List<SearchResponse> {
        val requestMap = buildARequestBody(
            title = query,
            count = 20
        )

        val novels = mutableListOf<SearchResponse>()
        try {
            grpcPost(searchNovelsUrl, requestMap, "$mainUrl/").use { source ->
                source.processGrpcStream {
                    while (!exhausted()) {
                        val tagNum = readVarint().toInt()
                        if (tagNum shr 3 == 1) { // Field 1: List of novel results
                            readMessage {
                                decodeNovelItem()?.let { novels.add(it) }
                            }
                        } else skipField(tagNum and 0x7)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return novels
    }

    data class ReactQueryRoot(
        @JsonProperty("queries") val queries: List<ReactQueryItem>
    )

    data class ReactQueryItem(
        @JsonProperty("queryKey") val queryKey: List<Any?>,
        @JsonProperty("state") val state: ReactQueryState
    )

    data class ReactQueryState(
        @JsonProperty("data") val data: JsonNode
    )

    data class ReactNovelResponse(
        @JsonProperty("item") val item: NovelDetail
    )

    data class NovelDetail(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String,
        @JsonProperty("status") val status: Int,
        @JsonProperty("slug") val slug: String,
        @JsonProperty("authorName") val authorName: ValueWrapper?,
        @JsonProperty("synopsis") val synopsis: ValueWrapper?,
        @JsonProperty("description") val description: ValueWrapper?,
        @JsonProperty("coverUrl") val coverUrl: ValueWrapper?,
        @JsonProperty("genres") val genres: List<String>?
    )

    fun getStatus(id: Int): String {
        return when (id) {
            0 -> "completed"
            1 -> "ongoing"
            2 -> "hiatus"
            else -> "ongoing"
        }
    }

    data class ValueWrapper(@JsonProperty("value") val value: String?)

    private fun buildARequestBody(
        title: String? = null,
        status: Long? = -1L, // status: All
        sortType: Int? = 1, // sortType
        sortDirection: Long = 1L, // sortDirection: 1 DESC, 0 ASC
        count: Int = 500, // count
        genres: List<String?>? = null,
        genresFilter: Int = 1, //1 OR, 0 AND
    ): MutableMap<Int, Any> {
        val requestMap = mutableMapOf<Int, Any>()
        if (title != null) requestMap[1] = mapOf(1 to title)
        requestMap[3] = status ?: -1L
        requestMap[4] = sortType ?: 1
        requestMap[5] = sortDirection
        requestMap[7] = count
        val genresAux = genres?.filter { !it.isNullOrEmpty() }
        if (!genresAux.isNullOrEmpty()) requestMap[10] =
            genresAux.map { 1 to it } + (2 to genresFilter)

        return requestMap
    }

    /**
     * Helper class to decode Protobuf messages directly from a network stream (BufferedSource).
     * This prevents OutOfMemory errors by not loading the entire response into a ByteArray.
     */
    private class ProtoReader(
        private val source: BufferedSource,
        var limit: Long = Long.MAX_VALUE
    ) {
        /** Returns true if the current message scope or the source is exhausted. */
        fun exhausted() = source.exhausted() || limit <= 0

        /** Decrements the remaining bytes allowed to be read in the current scope. */
        private fun consume(bytes: Long) {
            if (limit != Long.MAX_VALUE) {
                limit -= bytes
            }
        }

        /** Reads a Variable Integer (Varint) from the stream, common in Protobuf for tags and lengths. */
        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = source.readByte().toInt()
                consume(1)
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
            }
            return result
        }

        /** Skips a field in the stream based on its wire type if we don't need its value. */
        fun skipField(wireType: Int) {
            when (wireType) {
                0 -> readVarint() // Varint
                1 -> {
                    source.skip(8); consume(8)
                } // 64-bit
                2 -> {
                    val len = readVarint(); source.skip(len); consume(len)
                } // Length-delimited
                5 -> {
                    source.skip(4); consume(4)
                } // 32-bit
                else -> throw Exception("Unknown wire type $wireType")
            }
        }

        /** Reads a UTF-8 string from a length-delimited field. */
        fun readString(): String {
            val len = readVarint()
            consume(len)
            return source.readUtf8(len)
        }

        /** Reads raw bytes from a length-delimited field. */
        fun readBytes(): ByteArray {
            val len = readVarint()
            consume(len)
            return source.readByteArray(len)
        }

        /**
         * Enters a nested Protobuf message (Tag with wire type 2).
         * Creates a fresh child reader with a strict byte limit to ensure isolation.
         */
        inline fun readMessage(callback: ProtoReader.() -> Unit) {
            val len = readVarint()
            consume(len)
            val subReader = ProtoReader(source, len)
            subReader.callback()
            // If the callback didn't consume all bytes, skip them to maintain parent stream alignment
            if (subReader.limit > 0) {
                source.skip(subReader.limit)
            }
        }
    }

    /** Helper class to build Protobuf messages into a Buffer. */
    private class ProtoWriter {
        private val buffer = Buffer()

        /** Writes an integer as a Protobuf Varint. */
        fun writeVarint(value: Long) {
            var v = value
            if (v < 0) {
                repeat(9) {
                    buffer.writeByte(((v and 0x7F) or 0x80).toInt())
                    v = v ushr 7
                }
                buffer.writeByte(v.toInt())
            } else {
                while (v and 0x7F.inv() != 0L) {
                    buffer.writeByte(((v and 0x7F) or 0x80).toInt())
                    v = v ushr 7
                }
                buffer.writeByte(v.toInt())
            }
        }

        /** Encodes a Tag (Field Number + Wire Type). */
        fun writeTag(field: Int, wireType: Int) {
            writeVarint(((field shl 3) or wireType).toLong())
        }

        /** Writes a field with a Varint value. */
        fun writeVarintTag(field: Int, value: Long) {
            writeTag(field, 0)
            writeVarint(value)
        }

        /** Writes a length-delimited string field. */
        fun writeString(field: Int, value: String) {
            writeTag(field, 2)
            val bytes = value.toByteArray()
            writeVarint(bytes.size.toLong())
            buffer.write(bytes)
        }

        /** Writes a raw byte array field. */
        fun writeBytes(field: Int, value: ByteArray) {
            writeTag(field, 2)
            writeVarint(value.size.toLong())
            buffer.write(value)
        }

        /** Finalizes the message and returns the byte array. */
        fun build(): ByteArray = buffer.readByteArray()
    }

    private fun extractReactQuery(html: String): ReactQueryRoot? {
        val startToken = "__REACT_QUERY_STATE__ ="
        if (!html.contains(startToken)) return null
        return try {
            val raw = html.substringAfter(startToken).trim()
            // Slicing logic: ends before APP_CONTEXT or the script closing tag.
            val json = if (raw.contains("window.__APP_CONTEXT__")) {
                raw.substringBefore("window.__APP_CONTEXT__").trim().removeSuffix(";")
            } else {
                raw.substringBeforeLast("</script>").substringBeforeLast("};").trim() + "}"
            }.trim()
            parseJson<ReactQueryRoot>(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun ProtoReader.decodeReviewItem(): UserReview? {
        var text = ""
        var user = ""
        var avatar = ""

        while (!exhausted()) {
            val tagNum = readVarint().toInt()
            val field = tagNum shr 3
            val wire = tagNum and 0x7

            when (field) {
                2 -> readMessage { // Review Details Message
                    while (!exhausted()) {
                        val rTagNum = readVarint().toInt()
                        if (rTagNum shr 3 == 1) text = readString() // Field 1: The review text
                        else skipField(rTagNum and 0x7)
                    }
                }

                5 -> readMessage { // User Info Message
                    while (!exhausted()) {
                        val uTagNum = readVarint().toInt()
                        val uField = uTagNum shr 3
                        val uWire = uTagNum and 0x7
                        when (uField) {
                            2 -> user = readString() // Field 2: Nickname
                            3 -> readMessage { // Avatar Message
                                while (!exhausted()) {
                                    val aTagNum = readVarint().toInt()
                                    if (aTagNum shr 3 == 1) avatar =
                                        readString() // Field 1: Avatar URL path
                                    else skipField(aTagNum and 0x7)
                                }
                            }

                            else -> skipField(uWire)
                        }
                    }
                }

                else -> skipField(wire)
            }
        }
        return if (text.isNotEmpty()) newReview(text) {
            username = user
            avatarUrl = avatar
        } else null
    }

    private fun ProtoReader.decodeNovelItem(): SearchResponse? {
        var name = ""
        var slug = ""
        var cover = ""

        while (!exhausted()) {
            val tagNum = readVarint().toInt()
            val field = tagNum shr 3
            val wireType = tagNum and 0x7
            when (field) {
                2 -> name = readString() // Field 2: Title
                3 -> slug = readString() // Field 3: Slug
                10 -> { // Field 10: Nested cover info
                    readMessage {
                        while (!exhausted()) {
                            val innerTagNum = readVarint().toInt()
                            if (innerTagNum shr 3 == 1) cover = readString() // Field 1: URL
                            else skipField(innerTagNum and 0x7)
                        }
                    }
                }

                else -> skipField(wireType)
            }
        }

        if (name.isEmpty() || slug.isEmpty()) return null
        return newSearchResponse(name, "$mainUrl/novel/$slug") {
            posterUrl = cover
        }
    }

    /**
     * Recursively encodes a list of Field-Value pairs into Protobuf binary format.
     * Supports nested messages (as Iterables or Maps).
     */
    private fun Iterable<Pair<Int, Any>>.encodeProto(): ByteArray {
        val writer = ProtoWriter()
        this.sortedBy { it.first }.forEach { (field, value) ->
            when (value) {
                is Int -> writer.writeVarintTag(field, value.toLong())
                is Long -> writer.writeVarintTag(field, value)
                is String -> writer.writeString(field, value)
                is ByteArray -> writer.writeBytes(field, value)
                is Iterable<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    writer.writeBytes(field, (value as Iterable<Pair<Int, Any>>).encodeProto())
                }

                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    writer.writeBytes(field, (value as Map<Int, Any>).toList().encodeProto())
                }
            }
        }
        return writer.build()
    }

    /** Wraps Protobuf bytes into a gRPC-Web frame (1-byte flag + 4-byte length + payload). */
    private fun ByteArray.toGrpcWeb(): ByteArray {
        val out = Buffer()
        out.writeByte(0) // Flags: 0 means Data, 1 means Trailers
        out.writeInt(this.size) // Length in Big Endian
        out.write(this)
        return out.readByteArray()
    }

    /** Sends a gRPC-Web POST request and returns a streamable Source. */
    private suspend fun grpcPost(
        url: String,
        payload: Iterable<Pair<Int, Any>>,
        referer: String? = null
    ): BufferedSource {
        val body = payload.encodeProto().toGrpcWeb()
        val headers = mutableMapOf(
            "Content-Type" to "application/grpc-web+proto",
            "Accept" to "application/grpc-web+proto",
            "X-User-Agent" to "grpc-web-javascript/0.1",
            "x-grpc-web" to "1"
        )
        referer?.let { headers["Referer"] = it }

        val res = app.post(
            url,
            headers = headers,
            requestBody = body.toRequestBody("application/grpc-web+proto".toMediaTypeOrNull())
        )
        return res.body.source()
    }

    /** Convenience overload for Map-based payloads. */
    private suspend fun grpcPost(
        url: String,
        request: Map<Int, Any>,
        referer: String? = null
    ): BufferedSource {
        return grpcPost(url, request.toList(), referer)
    }

    /**
     * Iterates over gRPC-Web frames in a stream.
     * Each valid data frame is passed to the callback as a isolated ProtoReader.
     */
    private inline fun BufferedSource.processGrpcStream(callback: ProtoReader.() -> Unit) {
        while (!exhausted()) {
            val flags = readByte()
            val length = readInt()
            if (flags.toInt() != 0) { // Skip trailers or compressed chunks
                if (length > 0) skip(length.toLong())
                continue
            }
            // Process the data frame
            ProtoReader(this, length.toLong()).callback()
        }
    }
}
