package com.lagradost.quicknovel.ui.common

/**
 * Immutable state for UI and state management.
 *
 * All state must be immutable and copied with "copy" for any operation modifying the state.
 *
 * This is because compose likes immutable state for UI, and makes it a lot easier to handle multithreading.
 *
 * 1. Lock free
 * 2. Fully immutable
 * 3. Exception free, no ConcurrentModificationException
 *
 * The labels describe how these classes are used and where.
 * */

import android.content.Context
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.transformations
import com.lagradost.cloudstream4.state.FilterByQuery
import com.lagradost.cloudstream4.state.SearchableData
import com.lagradost.cloudstream4.state.SortByName
import com.lagradost.cloudstream4.state.rev
import com.lagradost.cloudstream4.state.sortByFloat
import com.lagradost.cloudstream4.state.sortByInt
import com.lagradost.cloudstream4.state.sortByLong
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.generateId
import com.lagradost.quicknovel.BookDownloader2Helper.getFilenameIMG
import com.lagradost.quicknovel.BookDownloader2Helper.sanitizeFilename
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE
import com.lagradost.quicknovel.DOWNLOAD_OFFSET
import com.lagradost.quicknovel.DownloadExtractLink
import com.lagradost.quicknovel.DownloadLink
import com.lagradost.quicknovel.EPUB_BITSET_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_VISUAL_POSITION
import com.lagradost.quicknovel.EpubResponse
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ReleaseStatus
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.downloader.BookDownloadStatus
import com.lagradost.quicknovel.downloader.BookState
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.util.BlurTransformation
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import java.io.File
import java.util.BitSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.uuid.Uuid


/**
 * UI -> ViewModel
 *
 * Action on ImmutableSearchResponse */
enum class SearchResponseOperation {
    /** Open the item in a result view */
    Open,

    /** Read the epub */
    Read,

    /** Stream read the item */
    Stream,

    /** Ask the viewmodel to delete this item, from e.g. history or bookmarks. This should not delete the item directly */
    AskDelete,

    /** Actually delete this item */
    Delete,

    /** Show a popup of metadata */
    Metadata,

    /** Download the item */
    Download,

    /** Pause the download of the item */
    Pause,

    /** Resume the download of the item */
    Resume,

    /** Stop the download of this item */
    Stop,

    /** No operation */
    NoOp,
}

@Immutable
data class ImmutableChapterData(
    val name: String,
    val url: String,
    val dateOfRelease: String? = null,
    val views: Int? = null,
    val randomUuid: Uuid = Uuid.random(),
    val index: Int,
) {
    companion object {
        fun from(chapter: ChapterData, index: Int): ImmutableChapterData =
            ImmutableChapterData(
                name = chapter.name,
                url = chapter.url,
                dateOfRelease = chapter.dateOfRelease,
                views = chapter.views,
                index = index,
            )
    }
}

@Immutable
data class ImmutableReview(
    val content: String,
    val title: String? = null,
    val username: String? = null,
    val date: String? = null,
    val avatarUrl: String? = null,
    val avatarHeaders: PersistentMap<String, String>? = null,
    val rating: Int? = null,
    val ratings: PersistentList<Pair<Int, String>>? = null,
    val containsSpoilers: Boolean = false,
    val randomUuid: Uuid = Uuid.random()
) {
    companion object {
        fun from(review: UserReview): ImmutableReview =
            ImmutableReview(
                content = review.review,
                title = review.title,
                username = review.username,
                date = review.date,
                avatarUrl = review.avatarUrl,
                avatarHeaders = review.avatarHeaders?.toPersistentMap(),
                rating = review.rating,
                ratings = review.ratings?.toPersistentList(),
                containsSpoilers = review.containsSpoilers
            )
    }

    val imageRequest
        get() = @Composable {
            val context = LocalContext.current
            remember(context, avatarUrl, avatarHeaders) {
                ImageRequest(context)
            }
        }

    fun ImageRequest(context: Context): ImageRequest =
        ImageRequest.Builder(context)
            .data(avatarUrl)
            .httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                avatarHeaders?.forEach { (key, value) ->
                    headerBuilder[key] = value
                }
            }.build())
            .crossfade(true)
            .build()

}

@Immutable
data class ImmutableLoadData(
    val related: PersistentList<ImmutableSearchResponse>?,
    val status: ReleaseStatus?,
    val chapters: PersistentList<ImmutableChapterData>?,
    val views: Int?,
    val peopleVoted: Int?,

    // TODO make this better
    var downloadLinks: PersistentList<DownloadLink>?,
    var downloadExtractLinks: PersistentList<DownloadExtractLink>?,
)

/**
 * Viewmodel -> UI+Viewmodel
 *
 * The main class for representing a single "item" with a poster and name.
 *
 * This is the same for both downloads, imports, bookmarks, history and response.
 *
 * But with different fields non-null, the main is "id" where having an "id"
 * means we have at some time entered it, meaning it must be a "cached" value.
 *
 * The other main field is downloadState which is only non-null on the downloaded "page".
 * */
