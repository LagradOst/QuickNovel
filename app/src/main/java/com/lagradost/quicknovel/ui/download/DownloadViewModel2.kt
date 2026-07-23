package com.lagradost.quicknovel.ui.download

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2.currentDownloads
import com.lagradost.quicknovel.BookDownloader2.currentDownloadsMutex
import com.lagradost.quicknovel.BookDownloader2.downloadInfoMutex
import com.lagradost.quicknovel.BookDownloader2.downloadProgress
import com.lagradost.quicknovel.BookDownloader2.downloadProgressChanged
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.CURRENT_TAB
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE
import com.lagradost.quicknovel.DOWNLOAD_NORMAL_SORTING_METHOD
import com.lagradost.quicknovel.DOWNLOAD_SETTINGS
import com.lagradost.quicknovel.DOWNLOAD_SORTING_METHOD
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadFileWorkManager
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.compose.ActionHandler
import com.lagradost.quicknovel.compose.DebounceQuery
import com.lagradost.quicknovel.compose.DefaultStateContainer
import com.lagradost.quicknovel.compose.StateContainer
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.common.ImmutableDownloadState
import com.lagradost.quicknovel.ui.common.ImmutableSearchList
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseOperation
import com.lagradost.quicknovel.ui.common.SortingMethodType
import com.lagradost.quicknovel.ui.common.updateRow
import com.lagradost.quicknovel.ui.common.updateRows
import com.lagradost.quicknovel.ui.download.DownloadDialog.*
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.cmap
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

@Immutable
data class DownloadPageState(
    val query: String = "",
    val pages: PersistentList<ImmutableSearchList> = persistentListOf(),
    val downloadSortingMethod: SortingMethodType = SortingMethodType.Default,
    val regularSortingMethod: SortingMethodType = SortingMethodType.Default,
    val activePage: Int = 0,
    /** null -> not shown */
    val dialog: DownloadDialog? = null,
)

@Immutable
sealed class DownloadDialog {
    object SortDownloads : DownloadDialog()
    object SortBookmarks : DownloadDialog()
    data class DeleteItem(val item: ImmutableSearchResponse) : DownloadDialog()
    data class DeleteBookmark(val item: ImmutableSearchResponse) : DownloadDialog()
}

@Immutable
sealed class DownloadPageAction {
    object Refresh : DownloadPageAction()
    data class Search(val query: String) : DownloadPageAction()
    data class ResultAction(val action: SearchResponseAction) : DownloadPageAction()
    object ShowSorting : DownloadPageAction()
    object DismissDialog : DownloadPageAction()
    data class SelectSortingMethod(
        val downloadSortingMethod: SortingMethodType? = null,
        val regularSortingMethod: SortingMethodType? = null
    ) : DownloadPageAction()

    data class SelectPage(val page: Int) : DownloadPageAction()
}

