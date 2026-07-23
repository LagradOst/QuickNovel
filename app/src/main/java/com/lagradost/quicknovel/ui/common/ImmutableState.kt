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
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.getFilenameIMG
import com.lagradost.quicknovel.BookDownloader2Helper.sanitizeFilename
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE
import com.lagradost.quicknovel.DefaultLibrary
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.ui.download.DownloadFragment
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
import kotlinx.collections.immutable.toPersistentSet
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


/**
 * UI -> ViewModel
 *
 * Action on ImmutableSearchResponse */
enum class SearchResponseOperation {
    /** Open the item in a result view */
    Open,

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
}

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
data class ImmutableSearchResponse @ExperimentalUuidApi constructor(
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
    private val totalChapters: Long? = null,
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
    /** The time we actually read the item, also known as "Recently opened" */
    val timeOfPageOpened: Long? = null,
    /** The size of the last written epub in chapters, aka how many chapters have we actually might have read */
    val epubSize: Int? = null,
    /** How many chapters we have read with the built-in reader */
    val chaptersRead: Int,
) {

    fun matchesQuery(query: String): Boolean =
        FuzzySearch.partialRatio(name.lowercase(), query) > 50

    val chapters: Long? get() = totalChapters ?: downloadState?.total
    val isImported: Boolean get() = (apiName == IMPORT_SOURCE || apiName == IMPORT_SOURCE_PDF)
    val hasNewChapters: Boolean get() = downloadState != null && epubSize != null && !isImported && epubSize < downloadState.progress

    val imageRequest
        get() = @Composable {
            val context = LocalContext.current
            remember(context) {
                if (isImported) {
                    (context.filesDir.toString() + getFilenameIMG(
                        sanitizeFilename(apiName),
                        sanitizeFilename(author ?: ""),
                        sanitizeFilename(name)
                    )).toUri()
                } else {
                    ImageRequest.Builder(context)
                        .data(posterUrl)
                        .httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                            posterHeaders?.forEach { (key, value) ->
                                headerBuilder[key] = value
                            }
                        }.build()) // Set the headers here
                        .crossfade(true)
                        .build()
                }
            }
        }

    companion object {
        fun chaptersRead(name: String): Int =
            getKey<Int>(EPUB_CURRENT_POSITION, name)?.let { it + 1 } ?: 0

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

        @OptIn(ExperimentalUuidApi::class)
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
                chaptersRead = chaptersRead(response.name)
            )

        @OptIn(ExperimentalUuidApi::class)
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
                totalChapters = cache.totalChapters.toLong(),
                author = cache.author,
                synopsis = cache.synopsis,
                timeOfPageOpened = timeOfPageOpened(cache.id),
                chaptersRead = chaptersRead(cache.name)
            )

        @OptIn(ExperimentalUuidApi::class)
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
                chaptersRead = chaptersRead(cache.name)
            )
    }

    fun doAction(operation: SearchResponseOperation) {
        when (operation) {
            SearchResponseOperation.Open -> loadResult(url, apiName)
            SearchResponseOperation.Metadata -> {
                MainActivity.loadPreviewPage(this)
            }

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

/**
 * Book downloader -> Viewmodel + UI
 *
 * The download state of a ImmutableSearchResponse */
@Immutable
data class ImmutableDownloadState(
    val status: DownloadState,
    /** How many chapters/bytes much have we downloaded, not including skipped chapters */
    val progress: Long,
    /** How many have we actually downloaded */
    val downloaded: Long,
    /** How many is there in total */
    val total: Long,
    /** When was this state updated */
    val lastUpdatedMs: Long,
    /** How many milliseconds until this item is fully downloaded */
    val etaMs: Long?
) {
    val downloadPercentage get() = downloaded.toFloat() / maxOf(total, 1)
    val progressPercentage get() = progress.toFloat() / maxOf(total, 1)

    companion object {
        fun from(state: DownloadProgressState) = ImmutableDownloadState(
            status = state.state,
            progress = state.progress,
            downloaded = state.downloaded,
            total = state.total,
            lastUpdatedMs = state.lastUpdatedMs,
            etaMs = state.etaMs
        )
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
    SortingMethodPair(
        R.string.alpha_sort,
        SortingMethodType.Alphabetical,
        SortingMethodType.RevAlphabetical
    ),
    SortingMethodPair(
        R.string.chapters, SortingMethodType.ChapterCount,
        SortingMethodType.RevChapterCount
    ),
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

/**
 * Viewmodel -> UI
 *
 * The base container for locally searchable items.
 *
 * This includes options for both filtering with a query, and sorting it by a SortingMethodType
 * */
@Immutable
data class ImmutableSearchList(
    val data: PersistentMap<Int, ImmutableSearchResponse> = persistentMapOf(),
    private val filtered: PersistentSet<Int> = persistentSetOf(),
    val sorted: PersistentList<Int> = persistentListOf(),
    val query: String = "",
    val sortingMethod: SortingMethodType = SortingMethodType.Default,
) {
    @CheckResult
    fun delete(id: Int): ImmutableSearchList {
        if (!data.contains(id)) {
            return this
        }

        return copy(
            data = data.removing(id),
            filtered = filtered.removing(id),
            sorted = if (filtered.contains(id)) sorted.removing(id) else sorted
        )
    }

    @CheckResult
    fun insert(
        id: Int,
        newItem: ImmutableSearchResponse
    ): ImmutableSearchList {
        val item = data[id]

        val passesFilter = (skipQuery(query) || newItem.matchesQuery(query))
        val newData = data.putting(id, newItem)
        val newFiltered = if (passesFilter) {
            filtered.adding(id)
        } else {
            filtered
        }
        val newSorted =
            if (passesFilter && (item == null || shouldUpdateItem(sortingMethod, item, newItem))) {
                sortList(newData, newFiltered, sortingMethod)
            } else {
                sorted
            }

        return copy(
            data = newData,
            filtered = newFiltered,
            sorted = newSorted
        )
    }

    @CheckResult
    fun update(
        id: Int,
        updater: ImmutableSearchResponse.() -> ImmutableSearchResponse
    ): ImmutableSearchList {
        val item = this.data[id] ?: return this
        val newItem = updater(item)
        val newData = data.putting(id, newItem)

        val newSorted =
            if (filtered.contains(id) && shouldUpdateItem(sortingMethod, item, newItem)) {
                sortList(newData, filtered, sortingMethod)
            } else {
                sorted
            }

        return copy(
            data = newData,
            sorted = newSorted
        )
    }

    @CheckResult
    fun search(
        query: String = this.query,
        sortingMethod: SortingMethodType = this.sortingMethod
    ): ImmutableSearchList {
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
            sortList(data, filtered, sortingMethod)
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
        fun shouldUpdateItem(
            sortingMethod: SortingMethodType,
            item: ImmutableSearchResponse,
            newItem: ImmutableSearchResponse
        ) = when (sortingMethod) {
            SortingMethodType.RevAlphabetical, SortingMethodType.Alphabetical -> newItem.name != item.name
            SortingMethodType.DownloadCount, SortingMethodType.RevDownloadCount -> {
                newItem.downloadState?.downloaded != item.downloadState?.downloaded
                        // Do not spam with update notifications for downloading items, as that is frequent
                        && newItem.downloadState?.status != DownloadState.IsDownloading
            }

            SortingMethodType.DownloadPercentage, SortingMethodType.RevDownloadPercentage -> {
                newItem.downloadState?.downloadPercentage != item.downloadState?.downloadPercentage
                        // Do not spam with update notifications for downloading items, as that is frequent
                        && newItem.downloadState?.status != DownloadState.IsDownloading
            }

            SortingMethodType.Default, SortingMethodType.LastOpened, SortingMethodType.RevLastOpened -> {
                newItem.timeOfPageOpened != item.timeOfPageOpened
            }

            SortingMethodType.LastNewChapterDownloaded, SortingMethodType.RevLastNewChapterDownloaded -> {
                newItem.timeOfChapterDownloaded != item.timeOfChapterDownloaded
            }

            SortingMethodType.LastCached, SortingMethodType.RevLastCached -> {
                newItem.timeOfCached != item.timeOfCached
            }

            SortingMethodType.ChapterCount, SortingMethodType.RevChapterCount -> item.chapters != newItem.chapters
        }

        @CheckResult
        fun skipQuery(query: String) = query.trim().length < 2

        @CheckResult
        fun filterList(
            data: PersistentMap<Int, ImmutableSearchResponse>,
            query: String
        ): PersistentSet<Int> {
            val set = data.keys.toPersistentSet()
            if (query.trim().length < 2) return set
            return set.removingAll { data[it]?.matchesQuery(query) != true }
        }

        @CheckResult
        fun sortList(
            data: PersistentMap<Int, ImmutableSearchResponse>,
            list: PersistentSet<Int>,
            method: SortingMethodType
        ): PersistentList<Int> {
            val sorted = when (method) {
                SortingMethodType.Alphabetical -> {
                    list.sortedBy { data[it]?.name ?: "" }
                }

                SortingMethodType.RevAlphabetical -> {
                    list.sortedByDescending { data[it]?.name ?: "" }
                }

                SortingMethodType.DownloadCount -> {
                    list.sortedByDescending { data[it]?.downloadState?.downloaded ?: 0 }
                }

                SortingMethodType.RevDownloadCount -> {
                    list.sortedBy { data[it]?.downloadState?.downloaded ?: 0 }
                }

                SortingMethodType.DownloadPercentage -> {
                    list.sortedByDescending { data[it]?.downloadState?.downloadPercentage ?: 0.0f }
                }

                SortingMethodType.RevDownloadPercentage -> {
                    list.sortedBy { data[it]?.downloadState?.downloadPercentage ?: 0.0f }
                }

                SortingMethodType.Default, SortingMethodType.LastOpened -> {
                    list.sortedByDescending { data[it]?.timeOfPageOpened ?: 0L }
                }

                SortingMethodType.RevLastOpened -> {
                    list.sortedBy { data[it]?.timeOfPageOpened ?: 0L }
                }

                SortingMethodType.LastNewChapterDownloaded -> {
                    list.sortedByDescending { data[it]?.timeOfChapterDownloaded ?: 0L }
                }

                SortingMethodType.RevLastNewChapterDownloaded -> {
                    list.sortedBy { data[it]?.timeOfChapterDownloaded ?: 0L }
                }

                SortingMethodType.LastCached -> {
                    list.sortedByDescending { data[it]?.timeOfCached ?: 0L }
                }

                SortingMethodType.RevLastCached -> {
                    list.sortedBy { data[it]?.timeOfCached ?: 0L }
                }

                SortingMethodType.ChapterCount -> {
                    list.sortedByDescending { data[it]?.chapters ?: 0L }
                }

                SortingMethodType.RevChapterCount -> {
                    list.sortedBy { data[it]?.chapters ?: 0L }
                }
            }

            return sorted.toPersistentList()
        }

        @CheckResult
        fun new(
            items: PersistentMap<Int, ImmutableSearchResponse>,
            query: String,
            sortingMethod: SortingMethodType
        ): ImmutableSearchList {
            val filtered = filterList(items, query)
            val sorted = sortList(items, filtered, sortingMethod)
            return ImmutableSearchList(
                data = items,
                filtered = filtered,
                sorted = sorted,
                query = query,
                sortingMethod = sortingMethod
            )
        }
    }
}