@Immutable
data class ImmutableSearchResponse(
    /** Name of the item */
    val name: String,
    /** API used for accessing the item */
    val apiName: String,
    /** Author written, this will be null on search items */
    val author: String? = null,
    /** The web url to OPEN the page */
    val url: String,
    /** The web url to the poster, use imageRequest instead */
    val posterUrl: String? = null,
    /** The web headers of the posterUrl, use imageRequest instead */
    val posterHeaders: ImmutableMap<String, String>? = null,
    /** The "score" or "rating" of the item */
    val rating: Int? = null,
    /** The latest chapter name to display */
    val latestChapterName: String? = null,
    /** This is a random uuid that is stable for e.g. using a key but not persistent between launches */
    val randomUuid: Uuid = Uuid.random(),
    /** The total chapter count, use chapters instead to also work on downloaded items */
    private val visualTotalChapters: Int? = null,
    /** The current download state, only present on "downloaded" items */
    val downloadState: ImmutableDownloadState? = null,
    /** The "synopsis" or "plot" of the item, absent for regular search results */
    val synopsis: String? = null,
    /** The tags of an item */
    val tags: ImmutableList<String>? = null,
    /** If the item is "loading" in some way, like generating an epub or having some misc loading state unrelated to "downloading" */
    val generating: Boolean = false,
    /** The ID of a card used for all keys, only non-null on regular search results */
    val id: Int? = null,
    /** The time we "cached"/"stored" the item and its metadata also known as "Recently refreshed" */
    val timeOfCached: Long,
    /** The time a "new" chapter got downloaded, also known as "Recently updated" */
    val timeOfChapterDownloaded: Long? = null,
    /** The time we opened the item in the full results view or read it, also known as "Recently opened" */
    val timeOfPageOpened: Long? = null,
    /** The size of the last written epub in chapters, aka how many chapters have we actually might have read */
    val epubSize: Int? = null,
    /** What the last chapter we read in the built-in reader was */
    val lastChaptersRead: Int,
    /** How many chapters we have read with the built-in reader */
    val chaptersRead: BitSet?,
    val loadData: ImmutableLoadData? = null,
    val reviewData: String? = null,
) {
    val chapters: Int? get() = loadData?.chapters?.size ?: visualTotalChapters
    val isImported: Boolean get() = (apiName == IMPORT_SOURCE || apiName == IMPORT_SOURCE_PDF)

    /**
     * The current offline download percentage with the offset,
     * aka 60(with offset = 0)/100 == 60(with offset = 4)/100 but the first 4 chapters may not be downloaded
     * */
    val downloadPercentage
        get() = downloadState?.let { state ->
            if (state.downloadedBytes > 0) {
                state.downloadedBytes.toFloat() / maxOf(state.totalBytes, 1L).toFloat()
            } else {
                state.totalDownloadedAfterOffset.toFloat() / maxOf(chapters ?: 1, 1).toFloat()
            }
        }

    val imageRequest
        get() = @Composable {
            val context = LocalContext.current
            remember(context, posterUrl, posterHeaders) {
                ImageRequest(context)
            }
        }
    val blurImageRequest
        get() = @Composable {
            val context = LocalContext.current
            remember(context, posterUrl, posterHeaders) {
                ImageRequest(context, radius = 100, sample = 3)
            }
        }

    fun ImageRequest(
        context: Context,
        radius: Int = 0,
        sample: Int = 3
    ): ImageRequest {
        val transformations = if (radius > 0) listOf(
            BlurTransformation(
                scale = sample.toFloat(),
                radius = radius
            )
        ) else emptyList()

        return if (isImported) {
            val file = File(
                context.filesDir.toString() + getFilenameIMG(
                    sanitizeFilename(apiName),
                    sanitizeFilename(author ?: ""),
                    sanitizeFilename(name)
                )
            )
            if (file.exists()) {
                val uri = file.toUri()
                ImageRequest.Builder(context)
                    .data(uri).error(R.drawable.error_poster)
                    .crossfade(true).transformations(transformations)
                    .build()
            } else {
                ImageRequest.Builder(context)
                    .data(R.drawable.error_poster)
                    .crossfade(true).transformations(transformations)
                    .build()
            }
        } else {
            ImageRequest.Builder(context)
                .data(posterUrl).error(R.drawable.error_poster)
                .httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                    posterHeaders?.forEach { (key, value) ->
                        headerBuilder[key] = value
                    }
                }.build())
                .crossfade(true).transformations(transformations)
                .build()
        }
    }

    fun toResultCached(
        id: Int,
    ): ResultCached {
        return ResultCached(
            source = url,
            name = name,
            apiName = apiName,
            id = id,
            author = author,
            poster = posterUrl,
            tags = tags,
            rating = rating,
            totalChapters = chapters ?: 1,
            cachedTime = System.currentTimeMillis(),
            synopsis = synopsis,
            posterHeaders = posterHeaders
        )
    }

    companion object {
        fun preview(): ImmutableSearchResponse = ImmutableSearchResponse(
            name = "hello world",
            apiName = "hello world",
            author = "author",
            url = "url",
            posterUrl = "https://www.royalroadcdn.com/public/covers-full/36735-the-perfect-run.jpg?time=${System.currentTimeMillis()}",
            posterHeaders = null,
            loadData = ImmutableLoadData(
                related = persistentListOf(),
                status = ReleaseStatus.Ongoing,
                chapters = persistentListOf(),
                views = 1337,
                peopleVoted = 42,
                downloadLinks = null,
                downloadExtractLinks = null,
            ),
            tags = persistentListOf(
                "tag 1",
                "tag 2",
                "tag 3",
                "tag 4",
                "tag 5",
                "tag 6",
                "tag 7",
                "tag 8",
                "tag 9",
                "Hello World",
                "More tags"
            ),
            synopsis = """Ryan "Quicksave" Romano is an eccentric adventurer with a strange power: he can create a save-point in time and redo his life whenever he dies. Arriving in New Rome, the glitzy capital of sin of a rebuilding Europe, he finds the city torn between mega-corporations, sponsored heroes, superpowered criminals, and true monsters. It's a time of chaos, where potions can grant the power to rule the world and dangers lurk everywhere. 

                Ryan only sees different routes; and from Hero to Villain, he has to try them all. Only then will he achieve his perfect ending... no matter how many loops it takes.
            """.trimIndent(),
            id = 0,
            rating = 123,
            latestChapterName = "hello world chapters",
            generating = false,
            lastChaptersRead = 0,
            timeOfCached = 0,
            chaptersRead = BitSet().apply { set(0); set(1); set(2) },
            downloadState = ImmutableDownloadState(
                downloadStatus = BookDownloadStatus.Idle,
                downloadedChapters = BitSet().apply { set(0); set(1); set(2) },
                downloadOffset = 0,
                estimateDownloadTime = 123.seconds,
            )
        )

        fun lastChaptersRead(name: String, id: Int?): Int =
            (id?.let { id -> getKey<Int>(EPUB_CURRENT_VISUAL_POSITION, id.toString()) }
                ?: getKey<Int>(EPUB_CURRENT_POSITION, name))?.let { it + 1 } ?: 0

        fun chaptersRead(id: Int): BitSet =
            getKey<ByteArray>(EPUB_BITSET_POSITION, id.toString())?.let { bytes ->
                BitSet.valueOf(
                    bytes
                )
            }
                ?: BitSet()

        fun addToHistory(response: ImmutableSearchResponse) {
            val id = response.id ?: return
            // we won't add it to history from cache
            setKey(
                HISTORY_FOLDER, id.toString(), response.toResultCached(id)
            )
        }

        fun timeOfPageOpened(id: Int): Long = getKey<Long>(
            DOWNLOAD_EPUB_LAST_ACCESS,
            id.toString(),
        ) ?: 0L

        fun epubSize(id: Int): Int = getKey(DOWNLOAD_EPUB_SIZE, id.toString()) ?: 0

        fun setTimeOfPageOpened(id: Int, value: Long) {
            setKey(
                DOWNLOAD_EPUB_LAST_ACCESS,
                id.toString(), value
            )
        }

        fun setEpubSize(id: Int, value: Int) {
            setKey(
                DOWNLOAD_EPUB_SIZE,
                id.toString(), value
            )
        }

        fun from(response: SearchResponse): ImmutableSearchResponse =
            ImmutableSearchResponse(
                name = response.name,
                url = response.url,
                posterUrl = response.posterUrl,
                rating = response.rating,
                latestChapterName = response.latestChapter,
                apiName = response.apiName,
                posterHeaders = response.posterHeaders?.toImmutableMap(),
                timeOfCached = System.currentTimeMillis(),
                lastChaptersRead = lastChaptersRead(response.name, null),
                chaptersRead = null
            )

        fun downloadState(id: Int) =
            BookState.downloadStatus(id)?.downloadState?.copy() ?: ImmutableDownloadState(
                downloadStatus = BookDownloadStatus.Idle,
                downloadOffset = getKey<Int>(DOWNLOAD_OFFSET, id.toString()) ?: 0,
                downloadedChapters = BitSet(),
            )

        fun from(response: LoadResponse): ImmutableSearchResponse {
            val id = generateId(response, response.apiName)
            val epubResponse = (response as? EpubResponse)
            val streamResponse = (response as? StreamResponse)

            val totalChapters = streamResponse?.data?.size ?: 1
            val currentDownloadProgress: ImmutableDownloadState =
                downloadState(id = id)

            return ImmutableSearchResponse(
                id = id,
                name = response.name,
                url = response.url,
                posterUrl = response.posterUrl,
                rating = response.rating,
                apiName = response.apiName,
                author = response.author,
                synopsis = response.synopsis,
                loadData = ImmutableLoadData(
                    related = response.related?.map { from(it) }?.toPersistentList(),
                    status = response.status,
                    chapters = streamResponse?.data?.mapIndexed { index, data ->
                        ImmutableChapterData.from(
                            data, index
                        )
                    }?.toPersistentList(),
                    downloadLinks = epubResponse?.downloadLinks?.toPersistentList(),
                    downloadExtractLinks = epubResponse?.downloadExtractLinks?.toPersistentList(),
                    views = response.views,
                    peopleVoted = response.peopleVoted,
                ),
                visualTotalChapters = totalChapters,
                timeOfPageOpened = timeOfPageOpened(id),
                reviewData = response.reviewData,
                tags = response.tags?.toPersistentList(),
                posterHeaders = response.posterHeaders?.toImmutableMap(),
                timeOfCached = System.currentTimeMillis(),
                lastChaptersRead = lastChaptersRead(response.name, id),
                epubSize = epubSize(id),
                downloadState = currentDownloadProgress,
                chaptersRead = chaptersRead(id)
            )
        }


        fun from(cache: ResultCached): ImmutableSearchResponse =
            ImmutableSearchResponse(
                name = cache.name,
                url = cache.source,
                posterUrl = cache.poster,
                posterHeaders = cache.posterHeaders?.toPersistentMap(),
                apiName = cache.apiName,
                rating = cache.rating,
                id = cache.id,
                timeOfCached = cache.cachedTime,
                visualTotalChapters = cache.totalChapters,
                author = cache.author,
                synopsis = cache.synopsis,
                timeOfPageOpened = timeOfPageOpened(cache.id),
                lastChaptersRead = lastChaptersRead(cache.name, cache.id),
                chaptersRead = chaptersRead(cache.id)
            )

        fun from(
            id: Int,
            cache: DownloadFragment.DownloadData,
            downloadState: ImmutableDownloadState? = null
        ) =
            ImmutableSearchResponse(
                name = cache.name,
                url = cache.source,
                posterUrl = cache.posterUrl,
                posterHeaders = cache.posterHeaders?.toPersistentMap(),
                apiName = cache.apiName,
                rating = cache.rating,
                id = id,
                timeOfCached = cache.lastUpdated ?: System.currentTimeMillis(),
                timeOfChapterDownloaded = cache.lastDownloaded,
                author = cache.author,
                downloadState = downloadState,
                synopsis = cache.synopsis,
                tags = cache.tags?.toImmutableList(),
                timeOfPageOpened = timeOfPageOpened(id),
                epubSize = epubSize(id),
                lastChaptersRead = lastChaptersRead(cache.name, id),
                chaptersRead = chaptersRead(id)
            )
    }

    fun doAction(operation: SearchResponseOperation) {
        when (operation) {
            SearchResponseOperation.Open -> {
                if (!isImported) {
                    loadResult(url, apiName)
                }
            }

            SearchResponseOperation.Metadata -> {
                MainActivity.loadPreviewPage(this)
            }

            SearchResponseOperation.NoOp -> {}
            else -> throw NotImplementedError()
        }
    }
}

