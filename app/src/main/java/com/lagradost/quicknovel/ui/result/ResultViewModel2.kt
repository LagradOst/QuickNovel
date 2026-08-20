package com.lagradost.quicknovel.ui.result

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.DownloadFileWorkManager
import com.lagradost.quicknovel.BookDownloader2.openQuickStream
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.createQuickStream
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DataStore
import com.lagradost.quicknovel.DefaultBookmark
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_CHAPTER
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_SCROLL_CHAR
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.QuickStreamData
import com.lagradost.quicknovel.QuickStreamMetaData
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.addBookmark
import com.lagradost.quicknovel.compose.ActionHandler
import com.lagradost.quicknovel.compose.DefaultEffectContainer
import com.lagradost.quicknovel.compose.DefaultStateContainer
import com.lagradost.quicknovel.compose.EffectContainer
import com.lagradost.quicknovel.compose.StateContainer
import com.lagradost.quicknovel.deleteBookmark
import com.lagradost.quicknovel.getBookmarks
import com.lagradost.quicknovel.mergeBookmarks
import com.lagradost.quicknovel.ui.common.ImmutableChapterData
import com.lagradost.quicknovel.ui.common.ImmutableDownloadState
import com.lagradost.quicknovel.ui.common.ImmutableReview
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseOperation
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.updateBookmark
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

@Immutable
data class ResultState(
    val response: ImmutableSearchResponse? = null,
    val responseError: Throwable? = null,
    val loadingResponse: Boolean = true,
    val reviews: ResultReviewState = ResultReviewState(),
    val currentBookmark: Int = 0,
    val bookmarks: PersistentList<DefaultBookmark> = emptyList<DefaultBookmark>().toPersistentList(),
    val dialogState: ResultDialogState? = null,
    val deleteTarget: ImmutableSearchResponse? = null,
    val showMoreInfo: Boolean = true
)

@Immutable
data class ResultDialogState (
    val isPreviewOpen: Boolean = false,
    val isBookmarkSelectionOpen: Boolean = false,
    val isDeleteConfirmationOpen: Boolean = false
)


@Immutable
data class ResultReviewState(
    val loading: Boolean = false,
    val page: Int = 0,
    val error: Throwable? = null,
    val items: PersistentList<ImmutableReview> = persistentListOf(),
)

enum class ChapterOperation {
    Stream,
}
sealed class BookmarkAction{
    data class AddBookmark(val title: String) : BookmarkAction()
    data class RenameBookmark(val library: DefaultBookmark, val newTitle: String) : BookmarkAction()
    data class DeleteBookmark(val libraryId: Int) : BookmarkAction()
    data class MergeBookmarks(val sourceId: Int, val targetId: Int) : BookmarkAction()
    data class ReorderBookmarks(val newList: List<DefaultBookmark>) : BookmarkAction()
}

@Immutable
sealed class ResultPageAction {
    data class SetBookmark(val libraryId: Int) : ResultPageAction()

    data class LoadResult(
        val response: ImmutableSearchResponse? = null,
        val isPreview: Boolean = true,
        val id: Int? = null,
        val url: String? = null,
        val apiName: String? = null,
    ) : ResultPageAction()

    data class ShowBookmarkDialog(val context: Context) : ResultPageAction()
    object ShowDeleteConfirmation : ResultPageAction()
    data class AskDeleteNovel(val response: ImmutableSearchResponse) : ResultPageAction()
    object DismissDeleteConfirmation : ResultPageAction()
    object DismissDialog : ResultPageAction()


    data class ModifyBookmark(val action: BookmarkAction): ResultPageAction()
    data class DeleteNovel(val id: Int) : ResultPageAction()


    object ExpandReviews : ResultPageAction()
    data class ResultAction(val action: SearchResponseAction) : ResultPageAction()
    data class ChapterAction(
        val response: ImmutableSearchResponse,
        val chapter: ImmutableChapterData,
        val operation: ChapterOperation
    ) : ResultPageAction()
}

@Immutable
sealed class ResultPageEffect {
    data class Toast(val text: Int) : ResultPageEffect()
    data class Error(val t: Throwable) : ResultPageEffect()
}

