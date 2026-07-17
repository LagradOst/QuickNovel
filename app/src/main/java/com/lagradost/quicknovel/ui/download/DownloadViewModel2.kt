package com.lagradost.quicknovel.ui.download

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2.currentDownloads
import com.lagradost.quicknovel.BookDownloader2.currentDownloadsMutex
import com.lagradost.quicknovel.BookDownloader2.downloadInfoMutex
import com.lagradost.quicknovel.BookDownloader2.downloadProgress
import com.lagradost.quicknovel.BookDownloader2.downloadProgressChanged
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DOWNLOAD_NORMAL_SORTING_METHOD
import com.lagradost.quicknovel.DOWNLOAD_SETTINGS
import com.lagradost.quicknovel.DOWNLOAD_SORTING_METHOD
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.ImmutableDownloadState
import com.lagradost.quicknovel.ImmutableSearchResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.SearchResponseAction
import com.lagradost.quicknovel.SearchResponseOperation
import com.lagradost.quicknovel.compose.ActionHandler
import com.lagradost.quicknovel.compose.DebounceQuery
import com.lagradost.quicknovel.compose.DefaultStateContainer
import com.lagradost.quicknovel.compose.StateContainer
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.download.ALPHA_SORT
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.cmap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.xdrop.fuzzywuzzy.FuzzySearch

@Immutable
data class DownloadRow(
    val name: Int,
    val row: ImmutableList<ImmutableSearchResponse>,
)


@Immutable
data class DownloadPageState(
    val pages: PersistentList<DownloadRow> = persistentListOf(),
    val query: String = "",
    val filteredPages: ImmutableList<DownloadRow> = persistentListOf(),
    val downloadSortingMethod: Int = DEFAULT_SORT,
    val regularSortingMethod: Int = DEFAULT_SORT,
)

@Immutable
sealed class DownloadPageAction {
    object Refresh : DownloadPageAction()
    data class Search(val query: String) : DownloadPageAction()
    data class ResultAction(val action : SearchResponseAction) : DownloadPageAction()
}

class DownloadViewModel2 : ViewModel(), ActionHandler<DownloadPageAction>,
    StateContainer<DownloadPageState> by DefaultStateContainer(DownloadPageState()) {
    companion object {
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

        fun filterRows(
            rows: ImmutableList<DownloadRow>,
            query: String,
            downloadSortingMethod: Int,
            regularSortingMethod: Int
        ): PersistentList<DownloadRow> {
            return rows.mapIndexed { index, row ->
                row.copy(
                    row = filterItems(
                        row.row,
                        if (index == 0) downloadSortingMethod else regularSortingMethod,
                        query
                    )
                )
            }.toPersistentList()
        }

        fun filterItems(
            items: ImmutableList<ImmutableSearchResponse>,
            sort: Int,
            query: String,
        ): ImmutableList<ImmutableSearchResponse> {
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
            }.toImmutableList()
        }
    }

    private val searchPipe = DebounceQuery()
    override fun onAction(action: DownloadPageAction) {
        when (action) {
            is DownloadPageAction.Refresh -> {
                viewModelScope.launch {
                    refreshDownloads()
                }
            }

            is DownloadPageAction.Search -> {
                viewModelScope.launch {
                    searchPipe.emit(action.query)
                }
            }

            is DownloadPageAction.ResultAction -> {
                resultAction(action.action)
            }
        }
    }
    private fun resultAction(action : SearchResponseAction) {
        action.doAction()
    }

    suspend fun refreshDownloads() {
        val progressState = state.value
        val downloadPage = progressState.pages.getOrNull(0) ?: return

        val values =
            downloadPage.row.filter { card ->
                val notImported = !card.isImported && card.apiName != IMPORT_SOURCE_PDF
                val downloadState = card.downloadState ?: return@filter false

                val canDownload =
                    downloadState.total <= 0 || downloadState.downloadPercentage > 0.9f

                val notDownloading = !currentDownloads.contains(
                    card.id
                )
                notImported && canDownload && notDownloading
            }

        downloadInfoMutex.withLock {
            for (card in values) {
                val id = card.id ?: return@withLock
                downloadProgress[id]?.apply {
                    state = DownloadState.IsPending
                    lastUpdatedMs = System.currentTimeMillis()
                    downloadProgressChanged.invoke(id to this@apply)
                }
            }
        }

        withContext(Dispatchers.IO) {
            values.cmap { card ->
                BookDownloader2.downloadWorkThread(card)
            }
        }
    }

    init {
        viewModelScope.launch {
            loadAll()
            searchPipe.launch { newQuery ->
                updateState {
                    if (query == newQuery) {
                        this
                    } else {
                        copy(
                            query = newQuery,
                            filteredPages = filterRows(
                                pages,
                                query = newQuery,
                                downloadSortingMethod = downloadSortingMethod,
                                regularSortingMethod = regularSortingMethod
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun loadAll() = withContext(Dispatchers.Default) {
        val mapping: HashMap<Int, ArrayList<ImmutableSearchResponse>> = hashMapOf(
            ReadType.PLAN_TO_READ.prefValue to arrayListOf(),
            ReadType.DROPPED.prefValue to arrayListOf(),
            ReadType.COMPLETED.prefValue to arrayListOf(),
            ReadType.ON_HOLD.prefValue to arrayListOf(),
            ReadType.READING.prefValue to arrayListOf(),
        )
        val keys = getKeys(RESULT_BOOKMARK_STATE)
        for (key in keys ?: emptyList()) {
            val type = getKey<Int>(key) ?: continue
            val id = key.replaceFirst(
                RESULT_BOOKMARK_STATE,
                RESULT_BOOKMARK
            )
            val cached = getKey<ResultCached>(id) ?: continue
            mapping[type]?.add(
                ImmutableSearchResponse.from(
                    cached
                )
            )
        }

        val map = downloadInfoMutex.withLock {
            BookDownloader2.downloadData.mapNotNull { entry ->
                ImmutableSearchResponse.from(
                    entry.key,
                    entry.value,
                    ImmutableDownloadState.from(
                        downloadProgress[entry.key] ?: return@mapNotNull null
                    )
                )
            }
        }

        val readList = arrayListOf(
            ReadType.READING,
            ReadType.ON_HOLD,
            ReadType.PLAN_TO_READ,
            ReadType.COMPLETED,
            ReadType.DROPPED,
        )

        val concat: List<DownloadRow> = buildList {
            add(DownloadRow(name = R.string.tab_downloads, row = map.toPersistentList()))
            for (x in readList) {
                add(DownloadRow(x.stringRes, row = mapping[x.prefValue]!!.toPersistentList()))
            }
        }

        val downloadSortingMethod =
            getKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD) ?: DEFAULT_SORT
        val regularSortingMethod =
            getKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD) ?: DEFAULT_SORT
        val query = ""

        val pages = concat.toPersistentList()
        val filteredPages = filterRows(pages, query, downloadSortingMethod, regularSortingMethod)

        updateState {
            copy(
                pages = pages,
                filteredPages = filteredPages
            )
        }
    }
}