/**
 * UI -> Viewmodel
 *
 * The response from the UI about an action on a ImmutableSearchResponse */
@Immutable
data class SearchResponseAction(
    val response: ImmutableSearchResponse,
    val operation: SearchResponseOperation,
) {
    fun doAction() {
        response.doAction(operation)
    }
}

@Immutable
data class ImmutableChapterProgress(
    val progress: Int,
    val total: Int
)

/**
 * Book downloader -> Viewmodel + UI
 *
 * The download state of a ImmutableSearchResponse */
@Immutable
data class ImmutableDownloadState(
    /*
    /** How many chapters/bytes much have we downloaded, not including skipped chapters */
    val progress: Long,
    /** How many have we actually downloaded */
    val downloaded: Long,
    */
    val downloadOffset: Int,
    val estimateDownloadTime: Duration? = null,
    /** When was this state updated */
    //val lastUpdatedMs: Long = System.currentTimeMillis(),
    /** What chapter is currently being downloaded */
    val chapterProgress: ImmutableChapterProgress? = null,
    /** What chapters we have downloaded */
    val downloadedChapters: BitSet,
    /** What chapters are pending in progress of being downloaded */
    val pendingChapters: BitSet = BitSet(),
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val downloadStatus: BookDownloadStatus = BookDownloadStatus.Idle,
    val error: Throwable? = null,
) {
    val totalDownloadedAfterOffset get() = downloadedChapters.nextClearBit(downloadOffset)

    /** How many milliseconds until this item is fully downloaded */
    val etaMs: Long? get() = estimateDownloadTime?.toLong(DurationUnit.MILLISECONDS)
    val estimateDownloadTimeFormat
        get() = estimateDownloadTime?.plus(999.milliseconds) // Round up: 0.1s -> 1.1s -> 1s
            ?.toComponents { hours, minutes, seconds, _ ->
                if (hours > 0) {
                    "${hours}h ${minutes}m"
                } else if (minutes > 0) {
                    "${minutes}m ${seconds}s"
                } else if (seconds > 0) {
                    "${seconds}s"
                } else {
                    null
                }
            }
    /*val downloaded: Long
        get() = if (downloadedBytes > 0L) downloadedBytes else downloadedChapters.length().toLong()
    val progress: Long
        get() = if (totalBytes > 0L) totalBytes else progressChapter?.toLong() ?: downloaded
     */


    /** Get the associated action */
    val action: DownloadStateAction get() = DownloadStateAction(downloadStatus)
}

