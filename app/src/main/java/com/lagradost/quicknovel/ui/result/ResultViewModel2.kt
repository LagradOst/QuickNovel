package com.lagradost.quicknovel.ui.result

import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.DownloadFileWorkManager
import com.lagradost.quicknovel.BookDownloader2.openQuickStream
import com.lagradost.quicknovel.BookDownloader2Helper.createQuickStream
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_CHAPTER
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_SCROLL_CHAR
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.QuickStreamData
import com.lagradost.quicknovel.QuickStreamMetaData
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.ActionHandler
import com.lagradost.quicknovel.compose.DefaultEffectContainer
import com.lagradost.quicknovel.compose.DefaultStateContainer
import com.lagradost.quicknovel.compose.EffectContainer
import com.lagradost.quicknovel.compose.StateContainer
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.common.ImmutableChapterData
import com.lagradost.quicknovel.ui.common.ImmutableDownloadState
import com.lagradost.quicknovel.ui.common.ImmutableReview
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseOperation
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.ui.result.ResultDialog.*
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
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
    val bookmark: ReadType = ReadType.NONE,
    val dialog: ResultDialog? = null
)

@Immutable
sealed class ResultDialog {
    data class Bookmark(val selected : ReadType) : ResultDialog()
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
}

@Immutable
sealed class ResultPageAction {
    data class SetBookmark(val type: ReadType) : ResultPageAction()
    object OpenBookmark : ResultPageAction()
    object DismissDialog : ResultPageAction()
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
    val api: APIRepository,
    val url: String,
) : ViewModel(), ActionHandler<ResultPageAction>,
    StateContainer<ResultState> by DefaultStateContainer(ResultState()),
    EffectContainer<ResultPageEffect> by DefaultEffectContainer() {
    companion object {
        fun provideFactory(bundle: Bundle) = viewModelFactory {
            initializer {
                val url = bundle.getString("url")!!
                val apiName = bundle.getString("apiName")!!
                ResultViewModel2(api = getApiFromName(apiName), url = url)
            }
        }

        fun addToHistory(response: ImmutableSearchResponse) {
            val id = response.id ?: return
            // we won't add it to history from cache

            setKey(
                HISTORY_FOLDER, id.toString(), ResultCached(
                    source = response.url,
                    name = response.name,
                    apiName = response.apiName,
                    id = id,
                    author = response.author,
                    poster = response.posterUrl,
                    tags = response.tags,
                    rating = response.rating,
                    totalChapters = response.loadData?.chapters?.size ?: 1,
                    cachedTime = System.currentTimeMillis(),
                    synopsis = response.synopsis,
                    posterHeaders = response.posterHeaders
                )
            )
        }
    }

    init {
        viewModelScope.launch {
            api.loadResult(url).onFailure { error ->
                updateState { copy(responseError = error, loadingResponse = false) }
            }.onSuccess { value ->
                updateState {
                    copy(
                        response = value,
                        loadingResponse = false,
                        responseError = null
                    )
                }
            }
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

    }

    fun onDownloadAdded(item: Pair<Int, DownloadFragment.DownloadData>) = viewModelScope.launch {
        val (id, page) = item
    }

    private fun onBookmarkChanged(id: Int) = viewModelScope.launch {

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
                updateState {
                    copy(bookmark = action.type)
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
            api.loadReviewsResult(
                page = nextPage,
                url = response.url,
                data = response.reviewData
            ).onFailure { error ->
                if (error is CancellationException) {
                    return@withContext
                }
                updateState {
                    copy(reviews = reviews.copy(loading = false, error = error))
                }
                postEffect {
                    ResultPageEffect.Error(error)
                }
            }.onSuccess { response ->
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