package com.lagradost.quicknovel.ui.download

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream4.state.ActionHandler
import com.lagradost.cloudstream4.state.DebounceQuery
import com.lagradost.cloudstream4.state.DefaultStateContainer
import com.lagradost.cloudstream4.state.StateContainer
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.downloader.BookAction
import com.lagradost.quicknovel.downloader.BookDownloadPackage
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.downloader.BookStateChanged
import com.lagradost.quicknovel.CURRENT_TAB
import com.lagradost.quicknovel.DOWNLOAD_NORMAL_SORTING_METHOD
import com.lagradost.quicknovel.DOWNLOAD_SETTINGS
import com.lagradost.quicknovel.DOWNLOAD_SORTING_METHOD
import com.lagradost.quicknovel.DownloadFileWorkManager
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.downloader.BookDownloadStatus
import com.lagradost.quicknovel.downloader.BookDownloader
import com.lagradost.quicknovel.downloader.BookNotifications
import com.lagradost.quicknovel.downloader.BookReader
import com.lagradost.quicknovel.downloader.BookState
import com.lagradost.quicknovel.downloader.BookState.setupMutex
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.common.ImmutableSearchList
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseOperation
import com.lagradost.quicknovel.ui.common.SortingMethodType
import com.lagradost.quicknovel.ui.common.updateRow
import com.lagradost.quicknovel.ui.common.updateRows
import com.lagradost.quicknovel.ui.download.DownloadDialog.DeleteBookmark
import com.lagradost.quicknovel.ui.download.DownloadDialog.DeleteItem
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

const val DEFAULT_SORT = 0
const val ALPHA_SORT = 1
const val REVERSE_ALPHA_SORT = 2
const val DOWNLOADSIZE_SORT = 3
const val REVERSE_DOWNLOADSIZE_SORT = 4
const val DOWNLOADPRECENTAGE_SORT = 5
const val REVERSE_DOWNLOADPRECENTAGE_SORT = 6
const val LAST_ACCES_SORT = 7
const val REVERSE_LAST_ACCES_SORT = 8
const val LAST_UPDATED_SORT = 9
const val REVERSE_LAST_UPDATED_SORT = 10