@Immutable
data class DownloadStateAction(
    val status: BookDownloadStatus
) {
    val operation
        get() =
            when (status) {
                BookDownloadStatus.Downloading -> SearchResponseOperation.Stop
                BookDownloadStatus.Paused -> SearchResponseOperation.Resume
                BookDownloadStatus.Stopped -> SearchResponseOperation.Download
                BookDownloadStatus.Failed -> SearchResponseOperation.Download
                BookDownloadStatus.Done -> SearchResponseOperation.Download
                BookDownloadStatus.Pending -> SearchResponseOperation.NoOp
                BookDownloadStatus.Idle -> SearchResponseOperation.Download
            }

    val operationName
        get() =
            when (status) {
                BookDownloadStatus.Downloading -> R.string.stop
                BookDownloadStatus.Paused -> R.string.resume
                BookDownloadStatus.Stopped -> R.string.stopped
                BookDownloadStatus.Failed -> R.string.failed
                BookDownloadStatus.Done -> R.string.downloaded
                BookDownloadStatus.Pending -> R.string.loading
                BookDownloadStatus.Idle -> R.string.download
            }

    val icon
        get() = when (status) {
            BookDownloadStatus.Downloading -> R.drawable.stop_circle_24px
            BookDownloadStatus.Paused -> R.drawable.play_arrow_24px
            BookDownloadStatus.Stopped -> R.drawable.arrow_circle_down_24px
            BookDownloadStatus.Failed -> R.drawable.arrow_circle_down_24px
            BookDownloadStatus.Done -> R.drawable.check_24px
            BookDownloadStatus.Pending -> null
            BookDownloadStatus.Idle -> R.drawable.arrow_circle_down_24px
        }

    val iconBig
        get() = when (status) {
            BookDownloadStatus.Downloading -> R.drawable.ic_baseline_stop_24
            BookDownloadStatus.Paused -> R.drawable.netflix_play
            BookDownloadStatus.Stopped -> R.drawable.ic_sharp_clear_24
            BookDownloadStatus.Failed -> R.drawable.ic_sharp_clear_24
            BookDownloadStatus.Done -> R.drawable.ic_baseline_check_24
            BookDownloadStatus.Pending -> null
            BookDownloadStatus.Idle -> R.drawable.netflix_download
        }
}

