package com.lagradost.quicknovel.ui.result

import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2.downloadProgress
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.BookDownloader2Helper.generateId
import com.lagradost.quicknovel.BookDownloader2Helper.getDirectory
import com.lagradost.quicknovel.BookDownloader2Helper.getFilename
import com.lagradost.quicknovel.BookDownloader2Helper.sanitizeFilename
import java.io.File
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.databinding.ChapterContextMenuBinding
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_CHAPTER
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_READ_AT
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_SCROLL_CHAR
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.launchSafe
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ResultViewModel : ViewModel() {
    fun clear() {
        loadResponse.postValue(null)
    }

    fun hasReadChapter(chapter: ChapterData): Boolean {
        val streamResponse =
            (load as? StreamResponse) ?: return false
        val index = chapterIndex(chapter) ?: return false
        
        // Use cached data if available, otherwise fallback to direct query
        if (statusCacheValid && index < cachedReadStatuses.size) {
            return cachedReadStatuses[index]
        }
        
        return getKey<Long>(
            EPUB_CURRENT_POSITION_READ_AT,
            "${streamResponse.name}/$index"
        ) != null
    }

    fun setReadChapter(chapter: ChapterData, value: Boolean): Boolean {
        val streamResponse =
            (load as? StreamResponse) ?: return false
        val index = chapterIndex(chapter) ?: return false

        if (value) {
            setKey(
                EPUB_CURRENT_POSITION_READ_AT,
                "${streamResponse.name}/$index",
                System.currentTimeMillis()
            )
        } else {
            removeKey(
                EPUB_CURRENT_POSITION_READ_AT,
                "${streamResponse.name}/$index",
            )
        }

        // Invalidate cache for instant filter updates
        invalidateStatusCache()
        return true
    }

    fun markAllPreviousChapters(chapter: ChapterData, value: Boolean): Boolean {
        val streamResponse = (load as? StreamResponse) ?: return false
        val currentIndex = chapterIndex(chapter) ?: return false
        
        // Mark all chapters from index 0 to currentIndex (inclusive) as read/unread
        for (i in 0..currentIndex) {
            if (value) {
                setKey(
                    EPUB_CURRENT_POSITION_READ_AT,
                    "${streamResponse.name}/$i",
                    System.currentTimeMillis()
                )
            } else {
                removeKey(
                    EPUB_CURRENT_POSITION_READ_AT,
                    "${streamResponse.name}/$i",
                )
            }
        }
        
        // Invalidate cache for instant filter updates
        invalidateStatusCache()
        return true
    }

    fun hasAnyPreviousChapterUnread(chapter: ChapterData): Boolean {
        val streamResponse = (load as? StreamResponse) ?: return false
        val currentIndex = chapterIndex(chapter) ?: return false
        
        // Check if any chapter from index 0 to currentIndex (inclusive) is unread
        for (i in 0..currentIndex) {
            val isRead = getKey<Long>(
                EPUB_CURRENT_POSITION_READ_AT,
                "${streamResponse.name}/$i"
            ) != null
            if (!isRead) {
                return true // Found at least one unread chapter
            }
        }
        
        return false // All chapters are read
    }

    fun isChapterBookmarked(chapter: ChapterData): Boolean {
        val streamResponse = (load as? StreamResponse) ?: return false
        val index = chapterIndex(chapter) ?: return false
        
        // Use cached data if available, otherwise fallback to direct query
        if (statusCacheValid && index < cachedBookmarkStatuses.size) {
            return cachedBookmarkStatuses[index]
        }
        
        return getKey<Long>(
            "CHAPTER_BOOKMARK",
            "${streamResponse.name}/$index"
        ) != null
    }

    fun setChapterBookmark(chapter: ChapterData, value: Boolean): Boolean {
        val streamResponse = (load as? StreamResponse) ?: return false
        val index = chapterIndex(chapter) ?: return false

        if (value) {
            setKey(
                "CHAPTER_BOOKMARK",
                "${streamResponse.name}/$index",
                System.currentTimeMillis()
            )
        } else {
            removeKey(
                "CHAPTER_BOOKMARK",
                "${streamResponse.name}/$index"
            )
        }

        // Invalidate cache for instant filter updates
        invalidateStatusCache()
        return true
    }

    fun isChapterDownloaded(chapter: ChapterData): Boolean {
        val streamResponse = (load as? StreamResponse) ?: return false
        val index = chapterIndex(chapter) ?: return false
        
        // Use cached data if available, otherwise fallback to direct file check
        if (statusCacheValid && index < cachedDownloadStatuses.size) {
            return cachedDownloadStatuses[index]
        }
        
        val ctx = context ?: return false
        
        try {
            val sApiName = sanitizeFilename(apiName)
            val sAuthor = sanitizeFilename(streamResponse.author ?: "")
            val sName = sanitizeFilename(streamResponse.name)
            
            val filepath = ctx.filesDir.toString() + getFilename(sApiName, sAuthor, sName, index)
            val file = File(filepath)
            
            return file.exists() && file.length() > 10 // Minimum file size check
        } catch (e: Exception) {
            return false
        }
    }

    fun getBookmarkedChapters(): List<ChapterData> {
        val streamResponse = (load as? StreamResponse) ?: return emptyList()
        
        // Ensure cache is built
        if (!statusCacheValid) {
            buildStatusCache()
        }
        
        val bookmarkedChapters = mutableListOf<ChapterData>()
        
        streamResponse.data.forEachIndexed { index, chapter ->
            val isBookmarked = if (index < cachedBookmarkStatuses.size) {
                cachedBookmarkStatuses[index]
            } else {
                getKey<Long>("CHAPTER_BOOKMARK", "${streamResponse.name}/$index") != null
            }
            if (isBookmarked) {
                bookmarkedChapters.add(chapter)
            }
        }
        
        return bookmarkedChapters
    }

    fun getUnreadChapters(): List<ChapterData> {
        val streamResponse = (load as? StreamResponse) ?: return emptyList()
        
        // Ensure cache is built
        if (!statusCacheValid) {
            buildStatusCache()
        }
        
        val unreadChapters = mutableListOf<ChapterData>()
        
        streamResponse.data.forEachIndexed { index, chapter ->
            val isRead = if (index < cachedReadStatuses.size) {
                cachedReadStatuses[index]
            } else {
                getKey<Long>(EPUB_CURRENT_POSITION_READ_AT, "${streamResponse.name}/$index") != null
            }
            if (!isRead) {
                unreadChapters.add(chapter)
            }
        }
        
        return unreadChapters
    }

    fun getFilteredChapters(): List<ChapterData> {
        val streamResponse = (load as? StreamResponse) ?: return emptyList()
        val showUnreadOnly = _showUnreadOnly.value ?: false
        val showBookmarkedOnly = _showBookmarkedOnly.value ?: false
        val showDownloadedOnly = _showDownloadedOnly.value ?: false
        val sortType = _currentSortType.value ?: SortType.BY_SOURCE
        val isAscending = _isAscending.value ?: true
        
        // Start with all chapters
        var chapters = streamResponse.data
        
        // Apply filtering if any filters are active
        if (showUnreadOnly || showBookmarkedOnly || showDownloadedOnly) {
            // Ensure cache is built and valid
            if (!statusCacheValid) {
                buildStatusCache()
            }
            
            // Fast filtering using cached data
            val filteredChapters = mutableListOf<ChapterData>()
            
            streamResponse.data.forEachIndexed { index, chapter ->
                // Use cached statuses for instant filtering
                val isRead = if (index < cachedReadStatuses.size) cachedReadStatuses[index] else false
                val isBookmarked = if (index < cachedBookmarkStatuses.size) cachedBookmarkStatuses[index] else false
                val isDownloaded = if (index < cachedDownloadStatuses.size) cachedDownloadStatuses[index] else false
                
                // Include chapter if it matches ALL active filters
                val matchesUnreadFilter = !showUnreadOnly || !isRead
                val matchesBookmarkFilter = !showBookmarkedOnly || isBookmarked
                val matchesDownloadedFilter = !showDownloadedOnly || isDownloaded
                
                if (matchesUnreadFilter && matchesBookmarkFilter && matchesDownloadedFilter) {
                    filteredChapters.add(chapter)
                }
            }
            chapters = filteredChapters
        }
        
        // Apply sorting
        val sortedChapters = when (sortType) {
            SortType.BY_SOURCE -> {
                // By source: maintain original order or reverse it
                if (isAscending) {
                    chapters // Original provider order (ascending)
                } else {
                    chapters.reversed() // Reverse provider order (descending)
                }
            }
            // Future sorting types can be added here with new when branches
        }
        
        return sortedChapters
    }

    fun showChapterContextMenu(chapter: ChapterData) {
        val ctx = activity ?: return
        val bottomSheetDialog = BottomSheetDialog(ctx)
        val binding = ChapterContextMenuBinding.inflate(ctx.layoutInflater, null, false)
        bottomSheetDialog.setContentView(binding.root)

        // Set chapter title
        binding.chapterMenuTitle.text = chapter.name

        // Update mark as read button text and icon based on current state
        val isRead = hasReadChapter(chapter)
        binding.chapterMenuMarkRead.apply {
            text = ctx.getString(if (isRead) R.string.mark_as_unread else R.string.mark_as_read)
            setIconResource(if (isRead) R.drawable.ic_baseline_collections_bookmark_24 else R.drawable.ic_baseline_check_24)
        }

        // Update mark all previous button text and icon based on whether any previous chapters are unread
        val hasUnreadPrevious = hasAnyPreviousChapterUnread(chapter)
        binding.chapterMenuMarkAllPrevious.apply {
            text = ctx.getString(if (hasUnreadPrevious) R.string.mark_all_previous_as_read else R.string.mark_all_previous_as_unread)
            setIconResource(if (hasUnreadPrevious) R.drawable.ic_baseline_check_24 else R.drawable.ic_baseline_collections_bookmark_24)
        }

        // Update bookmark button text and icon based on current bookmark state
        val isBookmarked = isChapterBookmarked(chapter)
        binding.chapterMenuBookmark.apply {
            text = ctx.getString(if (isBookmarked) R.string.remove_bookmark else R.string.bookmark_chapter)
            setIconResource(if (isBookmarked) R.drawable.ic_baseline_bookmark_24 else R.drawable.ic_baseline_bookmark_border_24)
        }

        // Set up button click listeners
        binding.chapterMenuMarkRead.setOnClickListener {
            setReadChapter(chapter, !isRead)
            triggerChapterRefresh()
            bottomSheetDialog.dismiss()
        }

        binding.chapterMenuMarkAllPrevious.setOnClickListener {
            // If any previous chapters are unread, mark all as read; otherwise mark all as unread
            markAllPreviousChapters(chapter, hasUnreadPrevious)
            triggerChapterRefresh()
            bottomSheetDialog.dismiss()
        }

        binding.chapterMenuBookmark.setOnClickListener {
            setChapterBookmark(chapter, !isBookmarked)
            triggerChapterRefresh()
            bottomSheetDialog.dismiss()
        }

        binding.chapterMenuShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out this chapter: ${chapter.name} - ${chapter.url}")
            }
            ctx.startActivity(Intent.createChooser(shareIntent, "Share Chapter"))
            bottomSheetDialog.dismiss()
        }

        binding.chapterMenuCopyUrl.setOnClickListener {
            val clipboard = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Chapter URL", chapter.url)
            clipboard.setPrimaryClip(clip)
            showToast(ctx, "URL copied to clipboard")
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    lateinit var repo: APIRepository

    var isGetLoaded = false

    var id: MutableLiveData<Int> = MutableLiveData<Int>(-1)
    var readState: MutableLiveData<ReadType> = MutableLiveData<ReadType>(ReadType.NONE)

    val api get() = repo
    val apiName get() = api.name

    val currentTabIndex: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    val currentTabPosition: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    private val loadMutex = Mutex()
    private lateinit var load: LoadResponse
    private var loadId: Int = 0
    private var loadUrl: String = ""
    private var hasLoaded: Boolean = false

    val loadResponse: MutableLiveData<Resource<LoadResponse>?> =
        MutableLiveData<Resource<LoadResponse>?>()

    // LiveData to trigger chapter list refresh when read status changes
    private val _chapterRefreshTrigger = MutableLiveData<Boolean>()
    val chapterRefreshTrigger: LiveData<Boolean> = _chapterRefreshTrigger

    // LiveData to track filter states
    private val _showUnreadOnly = MutableLiveData<Boolean>(false)
    val showUnreadOnly: LiveData<Boolean> = _showUnreadOnly
    
    private val _showBookmarkedOnly = MutableLiveData<Boolean>(false)
    val showBookmarkedOnly: LiveData<Boolean> = _showBookmarkedOnly
    
    private val _showDownloadedOnly = MutableLiveData<Boolean>(false)
    val showDownloadedOnly: LiveData<Boolean> = _showDownloadedOnly

    // LiveData to track sort states
    enum class SortType {
        BY_SOURCE
        // Future sorting types can be added here
    }
    
    private val _currentSortType = MutableLiveData<SortType>(SortType.BY_SOURCE)
    val currentSortType: LiveData<SortType> = _currentSortType
    
    private val _isAscending = MutableLiveData<Boolean>(true)
    val isAscending: LiveData<Boolean> = _isAscending

    // Performance optimization: Cache chapter statuses for instant filtering
    private var cachedReadStatuses: BooleanArray = BooleanArray(0)
    private var cachedBookmarkStatuses: BooleanArray = BooleanArray(0)
    private var cachedDownloadStatuses: BooleanArray = BooleanArray(0)
    private var statusCacheValid = false

    private fun triggerChapterRefresh() {
        _chapterRefreshTrigger.postValue(true)
    }

    private fun invalidateStatusCache() {
        statusCacheValid = false
    }

    private fun buildStatusCache() {
        val streamResponse = (load as? StreamResponse) ?: return
        val chapterCount = streamResponse.data.size
        
        if (chapterCount == 0) return
        
        // Initialize arrays
        cachedReadStatuses = BooleanArray(chapterCount)
        cachedBookmarkStatuses = BooleanArray(chapterCount)
        cachedDownloadStatuses = BooleanArray(chapterCount)
        
        // Batch load read statuses
        for (index in 0 until chapterCount) {
            cachedReadStatuses[index] = getKey<Long>(
                EPUB_CURRENT_POSITION_READ_AT,
                "${streamResponse.name}/$index"
            ) != null
        }
        
        // Batch load bookmark statuses
        for (index in 0 until chapterCount) {
            cachedBookmarkStatuses[index] = getKey<Long>(
                "CHAPTER_BOOKMARK",
                "${streamResponse.name}/$index"
            ) != null
        }
        
        // Batch check download statuses (optimized)
        batchCheckDownloadedChapters(streamResponse)
        
        statusCacheValid = true
    }

    private fun batchCheckDownloadedChapters(streamResponse: StreamResponse) {
        val ctx = context ?: return
        val chapterCount = streamResponse.data.size
        
        try {
            val sApiName = sanitizeFilename(apiName)
            val sAuthor = sanitizeFilename(streamResponse.author ?: "")
            val sName = sanitizeFilename(streamResponse.name)
            
            // Get the directory once
            val dir = File(ctx.filesDir.toString() + getDirectory(sApiName, sAuthor, sName))
            
            if (!dir.exists()) {
                // No downloads for this novel
                for (index in 0 until chapterCount) {
                    cachedDownloadStatuses[index] = false
                }
                return
            }
            
            // Get all existing files at once
            val existingFiles = dir.listFiles()?.mapNotNull { file ->
                file.nameWithoutExtension.toIntOrNull()?.let { index ->
                    index to (file.length() > 10)
                }
            }?.toMap() ?: emptyMap()
            
            // Set download status based on existing files
            for (index in 0 until chapterCount) {
                cachedDownloadStatuses[index] = existingFiles[index] == true
            }
            
        } catch (e: Exception) {
            // If there's an error, mark all as not downloaded
            for (index in 0 until chapterCount) {
                cachedDownloadStatuses[index] = false
            }
        }
    }

    fun toggleUnreadFilter() {
        _showUnreadOnly.postValue(!(_showUnreadOnly.value ?: false))
        triggerChapterRefresh()
    }

    fun setUnreadFilter(enabled: Boolean) {
        _showUnreadOnly.postValue(enabled)
        triggerChapterRefresh()
    }

    fun setBookmarkFilter(enabled: Boolean) {
        _showBookmarkedOnly.postValue(enabled)
        triggerChapterRefresh()
    }

    fun setDownloadedFilter(enabled: Boolean) {
        _showDownloadedOnly.postValue(enabled)
        triggerChapterRefresh()
    }

    // Sort methods
    fun setSortBySource() {
        val currentAscending = _isAscending.value ?: true
        // Toggle direction (since we only have source sorting for now)
        _isAscending.value = !currentAscending
        triggerChapterRefresh()
    }

    // Template for future sort methods:
    // fun setSortBy[NewType]() {
    //     val currentType = _currentSortType.value
    //     val currentAscending = _isAscending.value ?: true
    //     
    //     if (currentType == SortType.BY_[NEW_TYPE]) {
    //         // Toggle direction if already on this sort
    //         _isAscending.postValue(!currentAscending)
    //     } else {
    //         // Switch to new sort with ascending order
    //         _currentSortType.postValue(SortType.BY_[NEW_TYPE])
    //         _isAscending.postValue(true)
    //     }
    //     triggerChapterRefresh()
    // }

    val reviews: MutableLiveData<Resource<ArrayList<UserReview>>> by lazy {
        MutableLiveData<Resource<ArrayList<UserReview>>>()
    }
    private var currentReviews: ArrayList<UserReview> = arrayListOf()

    private val reviewPage: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    private val loadMoreReviewsMutex = Mutex()
    private fun loadMoreReviews(url: String) {
        viewModelScope.launch {
            if (loadMoreReviewsMutex.isLocked) return@launch
            loadMoreReviewsMutex.withLock {
                val loadPage = (reviewPage.value ?: 0) + 1
                if (loadPage == 1) {
                    reviews.postValue(Resource.Loading())
                }
                when (val data = repo.loadReviews(url, loadPage, false)) {
                    is Resource.Success -> {
                        val moreReviews = data.value
                        currentReviews.addAll(moreReviews)

                        reviews.postValue(Resource.Success(currentReviews))
                        reviewPage.postValue(loadPage)
                    }

                    else -> {}
                }
            }
        }
    }

    fun openInBrowser() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (loadUrl.isBlank()) return@launchSafe
            val i = Intent(Intent.ACTION_VIEW)
            i.data = loadUrl.toUri()
            activity?.startActivity(i)
        }
    }

    fun switchTab(index: Int?, position: Int?) {
        val newPos = index ?: return
        currentTabPosition.postValue(position ?: return)
        currentTabIndex.postValue(newPos)
        if (newPos == 1 && currentReviews.isEmpty()) {
            loadMoreReviews(verify = false)
        }
    }

    fun readEpub() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe
            addToHistory()
            BookDownloader2.readEpub(
                loadId,
                downloadState.value?.progress?.toInt() ?: return@launchSafe,
                load.author,
                load.name,
                apiName,
                load.synopsis
            )
        }
    }

    private var cachedChapters: HashMap<ChapterData, Int> = hashMapOf()

    private fun chapterIndex(chapter: ChapterData): Int? {
        return cachedChapters[chapter]
    }

    private fun reCacheChapters() {
        val streamResponse = (load as? StreamResponse)
        if (streamResponse == null) {
            cachedChapters = hashMapOf()
            return
        }
        val out = hashMapOf<ChapterData, Int>()
        streamResponse.data.mapIndexed { index, chapterData ->
            out[chapterData] = index
        }
        cachedChapters = out
    }

    fun streamRead(chapter: ChapterData? = null) = ioSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@ioSafe
            addToHistory()

            chapter?.let {
                // TODO BETTER STORE
                val streamResponse =
                    ((loadResponse.value as? Resource.Success)?.value as? StreamResponse)
                        ?: return@let
                val index = chapterIndex(chapter)
                if (index != null && index >= 0) {
                    setReadChapter(chapter, true)
                    setKey(EPUB_CURRENT_POSITION, streamResponse.name, index)
                    setKey(
                        EPUB_CURRENT_POSITION_CHAPTER,
                        streamResponse.name,
                        streamResponse.data[index].name
                    )
                    setKey(
                        EPUB_CURRENT_POSITION_SCROLL_CHAR, streamResponse.name, 0,
                    )
                }
            }

            BookDownloader2.stream(load, apiName)
        }
    }

    /** paused => resume,
     *  downloading => pause,
     *  done / pending => nothing,
     *  else => download
     * */
    fun downloadOrPause() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe

            BookDownloader2.downloadInfoMutex.withLock {
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.IsPaused -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Resume
                        )

                        DownloadState.IsDownloading -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Pause
                        )

                        DownloadState.IsDone, DownloadState.IsPending -> {

                        }

                        else -> BookDownloader2.download(load, context ?: return@launchSafe)
                    }
                } ?: run {
                    BookDownloader2.download(load, context ?: return@launchSafe)
                }
            }
        }
    }

    fun pause() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe

            BookDownloader2.downloadInfoMutex.withLock {
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.IsDownloading -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Pause
                        )

                        else -> {

                        }
                    }
                }
            }
        }
    }

    fun stop() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe

            BookDownloader2.downloadInfoMutex.withLock {
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.Nothing, DownloadState.IsDone, DownloadState.IsStopped, DownloadState.IsFailed -> {

                        }

                        else -> {
                            BookDownloader2.addPendingAction(
                                loadId,
                                DownloadActionType.Stop
                            )
                        }
                    }
                }
            }
        }
    }

    fun downloadFrom(start: Int?) = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe
            BookDownloader2.downloadInfoMutex.withLock {
                BookDownloader2.changeDownloadStart(load, api, start)
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.IsPaused -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Resume
                        )

                        DownloadState.IsPending -> {

                        }

                        // DownloadState.IsDone
                        else -> BookDownloader2.download(load, context ?: return@launchSafe)
                    }
                } ?: run {
                    BookDownloader2.download(load, context ?: return@launchSafe)
                }
            }
        }
    }

    fun download() = viewModelScope.launchSafe {
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe
            BookDownloader2.downloadInfoMutex.withLock {
                downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.IsPaused -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Resume
                        )

                        DownloadState.IsDone, DownloadState.IsPending -> {

                        }

                        else -> BookDownloader2.download(load, context ?: return@launchSafe)
                    }
                } ?: run {
                    BookDownloader2.download(load, context ?: return@launchSafe)
                }
            }
        }
    }


    private fun addToHistory() = viewModelScope.launchSafe {
        // we wont add it to history from cache
        if (!isGetLoaded) return@launchSafe
        loadMutex.withLock {
            if (!hasLoaded) return@launchSafe
            setKey(
                HISTORY_FOLDER, loadId.toString(), ResultCached(
                    loadUrl,
                    load.name,
                    apiName,
                    loadId,
                    load.author,
                    load.posterUrl,
                    load.tags,
                    load.rating,
                    (load as? StreamResponse)?.data?.size ?: 1,
                    System.currentTimeMillis(),
                    synopsis = load.synopsis
                )
            )
        }
    }

