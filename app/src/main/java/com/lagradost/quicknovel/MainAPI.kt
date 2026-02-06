package com.lagradost.quicknovel

import androidx.annotation.StringRes
import androidx.annotation.WorkerThread
import com.lagradost.nicehttp.NiceResponse
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.img
import kotlinx.coroutines.sync.Mutex
import org.jsoup.Jsoup

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.93 Safari/537.36"

abstract class MainAPI {
    open val name = "NONE"
    open val mainUrl = "NONE"

    open val lang = "en" // ISO_639_1 check SubtitleHelper

    open val rateLimitTime: Long = 0
    val hasRateLimit: Boolean get() = rateLimitTime > 0L
    val rateLimitMutex: Mutex = Mutex()

    open val usesCloudFlareKiller = false

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
        showSpoilers: Boolean = false
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

fun stripHtml(
    txt: String,
    chapterName: String? = null,
    chapterIndex: Int? = null,
    stripAuthorNotes: Boolean
): String {
    val document = Jsoup.parse(txt)
    try {
        if(stripAuthorNotes) {
            document.select("div.qnauthornotecontainer").remove()
        }
        if (chapterName != null && chapterIndex != null) {
            for (a in document.allElements) {
                if (a != null && a.hasText() &&
                    (a.text() == chapterName || (a.tagName() == "h3" && a.text()
                        .startsWith("Chapter ${chapterIndex + 1}")))
                ) { // IDK, SOME MIGHT PREFER THIS SETTING??
                    a.remove() // THIS REMOVES THE TITLE
                    break
                }
            }
        }
    } catch (e: Exception) {
        logError(e)
    }

    return document.html()
        .replace(
            "<p>.*<strong>Translator:.*?Editor:.*>".toRegex(),
            ""
        ) // FUCK THIS, LEGIT IN EVERY CHAPTER
        .replace("<.*?Translator:.*?Editor:.*?>".toRegex(), "") // FUCK THIS, LEGIT IN EVERY CHAPTER

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
    val review: String,
    val reviewTitle: String?,
    val username: String?,
    val reviewDate: String?,
    val avatarUrl: String?,
    val rating: Int?,
    val ratings: List<Pair<Int, String>>?,
)
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
    var posterHeaders: Map<String, String>? = null
) {
    val image get() = img(posterUrl, posterHeaders)
}

fun MainAPI.newSearchResponse(
    name: String,
    url: String,
    fix: Boolean = true,
    initializer: SearchResponse.() -> Unit = { },
): SearchResponse {
    val builder =
        SearchResponse(name = name, url = if (fix) fixUrl(url) else url, apiName = this.name)
    builder.initializer()

    return builder
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
        "ongoing", "on-going", "on_going" -> ReleaseStatus.Ongoing
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
    var rating: Int?
    var peopleVoted: Int?
    var views: Int?
    var synopsis: String?
    var tags: List<String>?
    var status: ReleaseStatus? // 0 = null - implemented but not found, 1 = Ongoing, 2 = Complete, 3 = Pause/HIATUS, 4 = Dropped
    var posterHeaders: Map<String, String>?

    val image: UiImage? get() = img(url = posterUrl, headers = posterHeaders)
    val apiName: String
    var related: List<SearchResponse>?

    fun downloadImage(): UiImage? {
        val act = activity
        val bitmap = BookDownloader2Helper.getCachedBitmap(act, apiName, author, name)

        return if (bitmap == null) {
            img(url = posterUrl, headers = posterHeaders)
        } else {
            UiImage.Bitmap(bitmap)
        }
    }
}

data class StreamResponse(
    override val url: String,
    override val name: String,
    val data: List<ChapterData>,
    override val apiName: String,
    override var author: String? = null,
    override var posterUrl: String? = null,
    override var rating: Int? = null,
    override var peopleVoted: Int? = null,
    override var views: Int? = null,
    override var synopsis: String? = null,
    override var tags: List<String>? = null,
    override var status: ReleaseStatus? = null,
    override var posterHeaders: Map<String, String>? = null,
    var nextChapter: ChapterData? = null,
    override var related: List<SearchResponse>? = null
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
    override var related: List<SearchResponse>? = null
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