/**
 * API -> Viewmodel
 * */
@Immutable
data class ImmutableHeadMainPageResponse(
    val url: String,
    val list: ImmutableList<ImmutableSearchResponse>,
) {
    companion object {
        fun from(response: HeadMainPageResponse) = ImmutableHeadMainPageResponse(
            url = response.url,
            list = response.list.map(ImmutableSearchResponse::from).toImmutableList()
        )
    }
}

/**
 * Viewmodel
 *
 * All sorting methods for sorting a ImmutableDownloadState
 * */
@Immutable
enum class SortingMethodType(val id: Int) {
    /** Default,  */
    Default(0),

    /** Ordering on the name */
    Alphabetical(1),
    RevAlphabetical(2),

    /** Download count, aka chapters downloaded */
    DownloadCount(3),
    RevDownloadCount(4),

    /** Download Percentage, aka chapters downloaded / total chapters or bytes */
    DownloadPercentage(5),
    RevDownloadPercentage(6),

    /** Last opened */
    LastOpened(7),
    RevLastOpened(8),

    /** Last update of *new* content */
    LastNewChapterDownloaded(9),
    RevLastNewChapterDownloaded(10),

    /**
     * In the case of downloads, this is the
     * Last update time the downloader was invoked,
     * even if no chapter was downloaded.
     *
     * For history this is when they are added
     * */
    LastCached(13),
    RevLastCached(14),
    ChapterCount(15),
    RevChapterCount(16);

    companion object {
        fun from(value: Int): SortingMethodType {
            return entries.firstOrNull { it.id == value } ?: Default
        }
    }
}

@Immutable
enum class ChapterSortingMethodType(val id: Int) {
    /** Default */
    Default(0),

    /** Ordering by index */
    Ascending(1),
    RevAscending(2),

    /** Ordering on the name */
    Alphabetical(3),
    RevAlphabetical(4);

    companion object {
        fun from(value: Int): ChapterSortingMethodType {
            return entries.firstOrNull { it.id == value } ?: Default
        }
    }
}

@Immutable
data class ChapterMethodPair(
    @StringRes val name: Int,
    val id: ChapterSortingMethodType,
    /** if inverse == id then it is a singular option */
    val inverse: ChapterSortingMethodType = id
)

val chapterSortingMethods = persistentListOf(
    ChapterMethodPair(R.string.default_sort, ChapterSortingMethodType.Default),
    ChapterMethodPair(
        R.string.release_sort,
        ChapterSortingMethodType.Ascending,
        ChapterSortingMethodType.RevAscending
    ),
    ChapterMethodPair(
        R.string.alpha_sort,
        ChapterSortingMethodType.Alphabetical,
        ChapterSortingMethodType.RevAlphabetical
    ),
)

@Immutable
enum class ChapterFilterMethodType(val id: Int) {
    /** Filter by download status */
    Downloaded(1),
    UnDownloaded(2),

    /** Filter by read */
    Read(3),
    UnRead(4),

