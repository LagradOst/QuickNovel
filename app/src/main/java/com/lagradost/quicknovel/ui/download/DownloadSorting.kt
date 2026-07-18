package com.lagradost.quicknovel.ui.download

import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.ImmutableDownloadState
import com.lagradost.quicknovel.ImmutableSearchResponse
import com.lagradost.quicknovel.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi

object DownloadSorting {
    val sortingMethods = arrayOf(
        SortingMethod(R.string.default_sort, DEFAULT_SORT),
        SortingMethod(R.string.recently_sort, LAST_ACCES_SORT, REVERSE_LAST_ACCES_SORT),
        SortingMethod(
            R.string.recently_updated_sort,
            LAST_UPDATED_SORT,
            REVERSE_LAST_UPDATED_SORT
        ),
        SortingMethod(R.string.alpha_sort, ALPHA_SORT, REVERSE_ALPHA_SORT),
        SortingMethod(R.string.download_sort, DOWNLOADSIZE_SORT, REVERSE_DOWNLOADSIZE_SORT),
        SortingMethod(
            R.string.download_perc, DOWNLOADPRECENTAGE_SORT,
            REVERSE_DOWNLOADPRECENTAGE_SORT
        ),
    )

    val normalSortingMethods = arrayOf(
        SortingMethod(R.string.default_sort, DEFAULT_SORT),
        SortingMethod(R.string.recently_sort, LAST_ACCES_SORT, REVERSE_LAST_ACCES_SORT),
        SortingMethod(R.string.alpha_sort, ALPHA_SORT, REVERSE_ALPHA_SORT),
    )

    @OptIn(ExperimentalUuidApi::class)
    fun updateDirty(
        pages: PersistentList<DownloadRow>,
        dirtyIds: ConcurrentHashMap<Int, ImmutableDownloadState>,
    ): PersistentList<DownloadRow> {
        val row = pages.getOrNull(0) ?: return pages
        // Also update the download state when we filter
        val items = if (dirtyIds.isEmpty()) {
            row.row
        } else {
            row.row.map { item ->
                item.copy(
                    downloadState = dirtyIds.remove(item.id) ?: item.downloadState
                )
            }.toPersistentList()
        }
        return pages.replacingAt(0, row.copy(row = items))
    }

    fun filterRows(
        rows: ImmutableList<DownloadRow>,
        query: String,
        downloadSortingMethod: Int,
        regularSortingMethod: Int
    ): PersistentList<DownloadRow> {
        return rows.mapIndexed { index, row ->
            if (index != 0) {
                return@mapIndexed row.copy(
                    row = filterItems(
                        row.row,
                        regularSortingMethod,
                        query
                    )
                )
            }

            row.copy(
                row = filterItems(
                    row.row,
                    downloadSortingMethod,
                    query
                )
            )
        }.toPersistentList()
    }

    fun filterItems(
        items: List<ImmutableSearchResponse>,
        sort: Int,
        query: String,
    ): PersistentList<ImmutableSearchResponse> {
        val currentArray = if (query.isBlank() || query.length < 2) {
            items
        } else {
            items.filter { item ->
                item.filter(query)
            }
        }

        return when (sort) {
            ALPHA_SORT -> {
                currentArray.sortedBy { t -> t.name }
            }

            REVERSE_ALPHA_SORT -> {
                currentArray.sortedByDescending { t -> t.name }
            }

            DOWNLOADSIZE_SORT -> {
                currentArray.sortedByDescending { t -> t.downloadState?.downloaded }
            }

            REVERSE_DOWNLOADSIZE_SORT -> {
                currentArray.sortedBy { t -> t.downloadState?.downloaded }
            }

            DOWNLOADPRECENTAGE_SORT -> {
                currentArray.sortedByDescending { t ->
                    t.downloadState?.downloadPercentage ?: 0.0f
                }
            }

            REVERSE_DOWNLOADPRECENTAGE_SORT -> {
                currentArray.sortedBy { t -> t.downloadState?.downloadPercentage ?: 0.0f }
            }

            REVERSE_LAST_ACCES_SORT -> {
                currentArray.sortedBy { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
            }

            LAST_UPDATED_SORT -> {
                if (currentArray.any { it.downloadTime == null }) {
                    currentArray.sortedByDescending { t ->
                        (getKey<Long>(
                            DOWNLOAD_EPUB_LAST_ACCESS,
                            t.id.toString(),
                            0
                        )!!)
                    }
                } else {
                    currentArray
                }.sortedByDescending { it.downloadTime ?: 0L }
            }

            REVERSE_LAST_UPDATED_SORT -> {
                if (currentArray.any { it.downloadTime == null }) {
                    currentArray.sortedByDescending { t ->
                        (getKey<Long>(
                            DOWNLOAD_EPUB_LAST_ACCESS,
                            t.id.toString(),
                            0
                        )!!)
                    }
                } else {
                    currentArray
                }.sortedBy { it.downloadTime ?: 0L }
            }
            //DEFAULT_SORT, LAST_ACCES_SORT
            else -> {
                currentArray.sortedByDescending { t ->
                    (getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
            }
        }.toPersistentList()
    }

}


fun PersistentList<DownloadRow>.updateSearchItem(
    index: Int,
    newItem : ImmutableSearchResponse,
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
}