// requireContext().setKey(DOWNLOAD_TOTAL, localId.toString(), res.data .size)
// loadReviews()

    fun isInReviews(): Boolean {
        return currentTabIndex.value == 1
    }

    fun deleteAlert() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        delete()
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
            val act = activity ?: return@launch
            val builder: AlertDialog.Builder = AlertDialog.Builder(act)
            builder.setMessage(act.getString(R.string.permanently_delete_format).format(load.name))
                .setTitle(R.string.delete)
                .setPositiveButton(R.string.delete, dialogClickListener)
                .setNegativeButton(R.string.cancel, dialogClickListener)
                .show()
        }
    }

    fun delete() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            BookDownloader2.deleteNovel(load.author, load.name, apiName)
        }
    }

    private fun updateBookmarkData() {
        // dont update data if preview because that data is from cache
        if (!isGetLoaded && getKey<ResultCached>(RESULT_BOOKMARK, loadId.toString()) != null) {
            return
        }

        setKey(
            RESULT_BOOKMARK, loadId.toString(), ResultCached(
                loadUrl,
                load.name,
                apiName,
                loadId,
                load.author,
                load.posterUrl,
                load.tags,
                load.rating,
                (load as? StreamResponse)?.data?.size ?: 1,
                System.currentTimeMillis(),
                synopsis = load.synopsis
            )
        )
    }

    fun bookmark(state: Int) = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            setKey(
                RESULT_BOOKMARK_STATE, loadId.toString(), state
            )
            updateBookmarkData()
        }

        readState.postValue(ReadType.fromSpinner(state))
    }

    fun share() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch

            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_SUBJECT, load.name)
            i.putExtra(Intent.EXTRA_TEXT, loadUrl)
            activity?.startActivity(Intent.createChooser(i, load.name))
        }
    }

    fun loadMoreReviews(verify: Boolean = true) = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            if (verify && currentTabIndex.value == 0) return@launch
            loadMoreReviews(loadUrl)
        }
    }

    init {
        // BookDownloader2.downloadDataChanged += ::progressDataChanged
        BookDownloader2.downloadProgressChanged += ::progressChanged
        BookDownloader2.downloadRemoved += ::downloadRemoved
    }

    override fun onCleared() {
        super.onCleared()
        BookDownloader2.downloadProgressChanged -= ::progressChanged
        //BookDownloader2.downloadDataChanged -= ::progressDataChanged
        BookDownloader2.downloadRemoved -= ::downloadRemoved
    }

    val downloadState: MutableLiveData<DownloadProgressState> by lazy {
        MutableLiveData<DownloadProgressState>(null)
    }

    private fun progressChanged(data: Pair<Int, DownloadProgressState>) =
        viewModelScope.launch {
            val (id, state) = data
            loadMutex.withLock {
                if (!hasLoaded || id != loadId) return@launch
                downloadState.postValue(state)
                
                // Invalidate download status cache when download state changes
                // This ensures downloaded filter updates when chapters complete downloading
                if (state.state == DownloadState.IsDone || state.progress > 0) {
                    invalidateStatusCache()
                }
            }
        }

    /*fun progressDataChanged(data: Pair<Int, DownloadFragment.DownloadData>) =
        viewModelScope.launch {
            val (id, downloadData) = data
            loadMutex.withLock {
                if (!hasLoaded || id != loadId) return@launch

            }
        }*/

    private fun downloadRemoved(id: Int) = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded || id != loadId) return@launch
            insertZeroData()
            // Invalidate download status cache when downloads are removed
            invalidateStatusCache()
        }
    }

    private fun insertZeroData() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch

            BookDownloader2.downloadInfoMutex.withLock {
                val current = downloadProgress[loadId]
                if (current != null) {
                    downloadState.postValue(current)
                } else {
                    BookDownloader2Helper.downloadInfo(
                        context,
                        load.author,
                        load.name,
                        load.apiName
                    )?.let { info ->
                        val new = DownloadProgressState(
                            state = DownloadState.Nothing,
                            progress = info.progress,
                            total = info.total,
                            downloaded = info.downloaded,
                            lastUpdatedMs = System.currentTimeMillis(),
                            etaMs = null
                        )
                        downloadProgress[loadId] = new
                        downloadState.postValue(new)
                    } ?: run {
                        val new = DownloadProgressState(
                            state = DownloadState.Nothing,
                            progress = 0,
                            total = (load as? StreamResponse)?.data?.size?.toLong() ?: 1,
                            downloaded = 0,
                            lastUpdatedMs = System.currentTimeMillis(),
                            etaMs = null
                        )
                        downloadState.postValue(new)
                    }
                }
            }
        }
    }

    fun initState(card: ResultCached) = viewModelScope.launch {
        isGetLoaded = false
        loadMutex.withLock {
            repo = Apis.getApiFromName(card.apiName)
            loadUrl = card.source

            val data = StreamResponse(
                url = card.source,
                name = card.name,
                data = listOf(),
                author = card.author,
                posterUrl = card.poster,
                rating = card.rating,
                synopsis = card.synopsis,
                tags = card.tags,
                apiName = card.apiName
            )

            load = data
            loadResponse.postValue(Resource.Success(data))

            setState(card.id)
        }
    }

    private fun setState(tid: Int) {
        loadId = tid

        readState.postValue(
            ReadType.fromSpinner(
                getKey(
                    RESULT_BOOKMARK_STATE, tid.toString()
                )
            )
        )

        setKey(
            DOWNLOAD_EPUB_LAST_ACCESS, tid.toString(), System.currentTimeMillis()
        )
        reCacheChapters()
        updateBookmarkData()
        
        // Invalidate status cache when loading a new novel
        invalidateStatusCache()

        hasLoaded = true

        // insert a download progress if not found
        insertZeroData()
    }

    fun initState(card: DownloadFragment.DownloadDataLoaded) = viewModelScope.launch {
        isGetLoaded = false
        loadResponse.postValue(Resource.Loading(card.source))

        loadMutex.withLock {
            repo = Apis.getApiFromName(card.apiName)
            loadUrl = card.source

            val data = StreamResponse(
                url = card.source,
                name = card.name,
                data = listOf(),
                author = card.author,
                posterUrl = card.posterUrl,
                rating = card.rating,
                synopsis = card.synopsis,
                tags = card.tags,
                apiName = card.apiName
            )
            load = data
            loadResponse.postValue(Resource.Success(data))

            setState(card.id)
        }
    }

    fun initState(apiName: String, url: String) = viewModelScope.launch {
        isGetLoaded = true
        loadResponse.postValue(Resource.Loading(url))

        loadMutex.withLock {
            repo = Apis.getApiFromName(apiName)
            loadUrl = url
        }

        val data = repo.load(url)
        loadMutex.withLock {
            when (data) {
                is Resource.Success -> {
                    val res = data.value

                    load = res
                    loadUrl = res.url

                    val tid = generateId(res, apiName)
                    setState(tid)
                }

                else -> {}
            }
            loadResponse.postValue(data)
        }
    }
}
