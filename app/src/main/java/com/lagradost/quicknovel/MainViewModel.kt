package com.lagradost.quicknovel

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.getFilenameIMG
import com.lagradost.quicknovel.BookDownloader2Helper.sanitizeFilename
import com.lagradost.quicknovel.compose.*
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.NovelPreviewData
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch

@Immutable
data class MainState(
    val isPreviewOpen: Boolean = false,
    val isBookmarkSelectionOpen: Boolean = false,
    val isDeleteConfirmationOpen: Boolean = false,
    val previewData: NovelPreviewData? = null,
    val isLoadingPreview: Boolean = false,
    val previewError: Throwable? = null,
    val currentLibraryId: Int = 0,
    val libraries: PersistentList<DefaultBookmark> = emptyList<DefaultBookmark>().toPersistentList(),
)

@Immutable
sealed class MainAction {
    data class LoadPreview(val response: SearchResponse) : MainAction()
    data class LoadPreviewResponse(val response: ImmutableSearchResponse) : MainAction()
    data class LoadPreviewDownload(val card: DownloadFragment.DownloadDataLoaded) : MainAction()
    data class LoadPreviewCached(val cached: ResultCached) : MainAction()
    data class ShowBookmarkDialog(val context: Context) : MainAction()
    object ShowDeleteConfirmation : MainAction()
    data class UpdateBookmark(val libraryId: Int) : MainAction()
    object DismissDialog : MainAction()


    // Bookmark CRUD
    data class AddBookmark(val title: String) : MainAction()
    data class RenameBookmark(val library: DefaultBookmark, val newTitle: String) : MainAction()
    data class DeleteBookmark(val libraryId: Int) : MainAction()
    data class MergeBookmarks(val sourceId: Int, val targetId: Int) : MainAction()
    data class ReorderBookmarks(val newList: List<DefaultBookmark>) : MainAction()

    object DeleteNovel : MainAction()
    object ReadNovel : MainAction()
}

