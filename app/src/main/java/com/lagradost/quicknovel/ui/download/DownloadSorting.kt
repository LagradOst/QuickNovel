package com.lagradost.quicknovel.ui.download

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.ImmutableDownloadState
import com.lagradost.quicknovel.ImmutableSearchResponse
import com.lagradost.quicknovel.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi

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
data class SortingMethodPair(
    @StringRes val name: Int,
    val id: SortingMethodType,
    val inverse: SortingMethodType = id
)

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

fun <T> PersistentList<T>.updateRow(
    index: Int,
    update: T.() -> T
): PersistentList<T> {
    val item = this.getOrNull(index) ?: return this
    val newItem = update(item)
    return this.replacingAt(index, newItem)
}

fun PersistentList<ImmutableSearchResponse>.updateItem(
    item: ImmutableSearchResponse,
    update: ImmutableSearchResponse.() -> ImmutableSearchResponse
): PersistentList<ImmutableSearchResponse> {
    val index = this.indexOfFirst { it.url == item.url }
    if (index == -1) return this
    val newItem = update(this[index])
    return this.replacingAt(index, newItem)
}

fun <T> PersistentList<T>.updateRows(
    update: T.(Int) -> T
): PersistentList<T> {
    return this.mapIndexed { index, t -> t.update(index) }.toPersistentList()
}

@Immutable
data class ImmutableSearchList(
    val data: PersistentMap<Int, ImmutableSearchResponse> = persistentMapOf(),
    val filtered: PersistentSet<Int> = persistentSetOf(),
    val sorted: PersistentList<Int> = persistentListOf(),
    val query: String = "",
    val sortingMethod: SortingMethodType = SortingMethodType.Default,
) {
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
        fun shouldUpdateItem(
            sortingMethod: SortingMethodType,
            item: ImmutableSearchResponse,
            newItem: ImmutableSearchResponse
        ) = when (sortingMethod) {
            SortingMethodType.RevAlphabetical, SortingMethodType.Alphabetical -> newItem.name != item.name
            SortingMethodType.DownloadCount, SortingMethodType.RevDownloadCount -> {
                newItem.downloadState?.downloaded != item.downloadState?.downloaded
                        // Do not spam with update notifications for downloading items, as that is frequent
                        && newItem.downloadState?.state != DownloadState.IsDownloading
            }

            SortingMethodType.DownloadPercentage, SortingMethodType.RevDownloadPercentage -> {
                newItem.downloadState?.downloadPercentage != item.downloadState?.downloadPercentage
                        // Do not spam with update notifications for downloading items, as that is frequent
                        && newItem.downloadState?.state != DownloadState.IsDownloading
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

            SortingMethodType.ChapterCount, SortingMethodType.RevChapterCount -> (((item.totalChapters?.toLong()
                ?: item.downloadState?.total) != (newItem.totalChapters?.toLong()
                ?: newItem.downloadState?.total)))
        }

        fun skipQuery(query: String) = query.trim().length < 2

        fun filterList(
            data: PersistentMap<Int, ImmutableSearchResponse>,
            query: String
        ): PersistentSet<Int> {
            val set = data.keys.toPersistentSet()
            if (query.trim().length < 2) return set
            return set.removingAll { data[it]?.matchesQuery(query) != true }
        }

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
                    list.sortedByDescending { data[it]?.totalChapters?.toLong() ?: data[it]?.downloadState?.total ?: 0L }
                }
                SortingMethodType.RevChapterCount -> {
                    list.sortedBy { data[it]?.totalChapters?.toLong() ?: data[it]?.downloadState?.total ?: 0L }
                }
            }

            return sorted.toPersistentList()
        }

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

/*
fun PersistentList<DownloadRow>.updateSearchItem(
    index: Int,
    newItem: ImmutableSearchResponse,
): PersistentList<DownloadRow> {
    val row = this.getOrNull(index) ?: return this

    val updateIndex = row.row.indexOfFirst { item -> item.id == newItem.id }
    val items = if (updateIndex == -1) {
        row.row.adding(newItem)
    } else {
        val old = row.row[updateIndex]
        @OptIn(ExperimentalUuidApi::class)
        row.row.replacingAt(updateIndex, newItem.copy(randomUuid = old.randomUuid))
    }

    val newRow = row.copy(row = items)
    return this.replacingAt(index, newRow)
}

fun PersistentList<DownloadRow>.updateRow(
    index: Int,
    constructor: (PersistentList<ImmutableSearchResponse>) -> PersistentList<ImmutableSearchResponse>
): PersistentList<DownloadRow> {
    val row = this.getOrNull(index) ?: return this
    val newRow = row.copy(row = constructor(row.row))
    return this.replacingAt(index, newRow)
}

fun PersistentList<ImmutableSearchResponse>.updateList(
    id: Int,
    constructor: (ImmutableSearchResponse) -> ImmutableSearchResponse
): PersistentList<ImmutableSearchResponse> {
    val index = this.indexOfFirst { item -> item.id == id }
    if (index == -1) return this
    val item = constructor(this[index])
    return this.replacingAt(index, item)
}

fun PersistentList<DownloadRow>.removeFromRow(
    index: Int,
    id: Int
): PersistentList<DownloadRow> {
    return updateRow(index) { row ->
        val index = row.indexOfFirst { item -> item.id == id }
        if (index != -1) {
            row.removingAt(index)
        } else {
            row
        }
    }
}*/