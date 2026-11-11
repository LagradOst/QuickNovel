package com.lagradost.quicknovel

import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.img
import com.lagradost.quicknovel.util.DefaultImagesHeaders
import kotlinx.coroutines.sync.Mutex
import org.jsoup.nodes.Element
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36"

abstract class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"

    open val lang = "en" // ISO_639_1 check SubtitleHelper

    open val usesCloudFlareKiller = false
    val app get() = if (!usesCloudFlareKiller) MainActivity.app else MainActivity.appWithInterceptor

    fun fixPosterHeaders(headers: Map<String, String>?): Map<String, String>? {
        return if (usesCloudFlareKiller) (headers
            ?: emptyMap()) + DefaultImagesHeaders.useCloudflareKillerHeader else headers
    }

    open val rateLimitTime: Long = 0
    val hasRateLimit: Boolean get() = rateLimitTime > 0L
    val rateLimitMutex: Mutex = Mutex()

    // DECLARE HAS ACCESS TO MAIN PAGE INFORMATION
    open val hasMainPage = false

    open val mainCategories: List<Pair<String, String>> = listOf()
    open val orderBys: List<Pair<String, String>> = listOf()
    open val tags: List<Pair<String, String>> = listOf()

    open val iconId: Int? = null
    open val iconBackgroundId: Int = R.color.primaryGrayBackground

    open suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        throw NotImplementedError()
    }

    open val hasReviews: Boolean = false
    open suspend fun loadReviews(
        url: String,
        page: Int,
        data : String?,
    ): List<UserReview> {
        throw NotImplementedError()
    }

    open suspend fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    open suspend fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    open suspend fun loadHtml(url: String): String? {
        throw NotImplementedError()
    }

    /*open suspend fun loadEpub(link: DownloadLinkType): ByteArray {
        if (link is DownloadLink) {
            return MainActivity.app.get(
                link.link,
                headers = link.headers,
                referer = link.referer,
                params = link.params,
                cookies = link.cookies
            ).body.byteStream().readBytes()
        } else {
            throw NotImplementedError()
        }
    }*/
}

class ErrorLoadingException(message: String? = null) : Exception(message)

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url.isNullOrEmpty()) {
        return null
    }
    return fixUrl(url)
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http")) {
        return url
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return mainUrl + url
        }
        return "$mainUrl/$url"
    }
}

//\.([A-z]) instead of \.([^-\s]) to preserve numbers like 17.4
val String?.textClean: String?
    get() = (this
        ?.replace(
            "\\.([A-z]|\\+)".toRegex(),
            "$1"
        ) //\.([^-\s]) BECAUSE + COMES AFTER YOU HAVE TO ADD \+ for stuff like shapes.h.i.+fted
        ?.replace("\\+([A-z])".toRegex(), "$1") //\+([^-\s])
            )

fun packageAsXHtml(title: String, bodyHtml: String): String {
    return """
        <?xml version='1.0' encoding='utf-8'?>
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"> 
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        <head>
            <title>$title</title>
            <link rel="stylesheet" type="text/css" href="style.css"/>
        </head>
        <body>
            $bodyHtml
        </body>
        </html>
    """.trimIndent()
}

data class HomePageList(
    val name: String,
    val list: List<SearchResponse>
)

data class HeadMainPageResponse(
    val url: String,
    val list: List<SearchResponse>,
)

data class UserReview(
    var review: String,
    var title: String? = null,
    var username: String? = null,
    var avatarUrl: String? = null,
    var rating: Int? = null,
    var ratings: List<Pair<Int, String>>? = null,
    var date: String? = null,
    var avatarHeaders: Map<String, String>? = null,
    var containsSpoilers : Boolean = false,
) {
    var score get() = Score.fromOld(this.rating)
        set(value) {
            this.rating = value?.toOld()
        }
    var scores get() = ratings?.map { (score, text) -> Score.fromOld(score) to text }
        set(value) { ratings = value?.map { (score, text) -> score!!.toOld() to text } }
}
/*
data class MainPageResponse(
    val name: String,
    val url: String,
    val posterUrl: String?,
    val rating: Int?,
    val latestChapter: String?,
    val apiName: String,
    val tags: ArrayList<String>,
)*/

data class SearchResponse(
    val name: String,
    val url: String,
    var posterUrl: String? = null,
    var rating: Int? = null,
    var latestChapter: String? = null,
    val apiName: String,
    var synopsis: String? = null,
    var posterHeaders: Map<String, String>? = null,
    var slopStatus: SlopStatus? = null,
    var genre: List<String>? = null,
    var tags: List<String>? = null,
    var contentWarnings: List<String>? = null,
) {
    var score get() = Score.fromOld(this.rating)
        set(value) {
            this.rating = value?.toOld()
        }
    val image get() = img(posterUrl, posterHeaders)
}