class MainViewModel : ViewModel(),
    StateContainer<MainState> by DefaultStateContainer(MainState()),
    ActionHandler<MainAction> {

    //avoid opening multiple previews at the same time
    private var loadJob: kotlinx.coroutines.Job? = null

    override fun onAction(action: MainAction) {
        when (action) {
            is MainAction.LoadPreview -> {
                loadJob?.cancel()
                val context = BaseApplication.context ?: return
                loadJob = loadPreview(
                    url = action.response.url,
                    apiName = action.response.apiName,
                    name = action.response.name,
                    posterUrl = action.response.posterUrl,
                    posterHeaders = action.response.posterHeaders,
                    rating = action.response.rating
                )
            }

            is MainAction.LoadPreviewResponse -> {
                loadJob?.cancel()
                val context = BaseApplication.context ?: return
                val chapterCount = action.response.chapters
                val chapterText = if (chapterCount != null) {
                    "$chapterCount ${context.getString(if (chapterCount == 1L) R.string.chapter else R.string.chapters)}"
                } else null

                val statusText = action.response.statusRes?.let { context.getString(it) }
                    ?: action.response.loadData?.status?.resource?.let { context.getString(it) }

                loadJob = loadPreview(
                    url = action.response.url,
                    apiName = action.response.apiName,
                    name = action.response.name,
                    author = action.response.author,
                    status = statusText,
                    posterUrl = action.response.posterUrl,
                    posterHeaders = action.response.posterHeaders,
                    rating = action.response.rating,
                    synopsis = action.response.synopsis,
                    chapters = chapterText,
                    id = action.response.id
                )
            }

            is MainAction.LoadPreviewDownload -> {
                val context = BaseApplication.context ?: return
                loadJob?.cancel()
                val chapterCount = action.card.downloadedTotal
                val chapterText =
                    "$chapterCount ${context.getString(if (chapterCount == 1L) R.string.chapter else R.string.chapters)}"

                loadJob = loadPreview(
                    url = action.card.source,
                    apiName = action.card.apiName,
                    name = action.card.name,
                    author = action.card.author,
                    posterUrl = action.card.posterUrl,
                    status = action.card.status?.let { context.getString(it) },
                    rating = action.card.rating,
                    synopsis = action.card.synopsis,
                    chapters = chapterText,
                    id = action.card.id
                )
            }

            is MainAction.LoadPreviewCached -> {
                loadJob?.cancel()
                val context = BaseApplication.context ?: return
                val chapterCount = action.cached.totalChapters.toLong()
                val chapterText =
                    "$chapterCount ${context.getString(if (chapterCount == 1L) R.string.chapter else R.string.chapters)}"

                loadJob = loadPreview(
                    url = action.cached.source,
                    apiName = action.cached.apiName,
                    name = action.cached.name,
                    author = action.cached.author,
                    status = action.cached.status?.let { context.getString(it) },
                    posterUrl = action.cached.poster,
                    posterHeaders = action.cached.posterHeaders,
                    rating = action.cached.rating,
                    synopsis = action.cached.synopsis,
                    chapters = chapterText,
                    id = action.cached.id
                )
            }

            MainAction.DismissDialog -> dismissDialog()
            is MainAction.ShowBookmarkDialog -> showBookmarkDialog(action.context)
            is MainAction.UpdateBookmark -> updateBookmark(action.libraryId)

            is MainAction.AddBookmark -> addLibrary(action.title)
            is MainAction.RenameBookmark -> renameLibrary(action.library, action.newTitle)
            is MainAction.DeleteBookmark -> deleteLibrary(action.libraryId)
            is MainAction.MergeBookmarks -> mergeLibrary(action.sourceId, action.targetId)
            is MainAction.ReorderBookmarks -> reorderLibraries(action.newList)

            MainAction.DeleteNovel -> deleteNovel()
            MainAction.ShowDeleteConfirmation -> updateState { copy(isDeleteConfirmationOpen = true) }
            MainAction.ReadNovel -> readNovel()
        }
    }

    private fun dismissDialog() {
        updateState {
            if (isDeleteConfirmationOpen) {
                copy(isDeleteConfirmationOpen = false)
            } else if (isBookmarkSelectionOpen) {
                copy(isBookmarkSelectionOpen = false)
            } else {
                loadJob?.cancel()
                copy(isPreviewOpen = false)
            }
        }
    }

    private fun showBookmarkDialog(context: Context?) {
        if (context == null) return
        updateState {
            copy(
                isBookmarkSelectionOpen = true,
                libraries = context.getBookmarks()
            )
        }
    }

    private fun getBookmarkText(
        bookmarkId: Int,
        libraries: List<DefaultBookmark>,
        context: Context
    ): String? {
        return when {
            bookmarkId > 0 -> libraries.find { it.id == bookmarkId }?.title
            bookmarkId != 0 -> context.getString(R.string.download)
            else -> null
        }
    }

    private fun isOnlyInDownload(bookmarkId: Int, libraries: List<DefaultBookmark>): Boolean {
        return bookmarkId != 0 && libraries.none { it.id == bookmarkId }
    }

    private fun loadPreview(
        url: String,
        apiName: String,
        name: String? = null,
        author: String? = null,
        posterUrl: String? = null,
        posterHeaders: Map<String, String>? = null,
        rating: Int? = null,
        status: String? = null,
        chapters: String? = null,
        synopsis: String? = null,
        id: Int? = null,
    ) = viewModelScope.launch {
        val isImported = (apiName == IMPORT_SOURCE || apiName == IMPORT_SOURCE_PDF)
        val context = BaseApplication.context ?: return@launch
        val bookId = id ?: BookDownloader2Helper.generateId(apiName, author, name ?: "")

        // Recover from cache if data is missing
        var finalSynopsis = synopsis
        var finalStatus = status
        var finalRating = rating
        var finalChapters = chapters
        var finalAuthor = author

        if (!isImported && (finalSynopsis == null || finalStatus == null)) {
            val cached = with(DataStore) { context.getKey<ResultCached>(RESULT_BOOKMARK, bookId.toString()) }
                ?: with(DataStore) { context.getKey<ResultCached>(HISTORY_FOLDER, bookId.toString()) }

            if (cached != null) {
                finalSynopsis = finalSynopsis ?: cached.synopsis
                finalStatus = finalStatus ?: cached.status?.let { context.getString(it) }
                finalRating = finalRating ?: cached.rating
                val chaptersCount = cached.totalChapters
                finalChapters = finalChapters ?: "$chaptersCount ${context.getString(if (chaptersCount == 1) R.string.chapter else R.string.chapters)}"
                finalAuthor = finalAuthor ?: cached.author
            }
        }

        val bookmarkId =
            with(DataStore) { context.getKey<Int>(RESULT_BOOKMARK_STATE, bookId.toString()) } ?: 0
        val bookmarks = context.getBookmarks()

        val bookmarkText = getBookmarkText(bookmarkId, bookmarks, context)
        val isInDownloadCategory = isOnlyInDownload(bookmarkId, bookmarks)
        val showBookmark = isImported || !isInDownloadCategory
        val showMoreInfo = !isImported

        val resolvedPoster: Any? = if (isImported) {
            (context.filesDir.toString() + getFilenameIMG(
                sanitizeFilename(apiName),
                sanitizeFilename(author ?: ""),
                sanitizeFilename(name ?: "")
            )).toUri()
        } else {
            posterUrl
        }

        // Start with loading if we don't have Synopsis AND Status
        val hasRequiredData = !finalSynopsis.isNullOrBlank() && !finalStatus.isNullOrBlank()
        val shouldShowInitialData = name != null && (hasRequiredData || isImported)

        if (shouldShowInitialData) {
            updateState {
                copy(
                    isPreviewOpen = true,
                    isBookmarkSelectionOpen = false,
                    isLoadingPreview = !isImported, // Still loading if not imported to refresh data
                    previewData = NovelPreviewData(
                        title = name,
                        id = bookId,
                        author = finalAuthor,
                        poster = resolvedPoster,
                        posterHeaders = posterHeaders,
                        rating = finalRating,
                        status = finalStatus,
                        chapters = finalChapters,
                        description = finalSynopsis,
                        url = url,
                        apiName = apiName,
                        isBookmarked = bookmarkId != 0,
                        bookmarkText = bookmarkText,
                        showBookmark = showBookmark,
                        showMoreInfo = showMoreInfo
                    ),
                    previewError = null,
                    currentLibraryId = bookmarkId,
                    libraries = bookmarks.toPersistentList()
                )
            }
        } else {
            updateState {
                copy(
                    isPreviewOpen = true,
                    isBookmarkSelectionOpen = false,
                    isLoadingPreview = true,
                    previewData = null,
                    previewError = null
                )
            }
        }

        if (isImported) return@launch

        val apiRepo = Apis.getApiFromNameOrNull(apiName)

        //if provider doesn't exist anymore
        if (apiRepo == null) {
            // If provider is missing, show what we have (even if incomplete) instead of eternal loading
            if (name != null) {
                updateState {
                    copy(
                        isLoadingPreview = false,
                        previewData = NovelPreviewData(
                            title = name,
                            id = bookId,
                            author = finalAuthor,
                            poster = resolvedPoster,
                            posterHeaders = posterHeaders,
                            rating = finalRating,
                            status = finalStatus,
                            chapters = finalChapters,
                            description = finalSynopsis,
                            url = url,
                            apiName = apiName,
                            isBookmarked = bookmarkId != 0,
                            bookmarkText = bookmarkText,
                            showBookmark = showBookmark,
                            showMoreInfo = showMoreInfo
                        )
                    )
                }
            } else {
                updateState { copy(isLoadingPreview = false, previewError = Exception("API not found")) }
            }
            return@launch
        }

        apiRepo.loadResult(url).onSuccess { fullResponse ->
            val freshShowMoreInfo = true
            val freshBookmarkId =
                with(DataStore) { context.getKey<Int>(RESULT_BOOKMARK_STATE, bookId.toString()) } ?: 0

            val freshBookmarkText = getBookmarkText(freshBookmarkId, bookmarks, context)
            val freshIsInDownloadCategory = isOnlyInDownload(freshBookmarkId, bookmarks)
            val freshShowBookmark = !freshIsInDownloadCategory

            val freshChapterCount = fullResponse.loadData?.chapters?.size?.toLong()
            val freshChapterText = if (freshChapterCount != null) {
                "$freshChapterCount ${context.getString(if (freshChapterCount == 1L) R.string.chapter else R.string.chapters)}"
            } else null

            updateState {
                copy(
                    isLoadingPreview = false,
                    currentLibraryId = freshBookmarkId,
                    previewData = NovelPreviewData(
                        title = fullResponse.name,
                        id = fullResponse.id,
                        author = fullResponse.author,
                        poster = fullResponse.posterUrl,
                        posterHeaders = fullResponse.posterHeaders,
                        rating = fullResponse.rating,
                        status = fullResponse.loadData?.status?.resource?.let { context.getString(it) },
                        chapters = freshChapterText,
                        description = fullResponse.synopsis,
                        url = fullResponse.url,
                        apiName = fullResponse.apiName,
                        isBookmarked = freshBookmarkId != 0,
                        bookmarkText = freshBookmarkText,
                        showBookmark = freshShowBookmark,
                        showMoreInfo = freshShowMoreInfo
                    )
                )
            }
        }.onFailure { error ->
            if(name == null)
                updateState { copy(isLoadingPreview = false, previewError = error) }
            else
                updateState {
                    copy(
                        isPreviewOpen = true,
                        isBookmarkSelectionOpen = false,
                        isLoadingPreview = false,
                        previewData = NovelPreviewData(
                            title = name,
                            id = bookId,
                            author = finalAuthor,
                            poster = resolvedPoster,
                            posterHeaders = posterHeaders,
                            rating = finalRating,
                            status = finalStatus,
                            chapters = finalChapters,
                            description = finalSynopsis,
                            url = url,
                            apiName = apiName,
                            isBookmarked = bookmarkId != 0,
                            bookmarkText = bookmarkText,
                            showBookmark = showBookmark,
                            showMoreInfo = showMoreInfo
                        ),
                        previewError = null,
                        currentLibraryId = bookmarkId,
                        libraries = bookmarks.toPersistentList()
                    )
                }
        }
    }

    private fun updateBookmark(bookmarkId: Int) {
        val data = state.value.previewData ?: return
        val context = BaseApplication.context ?: return
        val id = data.id ?: BookDownloader2Helper.generateId(data.apiName, data.author, data.title)

        viewModelScope.launch {
            with(DataStore) {
                if (bookmarkId == 0) {
                    context.removeKey(RESULT_BOOKMARK_STATE, id.toString())
                } else {
                    context.setKey(RESULT_BOOKMARK_STATE, id.toString(), bookmarkId)

                    // Save full metadata to cache so Library tabs have the status
                    val statusResId = ReleaseStatus.entries.find { context.getString(it.resource) == data.status }?.resource

                    context.setKey(
                        RESULT_BOOKMARK, id.toString(), ResultCached(
                            source = data.url,
                            name = data.title,
                            apiName = data.apiName,
                            id = id,
                            author = data.author,
                            poster = data.poster as? String, // Only save if it's a URL
                            tags = null, // We don't have tags in previewData object but we could add them if needed
                            rating = data.rating,
                            totalChapters = data.chapters?.split(" ")?.firstOrNull()?.toIntOrNull() ?: 0,
                            cachedTime = System.currentTimeMillis(),
                            synopsis = data.description,
                            posterHeaders = data.posterHeaders,
                            status = statusResId
                        )
                    )
                }
            }
            val libraries = context.getBookmarks()
            val bookmarkText = getBookmarkText(bookmarkId, libraries, context)

            updateState {
                copy(
                    currentLibraryId = bookmarkId,
                    previewData = previewData?.copy(
                        isBookmarked = bookmarkId != 0,
                        bookmarkText = bookmarkText,
                        // Update bookmark visibility if it was moved out of "Downloads"
                        showBookmark = true
                    )
                )
            }
            BookDownloader2.bookmarkChanged(id)
        }
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
            copy(libraries = context.getBookmarks())
        }
        BookDownloader2.updatePagesDetails.invoke(true)
    }

    private fun deleteNovel() = viewModelScope.launch {
        val data = state.value.previewData ?: return@launch
        val id = data.id ?: BookDownloader2Helper.generateId(data.apiName, data.author, data.title)

        val context = BaseApplication.context ?: return@launch
        with(DataStore) {
            context.removeKey(RESULT_BOOKMARK_STATE, id.toString())
            context.removeKey(RESULT_BOOKMARK, id.toString())
        }

        BookDownloader2.deleteNovel(data.author, data.title, data.apiName)
        
        // Notify UI to remove items from lists
        BookDownloader2.bookmarkChanged(id)
        updateState { copy(isPreviewOpen = false) }
    }

    private fun readNovel() = viewModelScope.launch {
        val data = state.value.previewData ?: return@launch
        val id = data.id ?: BookDownloader2Helper.generateId(data.apiName, data.author, data.title)
        val epubSize = BaseApplication.context?.let { ctx ->
            with(DataStore) { ctx.getKey<Int>(DOWNLOAD_EPUB_SIZE, id.toString()) }
        } ?: 0

        BookDownloader2.readEpub(
            id,
            epubSize,
            data.author,
            data.title,
            data.apiName,
            data.description
        )
        updateState { copy(isPreviewOpen = false) }
    }
}