class DownloadViewModel2 : ViewModel(), ActionHandler<DownloadPageAction>,
    StateContainer<DownloadPageState> by DefaultStateContainer(DownloadPageState()) {
    companion object {
        val readList = persistentListOf(
            ReadType.READING,
            ReadType.ON_HOLD,
            ReadType.PLAN_TO_READ,
            ReadType.COMPLETED,
            ReadType.DROPPED,
        )
    }

    private val searchPipe = DebounceQuery()
    override fun onAction(action: DownloadPageAction) {
        when (action) {
            is DownloadPageAction.Refresh -> {
                viewModelScope.launch {
                    val page = state.value.activePage
                    if (page == 0) {
                        refreshDownloads()
                    } else {
                        refreshPage(page)
                    }
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

            DownloadPageAction.ShowSorting -> {
                updateState {
                    copy(
                        dialog = if (activePage == 0) {
                            DownloadDialog.SortDownloads
                        } else {
                            DownloadDialog.SortBookmarks
                        }
                    )
                }
            }

            is DownloadPageAction.SelectPage -> {
                setKey(DOWNLOAD_SETTINGS, CURRENT_TAB, action.page)
                updateState {
                    val activeQuery = query
                    copy(
                        activePage = action.page,
                        pages = pages.updateRows { index ->
                            search(
                                query = activeQuery,
                                sortingMethod = if (index == 0) downloadSortingMethod else sortingMethod
                            )
                        })
                }
            }

            DownloadPageAction.DismissDialog -> {
                updateState {
                    copy(dialog = null)
                }
            }

            is DownloadPageAction.SelectSortingMethod -> {
                updateState {
                    val newRegularSortingMethod =
                        action.regularSortingMethod ?: regularSortingMethod
                    val newDownloadSortingMethod =
                        action.downloadSortingMethod ?: downloadSortingMethod

                    setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, newDownloadSortingMethod.id)
                    setKey(
                        DOWNLOAD_SETTINGS,
                        DOWNLOAD_NORMAL_SORTING_METHOD,
                        newRegularSortingMethod.id
                    )

                    val activeQuery = query
                    copy(
                        downloadSortingMethod = newDownloadSortingMethod,
                        regularSortingMethod = newRegularSortingMethod,
                        pages = pages.updateRows { index ->
                            search(
                                query = activeQuery,
                                sortingMethod = if (index == 0) newDownloadSortingMethod else newRegularSortingMethod
                            )
                        }
                    )
                }
            }
        }
    }

    private fun onRefreshingChanged(item: BookDownloader2.RefreshQuery) = viewModelScope.launch {
        // This is a hijack of the "generating" system, however we assume that it is fine
        updateState {
            copy(
                pages = pages.updateRow(item.page) {
                    update(item.id) {
                        @OptIn(ExperimentalUuidApi::class)
                        copy(generating = item.refreshing)
                    }
                },
            )
        }
    }

    private fun onBookmarkChanged(id: Int) = viewModelScope.launch {
        val result = getKey<ResultCached>(RESULT_BOOKMARK, id.toString())
        val state = getKey<Int>(RESULT_BOOKMARK_STATE, id.toString())

        // Always remove it, and then readd it
        updateState {
            copy(
                pages = pages.updateRows { index ->
                    if (index != 0) {
                        delete(id)
                    } else {
                        this
                    }
                },
            )
        }
        if (result == null || state == null) {
            return@launch
        }

        val newIndex = readList.indexOf(ReadType.fromSpinner(state))
        if (newIndex == -1) {
            return@launch
        }
        val response = ImmutableSearchResponse.from(result)
        updateState {
            copy(
                pages = pages.updateRow(newIndex + 1) {
                    insert(id, response)
                },
            )
        }
    }

    private fun readEpub(response: ImmutableSearchResponse) = viewModelScope.launch {
        withContext(Dispatchers.Default) {
            val id = response.id!!
            val downloadState = response.downloadState!!
            try {
                updateState {
                    copy(pages = pages.updateRow(0) {
                        update(id) {
                            @OptIn(ExperimentalUuidApi::class)
                            copy(generating = true)
                        }
                    })
                }

                if (response.isImported && downloadState.progress < downloadState.total) {
                    BookDownloader2.preloadPartialImportedPdf(response)
                }

                BookDownloader2.readEpub(
                    id,
                    downloadState.progress.toInt(),
                    response.author,
                    response.name,
                    response.apiName,
                    response.synopsis
                )
            } finally {
                val opened = System.currentTimeMillis()
                setKey(DOWNLOAD_EPUB_LAST_ACCESS, id.toString(), opened)
                val newEpubSize = getKey<Int>(DOWNLOAD_EPUB_SIZE, id.toString())

                updateState {
                    copy(pages = pages.updateRow(0) {
                        update(id) {
                            @OptIn(ExperimentalUuidApi::class)
                            copy(
                                generating = false,
                                timeOfPageOpened = opened,
                                epubSize = newEpubSize ?: this.epubSize
                            )
                        }
                    })
                }
            }
        }
    }

    private fun resultAction(action: SearchResponseAction) {
        when (action.operation) {
            SearchResponseOperation.Open -> {
                if (action.response.downloadState != null) {
                    readEpub(action.response)
                } else {
                    action.doAction()
                }
            }

            SearchResponseOperation.Stream -> {
                viewModelScope.launch {
                    val id = action.response.id!!
                    updateState {
                        copy(pages = pages.updateRows {
                            update(id) {
                                @OptIn(ExperimentalUuidApi::class)
                                copy(generating = true)
                            }
                        })
                    }
                    BookDownloader2.stream(action.response)
                    updateState {
                        copy(pages = pages.updateRows {
                            update(id) {
                                @OptIn(ExperimentalUuidApi::class)
                                copy(generating = false)
                            }
                        })
                    }
                }

            }

            SearchResponseOperation.AskDelete -> {
                if (action.response.downloadState != null) {
                    updateState { copy(dialog = DeleteItem(action.response)) }
                } else {
                    updateState { copy(dialog = DeleteBookmark(action.response)) }
                }
            }

            SearchResponseOperation.Delete -> {
                val id = action.response.id!!
                if (action.response.downloadState != null) {
                    BookDownloader2.deleteNovel(
                        action.response.author,
                        action.response.name,
                        action.response.apiName
                    )
                } else {
                    removeKey(RESULT_BOOKMARK, id.toString())
                    removeKey(RESULT_BOOKMARK_STATE, id.toString())

                    updateState {
                        copy(
                            pages = pages.updateRows { index ->
                                if (index != 0) {
                                    delete(id)
                                } else {
                                    this
                                }
                            },
                        )
                    }
                }
            }

            SearchResponseOperation.Metadata -> {
                action.doAction()
            }

            SearchResponseOperation.Download -> {
                viewModelScope.launch {
                    BookDownloader2.downloadWorkThread(action.response)
                }
            }

            SearchResponseOperation.Pause -> {
                val id = action.response.id!!
                BookDownloader2.addPendingAction(
                    id,
                    DownloadActionType.Pause
                )
            }

            SearchResponseOperation.Resume -> {
                val id = action.response.id!!
                BookDownloader2.addPendingAction(
                    id,
                    DownloadActionType.Resume
                )
            }
        }
    }


    fun refreshPage(page: Int) {
        DownloadFileWorkManager.refreshAllReadingProgress(
            BaseApplication.context ?: return,
            page
        )
    }

    suspend fun refreshDownloads() {
        val progressState = state.value
        val downloadPage = progressState.pages.getOrNull(0) ?: return

        val values =
            currentDownloadsMutex.withLock {
                downloadPage.data.filter { (id, card) ->
                    val notImported = !card.isImported && card.apiName != IMPORT_SOURCE_PDF
                    val downloadState = card.downloadState ?: return@filter false

                    val canDownload =
                        downloadState.total > 0 && downloadState.progressPercentage > 0.9f

                    val notDownloading = !currentDownloads.contains(
                        id
                    )
                    notImported && canDownload && notDownloading
                }
            }

        downloadInfoMutex.withLock {
            for ((id, _) in values) {
                downloadProgress[id]?.apply {
                    state = DownloadState.IsPending
                    lastUpdatedMs = System.currentTimeMillis()
                    downloadProgressChanged.invoke(id to this@apply)
                }
            }
        }

        withContext(Dispatchers.IO) {
            values.values.cmap { card ->
                BookDownloader2.downloadWorkThread(card)
            }
        }
    }

    init {
        viewModelScope.launch {
            searchPipe.launch { newQuery ->
                updateState {
                    copy(
                        query = newQuery,
                        pages = pages.updateRows {
                            search(newQuery)
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            loadAll()
        }
        BookDownloader2.downloadProgressChanged += this::onDownloadStateChange
        BookDownloader2.downloadRemoved += this::onDownloadRemoved
        BookDownloader2.downloadDataChanged += this::onDownloadAdded
        BookDownloader2.bookmarkChanged += this::onBookmarkChanged
        BookDownloader2.refreshingChanged += this::onRefreshingChanged
        BookDownloader2.chapterReadChanged += this::onChapterChanged
    }

    override fun onCleared() {
        BookDownloader2.downloadProgressChanged -= this::onDownloadStateChange
        BookDownloader2.downloadRemoved -= this::onDownloadRemoved
        BookDownloader2.downloadDataChanged -= this::onDownloadAdded
        BookDownloader2.bookmarkChanged -= this::onBookmarkChanged
        BookDownloader2.refreshingChanged -= this::onRefreshingChanged
        BookDownloader2.chapterReadChanged -= this::onChapterChanged
    }

    fun onChapterChanged(name: String) {
        updateState {
            copy(pages = pages.updateRows {
                val ids = data.filter { it.value.name == name }
                if(ids.isEmpty()) return@updateRows this

                val new = data.builder()
                for ((key, value) in ids) {
                    @OptIn(ExperimentalUuidApi::class)
                    val newResponse = value.copy(chaptersRead = ImmutableSearchResponse.chaptersRead(name))
                    new[key] = newResponse
                }
                return@updateRows copy(data = new.build())
            })
        }
    }

    fun onDownloadAdded(item: Pair<Int, DownloadFragment.DownloadData>) = viewModelScope.launch {
        val (id, page) = item

        val searchResponse = ImmutableSearchResponse.from(
            id,
            page,
            ImmutableDownloadState.from(
                downloadInfoMutex.withLock { downloadProgress[id] } ?: return@launch
            )
        )

        updateState {
            copy(
                pages = pages.updateRow(0) {
                    insert(id, searchResponse)
                },
            )
        }
    }

    fun onDownloadRemoved(id: Int) = viewModelScope.launch {
        updateState {
            copy(
                pages = pages.updateRow(0) {
                    delete(id)
                },
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun onDownloadStateChange(data: Pair<Int, DownloadProgressState>) = viewModelScope.launch {
        val (id, downloadState) = data
        val newDownloadState = ImmutableDownloadState.from(downloadState)

        updateState {
            copy(
                pages = pages.updateRow(0) {
                    update(id) {
                        copy(downloadState = newDownloadState)
                    }
                }
            )
        }
    }

    suspend fun loadAll() = withContext(Dispatchers.Default) {
        run {
            val downloadSortingMethod =
                getKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD) ?: DEFAULT_SORT
            val regularSortingMethod =
                getKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD) ?: DEFAULT_SORT
            val query = ""
            val page = getKey<Int>(DOWNLOAD_SETTINGS, CURRENT_TAB) ?: 0
            updateState {
                copy(
                    query = query,
                    regularSortingMethod = SortingMethodType.from(regularSortingMethod),
                    downloadSortingMethod = SortingMethodType.from(downloadSortingMethod),
                    activePage = page
                )
            }
        }

        val mapping: HashMap<Int, ArrayList<Pair<Int, ImmutableSearchResponse>>> = hashMapOf(
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
                cached.id to ImmutableSearchResponse.from(cached)
            )
        }

        val map = downloadInfoMutex.withLock {
            BookDownloader2.downloadData.mapNotNull { entry ->
                entry.key to ImmutableSearchResponse.from(
                    entry.key,
                    entry.value,
                    ImmutableDownloadState.from(
                        downloadProgress[entry.key] ?: return@mapNotNull null
                    )
                )
            }
        }

        val stateValue = state.value

        val concat: List<ImmutableSearchList> = buildList {
            add(
                ImmutableSearchList.new(
                    map.toMap().toPersistentHashMap(),
                    stateValue.query,
                    stateValue.downloadSortingMethod
                )
            )
            for (x in readList) {
                add(
                    ImmutableSearchList.new(
                        mapping[x.prefValue]!!.toMap().toPersistentHashMap(),
                        stateValue.query,
                        stateValue.downloadSortingMethod
                    )
                )
            }
        }

        val pages = concat.toPersistentList()

        updateState {
            copy(pages = pages)
        }
    }
}