fun MainAPI.newSearchResponse(
    name: String,
    url: String,
    fix: Boolean = true,
    initializer: SearchResponse.() -> Unit = { },
): SearchResponse {
    val builder = SearchResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name
    )
    builder.initializer()
    builder.posterHeaders = fixPosterHeaders(builder.posterHeaders)
    return builder
}

fun MainAPI.newReview(
    content: String,
    fix: Boolean = true,
    initializer: UserReview.() -> Unit = { },
): UserReview {
    val builder = UserReview(
        review = content
    )
    builder.initializer()
    if(fix) {
        builder.avatarUrl = fixUrlNull(builder.avatarUrl)
    }
    builder.avatarHeaders = fixPosterHeaders(builder.avatarHeaders)
    return builder
}

enum class SlopStatus {
    Human,
    AiAssisted,
    AiGenerated,
}

enum class ReleaseStatus(@StringRes val resource: Int) {
    Ongoing(R.string.ongoing),
    Completed(R.string.completed),
    Paused(R.string.paused),
    Dropped(R.string.dropped),
    Stubbed(R.string.stubbed),
}

fun LoadResponse.setStatus(status: String?): Boolean {
    if (status == null) {
        return false
    }
    this.status = when (status.lowercase().trim()) {
        "ongoing", "on-going", "on_going", "releasing" -> ReleaseStatus.Ongoing
        "completed", "complete", "done" -> ReleaseStatus.Completed
        "hiatus", "paused", "pause" -> ReleaseStatus.Paused
        "dropped", "drop" -> ReleaseStatus.Dropped
        "stub", "stubbed" -> ReleaseStatus.Stubbed
        else -> return false
    }
    return true
}

interface LoadResponse {
    val url: String
    val name: String
    var author: String?
    var posterUrl: String?

    //RATING IS FROM 0-1000
    @Deprecated("Use score instead")
    var rating: Int?
    var peopleVoted: Int?
    var views: Int?
    var synopsis: String?
    var tags: List<String>?
    var genre: List<String>?
    var contentWarnings: List<String>?
    var status: ReleaseStatus? // 0 = null - implemented but not found, 1 = Ongoing, 2 = Complete, 3 = Pause/HIATUS, 4 = Dropped
    var posterHeaders: Map<String, String>?
    val image: UiImage? get() = img(url = posterUrl, headers = posterHeaders)
    val apiName: String
    var related: List<SearchResponse>?
    var reviewData : String?
    val isImported: Boolean get() = (apiName == IMPORT_SOURCE || apiName == IMPORT_SOURCE_PDF)
    var slopStatus: SlopStatus?

    var score get() = Score.fromOld(this.rating)
        set(value) {
            this.rating = value?.toOld()
        }
}

data class StreamResponse(
    override val url: String,
    override val name: String,
    val data: List<ChapterData>,
    override val apiName: String,
    override var author: String? = null,
    override var posterUrl: String? = null,
    @Deprecated("Use score instead")
    override var rating: Int? = null,
    override var peopleVoted: Int? = null,
    override var views: Int? = null,
    override var synopsis: String? = null,
    override var tags: List<String>? = null,
    override var status: ReleaseStatus? = null,
    override var posterHeaders: Map<String, String>? = null,
    var nextChapter: ChapterData? = null,
    override var related: List<SearchResponse>? = null,
    override var reviewData: String? = null,
    override var slopStatus: SlopStatus? = null,
    override var contentWarnings: List<String>? = null,
    override var genre: List<String>? = null,
) : LoadResponse

suspend fun MainAPI.newStreamResponse(
    name: String,
    url: String,
    data: List<ChapterData>,
    fix: Boolean = true,
    initializer: suspend StreamResponse.() -> Unit = { },
): StreamResponse {
    val builder = StreamResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        data = data
    )
    builder.initializer()
    builder.posterHeaders = fixPosterHeaders(builder.posterHeaders)

    return builder
}

data class DownloadLink(
    override val url: String,
    override val name: String,
    val referer: String? = null,
    val headers: Map<String, String> = mapOf(),
    val params: Map<String, String> = mapOf(),
    val cookies: Map<String, String> = mapOf(),
    /// used for sorting, here you input the approx download speed in kb/s
    val kbPerSec: Long = 1,
) : DownloadLinkType

data class DownloadExtractLink(
    override val url: String,
    override val name: String,
    val referer: String? = null,
    val headers: Map<String, String> = mapOf(),
    val params: Map<String, String> = mapOf(),
    val cookies: Map<String, String> = mapOf(),
) : DownloadLinkType

