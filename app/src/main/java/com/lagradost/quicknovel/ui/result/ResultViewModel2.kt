package com.lagradost.quicknovel.ui.result

import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lagradost.cloudstream4.state.ActionHandler
import com.lagradost.cloudstream4.state.DefaultEffectContainer
import com.lagradost.cloudstream4.state.DefaultStateContainer
import com.lagradost.cloudstream4.state.EffectContainer
import com.lagradost.cloudstream4.state.StateContainer
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_CHAPTER
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_SCROLL_CHAR
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.RESULT_SORTING_METHOD
import com.lagradost.quicknovel.downloader.BookAction
import com.lagradost.quicknovel.downloader.BookDownloadPackage
import com.lagradost.quicknovel.downloader.BookDownloader
import com.lagradost.quicknovel.downloader.BookNotifications
import com.lagradost.quicknovel.downloader.BookReader
import com.lagradost.quicknovel.downloader.BookReader.streamReadChapters
import com.lagradost.quicknovel.downloader.BookState
import com.lagradost.quicknovel.downloader.BookStateChanged
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.common.ChapterSortingMethodType
import com.lagradost.quicknovel.ui.common.ImmutableChapterData
import com.lagradost.quicknovel.ui.common.ImmutableChapterList
import com.lagradost.quicknovel.ui.common.ImmutableReview
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseOperation
import com.lagradost.quicknovel.ui.download.DEFAULT_SORT
import com.lagradost.quicknovel.ui.result.ResultDialog.Bookmark
import com.lagradost.quicknovel.ui.result.ResultPageEffect.*
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.BitSet
import java.util.concurrent.CancellationException

@Immutable
data class ResultState(
    val loadUrl: String = "",
    val response: ImmutableSearchResponse? = null,
    val chapters: ImmutableChapterList? = null,
    val responseError: Throwable? = null,
    val loadingResponse: Boolean = true,
    val reviews: ResultReviewState = ResultReviewState(),
    val bookmark: ReadType = ReadType.NONE,
    val dialog: ResultDialog? = null,
    val api: APIRepository?,
)

@Immutable
sealed class ResultDialog {
    data class Bookmark(val selected: ReadType) : ResultDialog()
    data class DeleteItem(val item: ImmutableSearchResponse) : ResultDialog()
}


@Immutable
data class ResultReviewState(
    val loading: Boolean = false,
    val page: Int = 0,
    val error: Throwable? = null,
    val items: PersistentList<ImmutableReview> = persistentListOf(),
)

enum class ChapterOperation {
    Stream,
    Download,
}