const val CHAPTER_SORT = 11
const val REVERSE_CHAPTER_SORT = 12

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
                    copy(activePage = action.page)
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
        val id = response.id!!
        try {
            BookReader.readEpub(response, generating = {
                updateState {
                    copy(pages = pages.updateRow(0) {
                        update(id) {
                            copy(generating = true)
                        }
                    })
                }
            })
        } finally {
            updateState {
                copy(pages = pages.updateRow(0) {
                    update(id) {
                        copy(
                            generating = false,
                        )
                    }
                })
            }
        }
    }

    private fun streamEpub(response: ImmutableSearchResponse) = viewModelScope.launch {
        val id = response.id!!
        try {
            BookReader.streamRead(response, generating = {
                updateState {
                    copy(pages = pages.updateRow(0) {
                        update(id) {
                            copy(generating = true)
                        }
                    })
                }
            })
        } finally {
            updateState {
                copy(pages = pages.updateRow(0) {
                    update(id) {
                        copy(
                            generating = false,
                        )
                    }
                })
            }
        }
    }

    private fun resultAction(action: SearchResponseAction) {
        when (action.operation) {
            SearchResponseOperation.Read -> {
                readEpub(action.response)
            }

            SearchResponseOperation.Open, SearchResponseOperation.NoOp -> {
                action.doAction()
            }

            SearchResponseOperation.Stream -> {
                streamEpub(action.response)
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
                    viewModelScope.launch {
                        BookDownloader.delete(id)
                    }
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
                BookDownloader.download(
                    response = action.response,
                    bookPackage = BookDownloadPackage.AfterOffset(
                        offset = action.response.downloadState?.downloadOffset ?: 0
                    )
                )
            }

            SearchResponseOperation.Pause -> {
                val id = action.response.id!!
                BookNotifications.notify(
                    id,
                    BookAction.Pause
                )
            }

            SearchResponseOperation.Resume -> {
                val id = action.response.id!!
                BookNotifications.notify(
                    id,
                    BookAction.Resume
                )
            }

            SearchResponseOperation.Stop -> {
                val id = action.response.id!!
                BookNotifications.notify(
                    id,
                    BookAction.Stop
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

    suspend fun refreshDownloads() = withContext(Dispatchers.Default) {
        val progressState = state.value
        val downloadPage = progressState.pages.getOrNull(0) ?: return@withContext

        val values =
            /** Only refresh the selected ones */
            downloadPage.sorted.mapNotNull { index -> downloadPage.data[index] }.filter { card ->
                val notImported = !card.isImported && card.apiName != IMPORT_SOURCE_PDF
                val downloadState = card.downloadState ?: return@filter false
                val canDownload = (card.downloadPercentage ?: return@filter false) > 0.9f
                val notDownloading =
                    downloadState.downloadStatus != BookDownloadStatus.Downloading
                notImported && canDownload && notDownloading
            }

        /*
        downloadInfoMutex.withLock {
            for ((id, _) in values) {
                downloadProgress[id]?.apply {
                    state = DownloadState.IsPending
                    lastUpdatedMs = System.currentTimeMillis()
                    downloadProgressChanged.invoke(id to this@apply)
                }
            }
        }*/

        for (card in values) {
            BookDownloader.download(
                response = card,
                bookPackage = BookDownloadPackage.AfterOffset(
                    card.downloadState?.downloadOffset ?: 0
                )
            )
            // Stagger them slightly to prevent a lag spike
            delay(10.milliseconds)
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
        BookState.onRemoved += this::onDownloadRemoved
        BookState.onStatusChanged += this::onDownloadAdded

        BookDownloader2.bookmarkChanged += this::onBookmarkChanged
        BookDownloader2.refreshingChanged += this::onRefreshingChanged
        BookDownloader2.chapterReadChanged += this::onChapterChanged
        BookDownloader2.openChanged += this::onOpen
    }

    override fun onCleared() {
        BookState.onRemoved -= this::onDownloadRemoved
        BookState.onStatusChanged -= this::onDownloadAdded

        BookDownloader2.bookmarkChanged -= this::onBookmarkChanged
        BookDownloader2.refreshingChanged -= this::onRefreshingChanged
        BookDownloader2.chapterReadChanged -= this::onChapterChanged
        BookDownloader2.openChanged -= this::onOpen
    }

    fun onOpen(id: Int) {
        updateState {
            copy(pages = pages.updateRows {
                update(id) {
                    copy(
                        lastChaptersRead = ImmutableSearchResponse.lastChaptersRead(name, id),
                        timeOfPageOpened = ImmutableSearchResponse.timeOfPageOpened(id),
                        epubSize = ImmutableSearchResponse.epubSize(id)
                    )
                }
            })
        }
    }

    fun onChapterChanged(name: String) {
        updateState {
            copy(pages = pages.updateRows {
                val ids = data.filter { it.value.name == name }
                if (ids.isEmpty()) return@updateRows this

                var out = this
                for (id in ids.keys) {
                    out = out.update(id) {
                        copy(
                            lastChaptersRead = ImmutableSearchResponse.lastChaptersRead(name, id),
                            chaptersRead = ImmutableSearchResponse.chaptersRead(id),
                            timeOfPageOpened = ImmutableSearchResponse.timeOfPageOpened(id),
                            epubSize = ImmutableSearchResponse.epubSize(id)
                        )
                    }
                }
                return@updateRows out
            })
        }
    }

    fun onDownloadAdded(item: BookStateChanged) = viewModelScope.launch {
        updateState {
            copy(
                pages = pages.updateRow(0) {
                    val newResponse = item.response
                    if (data.containsKey(item.id)) {
                        update(item.id) {
                            /**
                             * We want to keep the uuid stuff here, and avoid setting loadData,
                             * as that causes a cache problem */
                            newResponse.copy(
                                timeOfPageOpened = timeOfPageOpened,
                                randomUuid = randomUuid,
                                loadData = null,
                            )
                        }
                    } else {
                        insert(item.id, newResponse.copy(loadData = null))
                    }
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

    suspend fun loadAll() = withContext(Dispatchers.Default) {
        run {
            val downloadSortingMethod =
                getKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD) ?: SortingMethodType.Default.id
            val regularSortingMethod =
                getKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD)
                    ?: SortingMethodType.Default.id
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

        /** Somewhat racy here, but we assume that setupMutex is taken by the BookDownloader3 first */
        val map = setupMutex.withLock {
            BookState.currentStatusMap.entries.associate { it.key to it.value.response }
                .toPersistentMap()
        }

        val stateValue = state.value

        val concat: List<ImmutableSearchList> = buildList {
            add(
                ImmutableSearchList.new(
                    map,
                    stateValue.query,
                    stateValue.downloadSortingMethod
                )
            )
            for (x in readList) {
                add(
                    ImmutableSearchList.new(
                        mapping[x.prefValue]!!.toMap().toPersistentHashMap(),
                        stateValue.query,
                        stateValue.regularSortingMethod
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