fun makeLinkSafe(url: String): String {
    return url.replace("http://", "https://")
}

@WorkerThread
suspend fun DownloadExtractLink.get(): NiceResponse {
    return app.get(makeLinkSafe(url), headers, referer, params, cookies)
}

@WorkerThread
suspend fun DownloadLink.get(): NiceResponse {
    return app.get(makeLinkSafe(url), headers, referer, params, cookies)
}

interface DownloadLinkType {
    val url: String
    val name: String
}

data class EpubResponse(
    override val url: String,
    override val name: String,
    override var author: String? = null,
    override var posterUrl: String? = null,
    @Deprecated("Use score instead")
    override var rating: Int? = null,
    override var peopleVoted: Int? = null,
    override var views: Int? = null,
    override var synopsis: String? = null,
    override var tags: List<String>? = null,
    override var status: ReleaseStatus? = null,
    override var posterHeaders: Map<String, String>? = null,
    var downloadLinks: List<DownloadLink>,
    var downloadExtractLinks: List<DownloadExtractLink>,
    override val apiName: String,
    override var related: List<SearchResponse>? = null,
    override var reviewData: String? = null,
    override var slopStatus: SlopStatus? = null,
    override var contentWarnings: List<String>? = null,
    override var genre: List<String>? = null,
) : LoadResponse

suspend fun MainAPI.newEpubResponse(
    name: String,
    url: String,
    links: List<DownloadLinkType>,
    fix: Boolean = true,
    initializer: suspend EpubResponse.() -> Unit = { },
): EpubResponse {
    val builder = EpubResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        downloadLinks = links.filterIsInstance<DownloadLink>().toList(),
        downloadExtractLinks = links.filterIsInstance<DownloadExtractLink>().toList()
    )
    builder.initializer()
    builder.posterHeaders = fixPosterHeaders(builder.posterHeaders)

    return builder
}

data class ChapterData(
    val name: String,
    val url: String,
    var dateOfRelease: String? = null,
    val views: Int? = null,
    //val regerer: String? = null
    //val index : Int,
)


fun MainAPI.newChapterData(
    name: String,
    url: String,
    fix: Boolean = true,
    initializer: ChapterData.() -> Unit = { },
): ChapterData {
    val builder = ChapterData(name = name, url = if (fix) fixUrl(url) else url)
    builder.initializer()

    return builder
}

/**
 * This is the primary way to store score/rating. Use Score.from or Score.from10 to parse the score
 * as it does not have a public constructor. Use toInt/toFloat to get back the score.
 *
 * Internally it stores it as an int up to 10^9 to represent up to 10 significant digits. So think
 * of this as a decimal class specifically for ratings.
 * */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@JvmInline