    /** Filter by bookmark status */
    Bookmark(5),
    UnBookmark(6);

    companion object {
        fun from(value: Int): ChapterFilterMethodType? {
            return entries.firstOrNull { it.id == value }
        }
    }
}

/**
 * UI
 *
 * The UI options for selecting a sorting method
 * */
@Immutable
data class SortingMethodPair(
    @StringRes val name: Int,
    val id: SortingMethodType,
    /** if inverse == id then it is a singular option */
    val inverse: SortingMethodType = id
)

/**
 * UI
 *
 * Sorting methods for downloaded ImmutableDownloadState
 * */
val sortingMethods = persistentListOf(
    SortingMethodPair(R.string.default_sort, SortingMethodType.Default),
    SortingMethodPair(
        R.string.recently_sort,
        SortingMethodType.LastOpened,
        SortingMethodType.RevLastOpened
    ),
    SortingMethodPair(
        R.string.recently_updated_sort,
        SortingMethodType.LastNewChapterDownloaded,
        SortingMethodType.RevLastNewChapterDownloaded
    ),
    SortingMethodPair(
        R.string.recently_checked_sort,
        SortingMethodType.LastCached,
        SortingMethodType.RevLastCached
    ),

    SortingMethodPair(
        R.string.alpha_sort,
        SortingMethodType.Alphabetical,
        SortingMethodType.RevAlphabetical
    ),
    SortingMethodPair(
        R.string.download_sort,
        SortingMethodType.DownloadCount,
        SortingMethodType.RevDownloadCount
    ),
    SortingMethodPair(
        R.string.download_perc, SortingMethodType.DownloadPercentage,
        SortingMethodType.RevDownloadPercentage
    ),
    SortingMethodPair(
        R.string.chapters, SortingMethodType.ChapterCount,
        SortingMethodType.RevChapterCount
    ),
)

/**
 * UI
 *
 * Sorting methods for any ImmutableDownloadState */
val normalSortingMethods = persistentListOf(
    SortingMethodPair(R.string.default_sort, SortingMethodType.Default),
    SortingMethodPair(
        R.string.recently_sort,
        SortingMethodType.LastOpened,
        SortingMethodType.RevLastOpened
    ),
    /*SortingMethodPair(
        R.string.recently_visisted_sort,
        SortingMethodType.LastCached,
        SortingMethodType.RevLastCached
    ),*/
    SortingMethodPair(
        R.string.alpha_sort,
        SortingMethodType.Alphabetical,
        SortingMethodType.RevAlphabetical
    ),
    SortingMethodPair(
        R.string.chapters, SortingMethodType.ChapterCount,
        SortingMethodType.RevChapterCount
    )
)

/**
 * Viewmodel
 *
 * Updates a singular index of a PersistentList with the new value, returning the update state
 * */
@CheckResult
fun <T> PersistentList<T>.updateRow(
    index: Int,
    update: T.() -> T
): PersistentList<T> {
    val item = this.getOrNull(index) ?: return this
    val newItem = update(item)
    return this.replacingAt(index, newItem)
}

/**
 * Viewmodel
 *
 * Updates a singular ImmutableSearchResponse of a PersistentList with the new value, returning the update state
 *
 * This is done by updating based on the url, as that is assumed to be unique
 * */
@CheckResult
fun PersistentList<ImmutableSearchResponse>.updateItem(
    item: ImmutableSearchResponse,
    update: ImmutableSearchResponse.() -> ImmutableSearchResponse
): PersistentList<ImmutableSearchResponse> {
    val index = this.indexOfFirst { it.url == item.url }
    if (index == -1) return this
    val newItem = update(this[index])
    return this.replacingAt(index, newItem)
}

/**
 * Viewmodel
 *
 * Update each item inside the PersistentList using the update function
 *
 * */
@CheckResult
fun <T> PersistentList<T>.updateRows(
    update: T.(Int) -> T
): PersistentList<T> {
    return this.mapIndexed { index, t -> t.update(index) }.toPersistentList()
}

data class FilterChapters(
    val query: FilterByQuery<ImmutableChapterData> = FilterByQuery<ImmutableChapterData>(query = "") { it.name },
    val filterMethodType: PersistentSet<ChapterFilterMethodType> = persistentSetOf(
        ChapterFilterMethodType.Downloaded,
        ChapterFilterMethodType.UnDownloaded
    ),
    val downloaded: BitSet = BitSet(),
    val read: BitSet = BitSet(),
    val bookmark: BitSet = BitSet()
) : Function1<ImmutableChapterData, Boolean> {
    override fun invoke(data: ImmutableChapterData): Boolean =
        query(data)
                && filterMethodType.any {
            when (it) {
                ChapterFilterMethodType.Downloaded -> downloaded.get(data.index)
                ChapterFilterMethodType.UnDownloaded -> !downloaded.get(data.index)
                ChapterFilterMethodType.Read -> read.get(data.index)
                ChapterFilterMethodType.UnRead -> !read.get(data.index)
                ChapterFilterMethodType.Bookmark -> bookmark.get(data.index)
                ChapterFilterMethodType.UnBookmark -> !bookmark.get(data.index)
            }
        }
}