class ResultViewModel2(
    var api: APIRepository? = null,
    var url: String? = null,
    var id: Int? = null
) : ViewModel(), ActionHandler<ResultPageAction>,
    StateContainer<ResultState> by DefaultStateContainer(ResultState()),
    EffectContainer<ResultPageEffect> by DefaultEffectContainer() {

    //avoid opening multiple previews at the same time
    private var loadJob: Job? = null


    companion object {
        fun toResultCached(response: ImmutableSearchResponse): ResultCached {
            return ResultCached(
                source = response.url,
                name = response.name,
                apiName = response.apiName,
                id = response.id ?: 0,
                author = response.author,
                poster = response.posterUrl,
                tags = response.tags,
                rating = response.rating,
                totalChapters = response.chapters?.toInt() ?: 1,
                cachedTime = System.currentTimeMillis(),
                synopsis = response.synopsis,
                posterHeaders = response.posterHeaders,
                status = response.statusRes
            )
        }

        fun updateCache(response: ImmutableSearchResponse, bookmarkId: Int? = null) {
            val id = response.id ?: return
            val context = BaseApplication.context ?: return

            with(DataStore) {
                val finalBookmarkId =
                    bookmarkId ?: context.getKey<Int>(RESULT_BOOKMARK_STATE, id.toString()) ?: 0

                val cached = toResultCached(response)
                // Only save to cache if it has a bookmark
                if (finalBookmarkId > 0) {
                    context.setKey(RESULT_BOOKMARK, id.toString(), cached)
                }
            }
        }

        fun addToHistory(response: ImmutableSearchResponse) {
            val id = response.id ?: return
            setKey(HISTORY_FOLDER, id.toString(), toResultCached(response))
        }
    }

    init {
        BookDownloader2.downloadProgressChanged += this::onDownloadStateChange
        BookDownloader2.downloadRemoved += this::onDownloadRemoved
        BookDownloader2.downloadDataChanged += this::onDownloadAdded
        BookDownloader2.refreshingChanged += this::onRefreshingChanged
        BookDownloader2.chapterReadChanged += this::onChapterChanged
    }

    override fun onCleared() {
        BookDownloader2.downloadProgressChanged -= this::onDownloadStateChange
        BookDownloader2.downloadRemoved -= this::onDownloadRemoved
        BookDownloader2.downloadDataChanged -= this::onDownloadAdded
        BookDownloader2.refreshingChanged -= this::onRefreshingChanged
        BookDownloader2.chapterReadChanged -= this::onChapterChanged
    }

    override fun onAction(action: ResultPageAction) {
        when (action) {
            is ResultPageAction.ResultAction -> {
                dismissDialog()
                resultAction(action.action)
            }

            is ResultPageAction.ChapterAction -> {
                chapterAction(action)
            }

            ResultPageAction.ExpandReviews -> {
                if (state.value.reviews.loading) return
                viewModelScope.launch {
                    expandReviews()
                }
            }

            ResultPageAction.DismissDialog -> {
                dismissDialog()
            }

            is ResultPageAction.SetBookmark -> {
                updateBookmark(action.libraryId)
            }

            is ResultPageAction.DeleteNovel -> deleteNovel()

            is ResultPageAction.LoadResult -> {
                loadJob?.cancel()
                val apiName = action.apiName ?: action.response?.apiName
                api = if (!apiName.isNullOrBlank()) Apis.getApiFromNameOrNull(apiName) else null
                id = action.id
                url = action.url

                updateState {
                    copy(
                        response = action.response,
                        loadingResponse = true,
                        reviews = ResultReviewState(),
                        responseError = null
                    )
                }

                loadJob = loadResult(action.response, action.isPreview)
            }
            ResultPageAction.ShowDeleteConfirmation -> updateState {
                copy(dialogState = dialogState?.copy(isDeleteConfirmationOpen = true) ?: ResultDialogState(isDeleteConfirmationOpen = true))
            }

            is ResultPageAction.AskDeleteNovel -> updateState {
                copy(
                    deleteTarget = action.response,
                    dialogState = dialogState?.copy(isDeleteConfirmationOpen = true) ?: ResultDialogState(isDeleteConfirmationOpen = true)
                )
            }

            ResultPageAction.DismissDeleteConfirmation -> updateState {
                copy(
                    dialogState = dialogState?.copy(isDeleteConfirmationOpen = false),
                    deleteTarget = null
                )
            }

            is ResultPageAction.ModifyBookmark -> {
                when(val bookmarkAction = action.action){
                    is BookmarkAction.AddBookmark -> addLibrary(bookmarkAction.title)
                    is BookmarkAction.RenameBookmark -> renameLibrary(bookmarkAction.library, bookmarkAction.newTitle)
                    is BookmarkAction.DeleteBookmark ->  deleteLibrary(bookmarkAction.libraryId)
                    is BookmarkAction.MergeBookmarks -> mergeLibrary(bookmarkAction.sourceId, bookmarkAction.targetId)
                    is BookmarkAction.ReorderBookmarks -> reorderLibraries(bookmarkAction.newList)
                }

            }
            is ResultPageAction.ShowBookmarkDialog -> showBookmarkDialog(action.context)
        }
    }

    private fun loadResult(
        result: ImmutableSearchResponse?,
        isPreview: Boolean
    ) = viewModelScope.launch(Dispatchers.IO) {
        val context = BaseApplication.context ?: return@launch
        val finalApiName = api?.name ?: result?.apiName ?: return@launch
        val finalUrl = url ?: result?.url ?: return@launch
        val isImported = (finalApiName == IMPORT_SOURCE || finalApiName == IMPORT_SOURCE_PDF)
        val bookId = id ?: result?.id ?: BookDownloader2Helper.generateId(
            finalApiName,
            result?.author,
            result?.name ?: ""
        )

        // Handle dialog state
        if (!isPreview) {
            ImmutableSearchResponse.setTimeOfPageOpened(bookId, System.currentTimeMillis())
            BookDownloader2.openChanged(bookId)
            updateState { copy(dialogState = null) }
        }
        val bookmarks = context.getBookmarks()
        val bookmarkId = with(DataStore) {
            context.getKey<Int>(RESULT_BOOKMARK_STATE, bookId.toString())
        } ?: 0
        
        updateState {
            copy(
                dialogState = if (isPreview) (dialogState?.copy(isPreviewOpen = true)
                    ?: ResultDialogState(isPreviewOpen = true)) else dialogState,
                loadingResponse = !isImported,
                currentBookmark = bookmarkId,
                bookmarks = bookmarks,
                showMoreInfo = !isImported
            )
        }

        if (isImported) return@launch

        val apiRepo = api ?: Apis.getApiFromNameOrNull(finalApiName)

        //if provider doesn't exist anymore
        if (apiRepo == null) {
            updateState { copy(loadingResponse = false) }
            return@launch
        }

        apiRepo.loadResult(finalUrl).onSuccess { fullResponse ->
            val freshBookmarkId = with(DataStore) {
                context.getKey<Int>(
                    RESULT_BOOKMARK_STATE,
                    bookId.toString()
                )
            } ?: 0

            val mergedResponse = if (fullResponse.posterUrl.isNullOrBlank() && result?.posterUrl?.isBlank() == true) {
                fullResponse.copy(
                    posterUrl = result.posterUrl,
                    posterHeaders = result.posterHeaders
                )
            } else {
                fullResponse
            }

            updateState {
                copy(
                    response = mergedResponse,
                    loadingResponse = false,
                    currentBookmark = freshBookmarkId,
                    showMoreInfo = true,
                    responseError = null
                )
            }
            updateCache(mergedResponse, freshBookmarkId)
            addToHistory(mergedResponse)
        }.onFailure { error ->
            updateState {
                copy(
                    loadingResponse = false,
                    responseError = if (response == null) error else null
                )
            }
        }
    }

    private fun updateBookmark(bookmarkId: Int) {
        val context = BaseApplication.context ?: return
        val data = state.value.response ?: return
        val id = data.id ?: BookDownloader2Helper.generateId(data.apiName, data.author, data.name)

        viewModelScope.launch(Dispatchers.IO) {
            with(DataStore) {
                if (bookmarkId == 0) {
                    context.removeKey(RESULT_BOOKMARK_STATE, id.toString())
                    context.removeKey(RESULT_BOOKMARK, id.toString())
                } else {
                    context.setKey(RESULT_BOOKMARK_STATE, id.toString(), bookmarkId)
                }
                updateState {
                    copy(
                        currentBookmark = bookmarkId,
                        bookmarks = context.getBookmarks()
                    )
                }
            }
            BookDownloader2.bookmarkChanged(id)
        }
    }

    private fun deleteNovel() = viewModelScope.launch {
        val currentState = state.value
        val target = currentState.deleteTarget ?: currentState.response ?: return@launch
        val id = target.id ?: BookDownloader2Helper.generateId(target.apiName, target.author, target.name)

        val context = BaseApplication.context ?: return@launch
        with(DataStore) {
            context.removeKey(RESULT_BOOKMARK_STATE, id.toString())
            context.removeKey(RESULT_BOOKMARK, id.toString())
        }

        BookDownloader2.deleteNovel(target.author, target.name, target.apiName)

        BookDownloader2.bookmarkChanged(id)
        updateState { copy(dialogState = null, deleteTarget = null) }
    }

    private fun addLibrary(title: String) {
        val context = BaseApplication.context ?: return
        try {
            context.addBookmark(title)
            refreshLibraries()
        } catch (e: Exception) {
            CommonActivity.showToast(e.message)
        }
    }

    private fun renameLibrary(library: DefaultBookmark, newTitle: String) {
        val context = BaseApplication.context ?: return
        try {
            context.updateBookmark(library.copy(title = newTitle))
            refreshLibraries()
        } catch (e: Exception) {
            CommonActivity.showToast(e.message)
        }
    }

    private fun deleteLibrary(libraryId: Int) {
        val context = BaseApplication.context ?: return
        try {
            context.deleteBookmark(libraryId)
            refreshLibraries()
        } catch (e: Exception) {
            CommonActivity.showToast(e.message)
        }
    }

    private fun mergeLibrary(sourceId: Int, targetId: Int) {
        val context = BaseApplication.context ?: return
        try {
            context.mergeBookmarks(sourceId, targetId)
            refreshLibraries()
        } catch (e: Exception) {
            CommonActivity.showToast(e.message)
        }
    }

    private fun reorderLibraries(newList: List<DefaultBookmark>) {
        val context = BaseApplication.context ?: return
        try {
            newList.forEachIndexed { index, lib ->
                context.updateBookmark(lib.copy(position = index + 1))
            }
            refreshLibraries()
        } catch (e: Exception) {
            CommonActivity.showToast(e.message)
        }
    }

    private fun refreshLibraries() {
        val context = BaseApplication.context ?: return
        updateState {
            copy(bookmarks = context.getBookmarks())
        }
        BookDownloader2.updatePagesDetails.invoke(true)
    }

    fun onDownloadStateChange(data: Pair<Int, DownloadProgressState>) = viewModelScope.launch {
        val (id, newState) = data
        updateState {
            if (id != response?.id) {
                this
            } else {
                copy(response = response.copy(downloadState = ImmutableDownloadState.from(newState)))
            }
        }
    }

    fun onDownloadRemoved(id: Int) = viewModelScope.launch {
        updateState {
            if (id != response?.id) {
                this
            } else {
                copy(response = response.copy(downloadState = null))
            }
        }
    }

    fun onDownloadAdded(item: Pair<Int, DownloadFragment.DownloadData>) = viewModelScope.launch {
        val (id, _) = item
        updateState {
            if (id != response?.id) {
                this
            } else {
                copy(response = response.copy(downloadState = ImmutableDownloadState.from(
                    BookDownloader2.downloadProgress[id] ?: DownloadProgressState(
                        state = com.lagradost.quicknovel.DownloadState.Nothing,
                        progress = 0,
                        total = response.loadData?.chapters?.size?.toLong() ?: 1,
                        downloaded = 0,
                        lastUpdatedMs = System.currentTimeMillis(),
                        etaMs = null
                    )
                )))
            }
        }
    }

    private fun onRefreshingChanged(item: BookDownloader2.RefreshQuery) = viewModelScope.launch {
        updateState {
            if (item.id != response?.id) {
                this
            } else {
                copy(response = response.copy(generating = item.refreshing))
            }
        }
    }

    fun onChapterChanged(name: String) {
        updateState {
            if (name != response?.name) {
                this
            } else {
                copy(
                    response = response.copy(
                        chaptersRead = ImmutableSearchResponse.chaptersRead(name),
                        timeOfPageOpened = System.currentTimeMillis(),
                        epubSize = response.id?.let { ImmutableSearchResponse.epubSize(it) }
                            ?: response.epubSize
                    ))
            }
        }
    }

    private fun dismissDialog() {
        updateState {
            if (dialogState?.isDeleteConfirmationOpen == true) {
                copy(dialogState = dialogState.copy(isDeleteConfirmationOpen = false), deleteTarget = null)
            } else if (dialogState?.isBookmarkSelectionOpen == true) {
                copy(dialogState = dialogState.copy(isBookmarkSelectionOpen = false))
            } else {
                loadJob?.cancel()
                copy(dialogState = null)
            }
        }
    }

    private fun showBookmarkDialog(context: Context?) {
        if (context == null) return
        updateState {
            copy(
                dialogState = dialogState?.copy(isBookmarkSelectionOpen = true) ?: ResultDialogState(isBookmarkSelectionOpen = true),
                bookmarks = context.getBookmarks()
            )
        }
    }

    private val expandMutex = Mutex()

    suspend fun expandReviews() = withContext(Dispatchers.IO) {
        val query = state.value.reviews

        try {
            expandMutex.lock()
            if (query != state.value.reviews) return@withContext
            val response = state.value.response ?: return@withContext

            updateState {
                copy(reviews = reviews.copy(loading = true))
            }

            val nextPage = query.page + 1
            api?.loadReviewsResult(
                page = nextPage,
                url = response.url,
                data = response.reviewData
            )?.onFailure { error ->
                if (error is CancellationException) {
                    return@withContext
                }
                updateState {
                    copy(reviews = reviews.copy(loading = false, error = error))
                }
                postEffect {
                    ResultPageEffect.Error(error)
                }
            }?.onSuccess { response ->
                val newList = query.items.addingAll(response)
                updateState {
                    // Outdated query, drop it
                    if (query.page != state.value.reviews.page) {
                        return@updateState copy(reviews = reviews.copy(loading = false))
                    }
                    copy(
                        reviews = reviews.copy(
                            loading = false,
                            error = null,
                            items = newList,
                            page = nextPage
                        )
                    )
                }
            }
        } finally {
            updateState {
                copy(reviews = reviews.copy(loading = false))
            }
            expandMutex.unlock()
        }
    }

    private fun chapterAction(action: ResultPageAction.ChapterAction) {
        when (action.operation) {
            ChapterOperation.Stream -> {
                addToHistory(response = action.response)
                val chapters = action.response.loadData?.chapters ?: persistentListOf()
                if (chapters.isEmpty()) {
                    viewModelScope.launch {
                        postEffect {
                            ResultPageEffect.Toast(R.string.no_data)
                        }
                    }
                    return
                }

                try {
                    val uri =
                        activity?.createQuickStream(
                            QuickStreamData(
                                QuickStreamMetaData(
                                    action.response.author,
                                    action.response.name,
                                    action.response.apiName,
                                ),
                                action.response.posterUrl,
                                chapters.map { chapter ->
                                    ChapterData(
                                        name = chapter.name,
                                        url = chapter.url,
                                        dateOfRelease = chapter.dateOfRelease,
                                        views = chapter.views
                                    )
                                }.toMutableList()
                            )
                        )

                    setKey(EPUB_CURRENT_POSITION, action.response.name, action.chapter.index)
                    setKey(
                        EPUB_CURRENT_POSITION_CHAPTER,
                        action.response.name,
                        action.chapter.name
                    )
                    setKey(
                        EPUB_CURRENT_POSITION_SCROLL_CHAR, action.response.name, 0,
                    )

                    openQuickStream(uri)
                } catch (t: Throwable) {
                    viewModelScope.launch {
                        postEffect {
                            ResultPageEffect.Error(t)
                        }
                    }
                }
            }
        }
    }

    private fun resultAction(action: SearchResponseAction) {
        when (action.operation) {
            SearchResponseOperation.Open, SearchResponseOperation.Metadata, SearchResponseOperation.NoOp -> {
                action.doAction()
            }

            SearchResponseOperation.Stream -> {
                viewModelScope.launch {
                    BookDownloader2.stream(action.response)
                }
            }

            SearchResponseOperation.Read -> {
                addToHistory(action.response)
                readEpub(action.response)
            }

            SearchResponseOperation.AskDelete -> {

            }

            SearchResponseOperation.Delete -> {
                if (action.response.downloadState != null) {
                    BookDownloader2.deleteNovel(
                        action.response.author,
                        action.response.name,
                        action.response.apiName
                    )
                }
            }

            SearchResponseOperation.Download -> {
                DownloadFileWorkManager.download(
                    action.response,
                    BaseApplication.context ?: return
                )
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

            SearchResponseOperation.Stop -> {
                val id = action.response.id!!
                BookDownloader2.addPendingAction(
                    id,
                    DownloadActionType.Stop
                )
            }
        }
    }

    private fun readEpub(response: ImmutableSearchResponse) = viewModelScope.launch {
        withContext(Dispatchers.Default) {

            val id = response.id!!
            val downloadState = response.downloadState!!
            try {
                if (response.isImported && downloadState.progress < downloadState.total) {
                    updateState {
                        copy(response = this@updateState.response?.copy(generating = true))
                    }
                    BookDownloader2.preloadPartialImportedPdf(response)
                }
                BookDownloader2.readEpub(
                    id,
                    downloadState.progress.toInt(),
                    response.author,
                    response.name,
                    response.apiName,
                    response.synopsis
                ) {
                    updateState {
                        copy(response = this@updateState.response?.copy(generating = true))
                    }
                }
            } finally {
                val newTimeOfPageOpened = System.currentTimeMillis()
                ImmutableSearchResponse.setTimeOfPageOpened(id, newTimeOfPageOpened)
                BookDownloader2.chapterReadChanged(response.name)

                updateState {
                    copy(response = this@updateState.response?.copy(generating = false))
                }
            }
        }
    }
}