@Suppress("UNUSED")
value class Score private constructor(
    /** Decimal between [0, 10^9] representing the min score and max score respectively */
    @JsonProperty("data") private val data: Int,
) {
    /*@Deprecated(
        "toOld() is deprecated. Use other Score methods instead.",
        level = DeprecationLevel.ERROR
    )*/
    fun toOld(): Int = toInt(1000)

    fun toByte(maxScore: Int): Byte = toLong(maxScore).toByte()

    fun toInt(maxScore: Int = 10): Int = toLong(maxScore).toInt()

    fun toLong(maxScore: Int = 10): Long = (data.toLong() * maxScore.toLong()) / MAX.toLong()

    fun toFloat(maxScore: Int = 10): Float =
        (data.toFloat() / MAX.toFloat()) * maxScore.toFloat()

    fun toDouble(maxScore: Int = 10): Double =
        (data.toDouble() / MAX.toDouble()) * maxScore.toDouble()

    override fun toString(): String = this.toString(10)

    /** Formats the rating to a human-readable format (with no rounding)
     *
     * However, it may also return null if the score is less than the minimum score,
     * this is to avoid 0.0/10.0 in case of default = 0
     * */
    @Throws(IllegalArgumentException::class)
    fun toStringNull(
        minScore: Double,
        maxScore: Int,
        decimals: Int = 1,
        removeTrailingZeros: Boolean = true,
        decimalChar: Char = '.'
    ): String? {
        if (toDouble() < minScore) return null
        return toString(maxScore, decimals, removeTrailingZeros, decimalChar)
    }

    /** Formats the rating to a human-readable format (with no rounding) */
    @Throws(IllegalArgumentException::class)
    fun toString(
        maxScore: Int,
        decimals: Int = 1,
        removeTrailingZeros: Boolean = true,
        decimalChar: Char = '.'
    ): String {
        require(maxScore in 1..1000) {
            "maxScore ∈ [1,1000]"
        }
        require(decimals in 0..MAX_ZEROS) {
            "decimals ∈ [0,$MAX_ZEROS]"
        }
        var number = data.toLong() * maxScore.toLong()
        val chars = CharArray(MAX_ZEROS + 6)

        for (i in chars.indices) {
            chars[i] = (number % 10L).toInt().digitToChar()
            number /= 10L
        }

        var trailingZeros = MAX_ZEROS - decimals
        for (i in (MAX_ZEROS - decimals) until chars.size) {
            if (chars[i] != '0') {
                break
            }
            trailingZeros += 1
        }

        var leadingZeros = 0
        for (i in chars.indices.reversed()) {
            if (chars[i] != '0') {
                break
            }
            leadingZeros += 1
        }

        val stringBuilder = StringBuilder()
        for (i in maxOf(MAX_ZEROS, (chars.size - leadingZeros - 1)) downTo MAX_ZEROS) {
            stringBuilder.append(chars[i])
        }

        val end = if (removeTrailingZeros) {
            maxOf(MAX_ZEROS - decimals, trailingZeros)
        } else {
            MAX_ZEROS - decimals
        }

        if (end <= MAX_ZEROS - 1) {
            stringBuilder.append(decimalChar)
            for (i in MAX_ZEROS - 1 downTo end) {
                stringBuilder.append(chars[i])
            }
        }

        return stringBuilder.toString()
    }

    companion object {
        const val MAX: Int = 1000_000_000
        //const val MIN: Int = 0
        const val MAX_ZEROS: Int = 9
        //private const val TAG: String = "Score"

        /*@Deprecated(
            "Score.fromOld is deprecated. Use other Score.from* methods instead.",
            level = DeprecationLevel.ERROR
        )*/
        fun fromOld(value: Int?): Score? {
            if (value == null) return null
            if (value !in 0..1000) {
                //Log.w(TAG, "old: $value ∉ [0, 1000]")
                return null
            }
            return Score(value * 1_000_000)
        }

        /** `value ∈ [0, maxScore]` */
        fun from(value: Int?, maxScore: Int): Score? {
            if (value == null) {
                return null
            }
            if (value !in 0..maxScore) {
                //Log.w(TAG, "fromInt: $value ∉ [0, $maxScore]")
                return null
            }
            return Score((MAX / maxScore) * value)
        }

        /** `value ∈ [0.0, maxScore]` */
        fun from(value: Double?, maxScore: Int): Score? {
            if (value == null) {
                return null
            }
            if (value < 0.0 || value > maxScore) {
                //Log.w(TAG, "fromDouble: $value ∉ [0.0, $maxScore]")
                return null
            }
            return Score(((MAX / maxScore).toDouble() * value).roundToInt())
        }

        /** `value ∈ [0.0f, maxScore]` */
        fun from(value: Float?, maxScore: Int): Score? {
            if (value == null) {
                return null
            }
            if (value < 0.0 || value > maxScore) {
                //Log.w(TAG, "fromFloat: $value ∉ [0.0f, $maxScore]")
                return null
            }
            return Score(((MAX / maxScore).toFloat() * value).roundToInt())
        }

        /** `value ∈ ["0.0", maxScore]` */
        fun from(value: String?, maxScore: Int): Score? =
            from(value?.trim()?.toDoubleOrNull()?.absoluteValue, maxScore)

        /** `value ∈ [0, 5]` */
        fun from5(value: Int?): Score? = from(value, 5)

        /** `value ∈ [0, 10]` */
        fun from10(value: Int?): Score? = from(value, 10)

        /** `value ∈ [0, 100]` */
        fun from100(value: Int?): Score? = from(value, 100)

        /** `value ∈ [0.0, 5.0]` */
        fun from5(value: Double?): Score? = from(value, 5)

        /** `value ∈ [0.0, 10.0]` */
        fun from10(value: Double?): Score? = from(value, 10)

        /** `value ∈ [0.0, 100.0]` */
        fun from100(value: Double?): Score? = from(value, 100)

        /** `value ∈ [0.0f, 5.0f]` */
        fun from5(value: Float?): Score? = from(value, 5)

        /** `value ∈ [0.0f, 10.0f]` */
        fun from10(value: Float?): Score? = from(value, 10)

        /** `value ∈ [0.0f, 100.0f]` */
        fun from100(value: Float?): Score? = from(value, 100)

        /** `value ∈ ["0.0", "5.0"]` */
        fun from5(value: String?): Score? = from(value, 5)

        /** `value ∈ ["0.0", "10.0"]` */
        fun from10(value: String?): Score? = from(value, 10)

        /** `value ∈ ["0.0", "100.0"]` */
        fun from100(value: String?): Score? = from(value, 100)
    }
}