/**
 * Contrary to the SearchList, the chapter data will be truly immutable
 * therefore we use the PersistentList<ImmutableChapterData> instead of passing by index/id
 * */
@Immutable
data class ImmutableChapterList(
    private val internalData: SearchableData<Int, ImmutableChapterData> = SearchableData(
        data = persistentMapOf(),
        filtered = persistentSetOf(),
        sorted = persistentListOf(),
        filteredBy = null,
        sortedBy = SortByName { it.name }
    ),
    val sortingMethod: ChapterSortingMethodType = ChapterSortingMethodType.Default,
    val filtering: FilterChapters = FilterChapters(),
) {
    val data get() = internalData.data
    val sorted get() = internalData.sorted
    val filtered get() = internalData.filtered

    @CheckResult
    fun filterBy(
        newFilter: FilterChapters
    ): ImmutableChapterList =
        copy(internalData = internalData.filteringBy(newFilter = newFilter), filtering = newFilter)

    @CheckResult
    fun sortBy(
        newSorting: ChapterSortingMethodType
    ): ImmutableChapterList =
        copy(
            internalData = internalData.sortingBy(newSorting = getSortingMethod(newSorting)),
            sortingMethod = newSorting
        )

    companion object {
        private val Ascending = sortByInt<ImmutableChapterData> { chapter -> chapter.index }
        private val RevAscending =
            sortByInt<ImmutableChapterData> { chapter -> chapter.index }.rev()
        private val Alphabetical = SortByName<ImmutableChapterData> { chapter -> chapter.name }
        private val RevAlphabetical =
            SortByName<ImmutableChapterData> { chapter -> chapter.name }.rev()

        private fun getSortingMethod(method: ChapterSortingMethodType): Comparator<ImmutableChapterData> {
            return when (method) {
                ChapterSortingMethodType.Default, ChapterSortingMethodType.Ascending -> Ascending
                ChapterSortingMethodType.RevAscending -> RevAscending
                ChapterSortingMethodType.Alphabetical -> Alphabetical
                ChapterSortingMethodType.RevAlphabetical -> RevAlphabetical
            }
        }

        @CheckResult
        fun from(
            chapters: PersistentList<ImmutableChapterData>,
            sortBy: ChapterSortingMethodType = ChapterSortingMethodType.Default,
            filteredBy: FilterChapters
        ): ImmutableChapterList = ImmutableChapterList(
            sortingMethod = sortBy,
            filtering = filteredBy,
            internalData = SearchableData.from(
                data = chapters.associateBy { it.index }.toPersistentMap(),
                sortedBy = getSortingMethod(sortBy),
                filteredBy = filteredBy
            )
        )
    }

    /*@CheckResult
    fun search(
        query: String = this.query,
        sortingMethod: ChapterSortingMethodType = this.sortingMethod
    ): ImmutableChapterList {
        val sameQuery = this.query == query
        val sameSorting = this.sortingMethod == sortingMethod

        val filtered = if (sameQuery) {
            filtered
        } else {
            filterList(data, query)
        }
        val sorted = if (sameSorting && sameQuery) {
            sorted
        } else {
            sortList(filtered, sortingMethod)
        }

        return this.copy(
            filtered = filtered,
            sorted = sorted,
            query = query,
            sortingMethod = sortingMethod,
        )
    }

    companion object {
        @CheckResult
        fun filterList(
            data: PersistentList<ImmutableChapterData>,
            query: String
        ): PersistentList<ImmutableChapterData> {
            if (query.trim().length < 2) return data
            return data.removingAll { item -> !item.matchesQuery(query) }
        }

        @CheckResult
        fun sortList(
            list: PersistentList<ImmutableChapterData>,
            method: ChapterSortingMethodType
        ): PersistentList<ImmutableChapterData> {
            val sorted = when (method) {
                ChapterSortingMethodType.Default, ChapterSortingMethodType.Ascending -> list
                ChapterSortingMethodType.RevAscending -> list.asReversed().toPersistentList()
                ChapterSortingMethodType.Alphabetical -> list.sortedBy { it.name }
                    .toPersistentList()

                ChapterSortingMethodType.RevAlphabetical -> list.sortedByDescending { it.name }
                    .toPersistentList()
            }

            return sorted
        }

        @CheckResult
        fun new(
            items: PersistentList<ImmutableChapterData>,
            query: String,
            sortingMethod: ChapterSortingMethodType
        ): ImmutableChapterList {
            val filtered = filterList(items, query)
            val sorted = sortList(filtered, sortingMethod)
            return ImmutableChapterList(
                data = items,
                filtered = filtered,
                sorted = sorted,
                query = query,
                sortingMethod = sortingMethod
            )
        }
    }*/
}

/**
 * Viewmodel -> UI
 *
 * The base container for locally searchable items.
 *
 * This includes options for both filtering with a query, and sorting it by a SortingMethodType
 * */