@Immutable
sealed class ResultPageAction {
    data class SetBookmark(val type: ReadType) : ResultPageAction()
    object OpenBookmark : ResultPageAction()
    object DismissDialog : ResultPageAction()
    object ExpandReviews : ResultPageAction()
    object Reload : ResultPageAction()
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
    val api: APIRepository?,
    val url: String,
    initializer: ImmutableSearchResponse?,
    /** Should we load from the api? */
    loadData: Boolean = true,
    /** Is this loading from the start? */
    loadingResponse: Boolean = true,
) : ViewModel(), ActionHandler<ResultPageAction>,
    StateContainer<ResultState> by DefaultStateContainer(
        initialState = ResultState(
            loadingResponse = loadingResponse,
            response = initializer?.let { response ->
                if (response.id != null && response.downloadState == null) {
                    response.copy(
                        downloadState = ImmutableSearchResponse.downloadState(
                            response.id,
                            totalChapters = response.chapters ?: 1
                        )
                    )
                } else {
                    response
                }
            },
            bookmark = initializer?.id?.let { getBookmarkState(it) } ?: ReadType.NONE,
            chapters = newChaptersList(initializer),
            api = api,
            loadUrl = url
        )
    ),
    EffectContainer<ResultPageEffect> by DefaultEffectContainer() {
    companion object {
        fun newChaptersList(initializer: ImmutableSearchResponse?): ImmutableChapterList? {
            val chapters = initializer?.loadData?.chapters ?: return null
            val id = initializer.id ?: return null

            val method = ChapterSortingMethodType.from(
                getKey<Int>(RESULT_SORTING_METHOD, id.toString()) ?: DEFAULT_SORT
            )

            return ImmutableChapterList.new(chapters, query = "", sortingMethod = method)
        }

        fun provideFactory(bundle: Bundle) = viewModelFactory {
            initializer {
                val url = bundle.getString("url")!!
                val apiName = bundle.getString("apiName")!!
                ResultViewModel2(api = getApiFromName(apiName), url = url, initializer = null)
            }
        }

        fun getBookmarkState(id: Int): ReadType = ReadType.fromSpinner(
            getKey<Int>(
                RESULT_BOOKMARK_STATE, id.toString()
            )
        )

        fun setBookmark(id: Int, response: ImmutableSearchResponse, type: ReadType) {
            if (ReadType.NONE == type) {
                removeKey(RESULT_BOOKMARK_STATE, id.toString())
                removeKey(RESULT_BOOKMARK, id.toString())
            } else {
                setKey(
                    RESULT_BOOKMARK_STATE, id.toString(), type.prefValue
                )
                setKey(
                    RESULT_BOOKMARK, id.toString(), response.toResultCached(id)
                )
            }
            BookDownloader2.bookmarkChanged(id)
        }
    }

    init {
        if (loadData) {
            viewModelScope.launch {
                initLoad()
            }
        }

        BookState.onRemoved += this::onDownloadRemoved
        BookState.onStatusChanged += this::onDownloadChanged

        BookDownloader2.bookmarkChanged += this::onBookmarkChanged
        BookDownloader2.refreshingChanged += this::onRefreshingChanged
        BookDownloader2.chapterReadChanged += this::onChapterChanged
    }

    suspend fun initLoad() = withContext(Dispatchers.IO) {
        api?.loadResult(url)?.onFailure { error ->
            updateState { copy(responseError = error, loadingResponse = false) }
        }?.onSuccess { value ->
            val id =
                value.id ?: throw IllegalStateException("loadResult must give an id")
            val bookmarkState = getBookmarkState(id)
            updateState {
                copy(
                    response = value,
                    loadingResponse = false,
                    responseError = null,
                    bookmark = bookmarkState,
                    chapters = newChaptersList(value)
                )
            }

            // Mark as opened
            ImmutableSearchResponse.setTimeOfPageOpened(id, System.currentTimeMillis())
            BookDownloader2.openChanged(id)

            // When we open this we can update the cached data in downloads
            setBookmark(id, response = value, type = bookmarkState)
        }
    }

    suspend fun requireLoadData() {
        if (state.value.response?.loadData != null) return
        api?.loadResult(url)?.onFailure { error ->
            logError(error)
        }?.onSuccess { value ->
            val id =
                value.id ?: throw IllegalStateException("loadResult must give an id")
            val bookmarkState = getBookmarkState(id)
            updateState {
                copy(
                    response = value,
                    loadingResponse = false,
                    responseError = null,
                    bookmark = bookmarkState,
                    chapters = newChaptersList(value)
                )
            }
            setBookmark(id = id, response = value, type = bookmarkState)
        }
    }

    override fun onCleared() {
        BookState.onRemoved -= this::onDownloadRemoved
        BookState.onStatusChanged -= this::onDownloadChanged

        BookDownloader2.bookmarkChanged -= this::onBookmarkChanged
        BookDownloader2.refreshingChanged -= this::onRefreshingChanged
        BookDownloader2.chapterReadChanged -= this::onChapterChanged
    }


    fun onDownloadRemoved(id: Int) = viewModelScope.launch {
        if (id != state.value.response?.id) {
            return@launch
        }
        updateState {
            val id = response?.id
            if (id == null) {
                // how?
                this
            } else {
                copy(response = response.copy(downloadState = null))
            }
        }
    }

    fun onDownloadChanged(item: BookStateChanged) = viewModelScope.launch {
        if (item.id != state.value.response?.id) {
            return@launch
        }
        updateState {
            val id = response?.id
            if (id == null) {
                // how?
                this
            } else {
                copy(
                    response = response.copy(
                        downloadState = item.response.downloadState
                    )
                )
            }
        }
    }

    private fun onBookmarkChanged(id: Int) = viewModelScope.launch {
        if (id != state.value.response?.id) {
            return@launch
        }
        updateState {
            val id = response?.id
            if (id == null) {
                // how?
                this
            } else {
                copy(bookmark = getBookmarkState(id))
            }
        }
    }

    private fun onRefreshingChanged(item: BookDownloader2.RefreshQuery) = viewModelScope.launch {

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

    override fun onAction(action: ResultPageAction) {
        when (action) {
            is ResultPageAction.ResultAction -> {
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

            ResultPageAction.OpenBookmark -> {
                updateState {
                    copy(dialog = Bookmark(bookmark))
                }
            }

            ResultPageAction.DismissDialog -> {
                updateState {
                    copy(dialog = null)
                }
            }

            is ResultPageAction.SetBookmark -> {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) {
                        val response = state.value.response
                        val id = response?.id
                        if (id != null) {
                            // Do note that bookmarkChanged invoked the UI change, so this does not have to change state
                            setBookmark(id = id, response = response, type = action.type)
                        } else {
                            api?.loadResult(url)?.onFailure { error ->
                                logError(error)
                            }?.onSuccess { newResponse ->
                                val id =
                                    newResponse.id
                                        ?: throw IllegalStateException("loadResult must give an id")
                                updateState {
                                    copy(
                                        response = newResponse,
                                        loadingResponse = false,
                                        responseError = null,
                                        bookmark = getBookmarkState(id),
                                        chapters = newChaptersList(newResponse)
                                    )
                                }
                                setBookmark(id = id, response = newResponse, type = action.type)
                            }
                        }
                    }
                }
            }

            ResultPageAction.Reload -> {
                viewModelScope.launch {
                    updateState {
                        copy(loadingResponse = true)
                    }
                    initLoad()
                }
            }
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
                val chapters = action.response.loadData?.chapters ?: persistentListOf()
                if (chapters.isEmpty()) {
                    viewModelScope.launch {
                        postEffect {
                            Toast(R.string.no_data)
                        }
                    }
                    return
                }

                try {
                    val activity = activity ?: return
                    setKey(EPUB_CURRENT_POSITION, action.response.name, action.chapter.index)
                    setKey(
                        EPUB_CURRENT_POSITION_CHAPTER,
                        action.response.name,
                        action.chapter.name
                    )
                    setKey(
                        EPUB_CURRENT_POSITION_SCROLL_CHAR, action.response.name, 0,
                    )
                    streamReadChapters(activity = activity, response = action.response)
                } catch (t: Throwable) {
                    viewModelScope.launch {
                        postEffect {
                            Error(t)
                        }
                    }
                }
            }

            ChapterOperation.Download -> {
                BookDownloader.download(
                    response = action.response, bookPackage = BookDownloadPackage.Chapters(
                        BitSet().apply { set(action.chapter.index) })
                )
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
                    updateState { copy(response = response?.copy(generating = true)) }
                    requireLoadData()
                    BookReader.streamRead(action.response)
                    updateState { copy(response = response?.copy(generating = false)) }
                }
            }

            SearchResponseOperation.Read -> {
                readEpub(action.response)
            }

            SearchResponseOperation.AskDelete -> {
                updateState {
                    copy(dialog = response?.let { ResultDialog.DeleteItem(it) })
                }
            }

            SearchResponseOperation.Delete -> {
                action.response.id?.let { id ->
                    viewModelScope.launch {
                        BookDownloader.delete(id)
                    }
                }
            }

            SearchResponseOperation.Download -> {
                viewModelScope.launch {
                    requireLoadData()
                    BookDownloader.download(
                        response = action.response,
                        bookPackage = BookDownloadPackage.AfterOffset(
                            action.response.downloadState?.downloadOffset ?: 0
                        )
                    )
                }
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

    private fun readEpub(response: ImmutableSearchResponse) = viewModelScope.launch {
        withContext(Dispatchers.Default) {
            try {
                BookReader.readEpub(response) {
                    updateState {
                        copy(response = this@updateState.response?.copy(generating = true))
                    }
                }
            } finally {
                updateState {
                    copy(response = this@updateState.response?.copy(generating = false))
                }
            }
        }
    }
}