@Immutable
data class ImmutableSearchList(
    private val internalData: SearchableData<Int, ImmutableSearchResponse> = SearchableData(
        data = persistentMapOf(),
        filtered = persistentSetOf(),
        sorted = persistentListOf(),
        filteredBy = null,
        sortedBy = SortByName { it.name }
    ),
    val query: String = "",
    val sortingMethod: SortingMethodType = SortingMethodType.Default,
) {
    val data = internalData.data

    //val filtered = internalData.filtered
    val sorted = internalData.sorted

    @CheckResult
    fun delete(id: Int): ImmutableSearchList {
        return copy(internalData = internalData.removing(id))
    }

    @CheckResult
    fun insert(
        id: Int,
        newItem: ImmutableSearchResponse
    ): ImmutableSearchList {
        return copy(internalData = internalData.adding(id, newItem))
    }

    @CheckResult
    fun update(
        id: Int,
        updater: ImmutableSearchResponse.() -> ImmutableSearchResponse
    ): ImmutableSearchList {
        return copy(internalData = internalData.updating(id, updater))
    }

    @CheckResult
    fun search(
        query: String = this.query,
        sortingMethod: SortingMethodType = this.sortingMethod
    ): ImmutableSearchList {
        val sameQuery = this.query == query
        val sameSorting = this.sortingMethod == sortingMethod
        if (sameQuery && sameSorting) {
            return this
        }

        return copy(
            internalData = internalData.searchBy(
                newSorting = getSortingMethod(sortingMethod),
                newFilter = getQuery(query),
            ),
            sortingMethod = sortingMethod,
            query = query
        )
    }

    companion object {
        private val Alphabetical = SortByName<ImmutableSearchResponse> { it.name }
        private val RevAlphabetical = SortByName<ImmutableSearchResponse> { it.name }.rev()
        private val DownloadCount =
            sortByInt<ImmutableSearchResponse> {
                it.downloadState?.downloadedChapters?.length() ?: 0
            }.rev()
        private val RevDownloadCount =
            sortByInt<ImmutableSearchResponse> {
                it.downloadState?.downloadedChapters?.length() ?: 0
            }
        private val DownloadPercentage = sortByFloat<ImmutableSearchResponse> {
            it.downloadPercentage ?: 0.0f
        }.rev()
        private val RevDownloadPercentage =
            sortByFloat<ImmutableSearchResponse> { it.downloadPercentage ?: 0.0f }
        private val LastOpened =
            sortByLong<ImmutableSearchResponse> { it.timeOfPageOpened ?: 0L }.rev()
        private val RevLastOpened =
            sortByLong<ImmutableSearchResponse> { it.timeOfPageOpened ?: 0L }
        private val LastNewChapterDownloaded =
            sortByLong<ImmutableSearchResponse> { it.timeOfChapterDownloaded ?: 0L }.rev()
        private val RevLastNewChapterDownloaded =
            sortByLong<ImmutableSearchResponse> { it.timeOfChapterDownloaded ?: 0L }
        private val LastCached = sortByLong<ImmutableSearchResponse> { it.timeOfCached }.rev()
        private val RevLastCached = sortByLong<ImmutableSearchResponse> { it.timeOfCached }
        private val ChapterCount = sortByInt<ImmutableSearchResponse> { it.chapters ?: 0 }.rev()
        private val RevChapterCount = sortByInt<ImmutableSearchResponse> { it.chapters ?: 0 }
        private val DefaultQuery = FilterByQuery<ImmutableSearchResponse>("") { it.name }

        private fun getQuery(query: String): FilterByQuery<ImmutableSearchResponse>? {
            return if (query.length < 2) {
                null
            } else {
                DefaultQuery.update(query)
            }
        }

        private fun getSortingMethod(method: SortingMethodType): Comparator<ImmutableSearchResponse> {
            return when (method) {
                SortingMethodType.Alphabetical -> Alphabetical

                SortingMethodType.RevAlphabetical -> RevAlphabetical

                SortingMethodType.DownloadCount -> DownloadCount

                SortingMethodType.RevDownloadCount -> RevDownloadCount

                SortingMethodType.DownloadPercentage -> DownloadPercentage

                SortingMethodType.RevDownloadPercentage -> RevDownloadPercentage

                SortingMethodType.Default, SortingMethodType.LastOpened -> LastOpened

                SortingMethodType.RevLastOpened -> RevLastOpened

                SortingMethodType.LastNewChapterDownloaded -> LastNewChapterDownloaded

                SortingMethodType.RevLastNewChapterDownloaded -> RevLastNewChapterDownloaded

                SortingMethodType.LastCached -> LastCached

                SortingMethodType.RevLastCached -> RevLastCached

                SortingMethodType.ChapterCount -> ChapterCount

                SortingMethodType.RevChapterCount -> RevChapterCount
            }
        }

        @CheckResult
        fun new(
            items: PersistentMap<Int, ImmutableSearchResponse>,
            query: String,
            sortingMethod: SortingMethodType
        ): ImmutableSearchList {
            return ImmutableSearchList(
                query = query,
                sortingMethod = sortingMethod,
                internalData = SearchableData.from(
                    data = items,
                    sortedBy = getSortingMethod(sortingMethod),
                    filteredBy = getQuery(query)
                )
            )
        }
    }
}
