package com.lagradost.quicknovel.downloader

import android.content.Context
import android.net.Uri
import androidx.annotation.WorkerThread
import androidx.compose.runtime.Immutable
import com.anggrayudi.storage.StorageFile
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BaseApplication
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2.LOCAL_EPUB_MIN_SIZE
import com.lagradost.quicknovel.DOWNLOAD_FOLDER
import com.lagradost.quicknovel.DOWNLOAD_OFFSET
import com.lagradost.quicknovel.DOWNLOAD_POSTER
import com.lagradost.quicknovel.DOWNLOAD_TOTAL
import com.lagradost.quicknovel.DownloadFileWorkManager
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.downloader.BookFiles.migrateAllInformation
import com.lagradost.quicknovel.downloader.BookFiles.resetApiLastRequestTime
import com.lagradost.quicknovel.downloader.BookFiles.transferTo
import com.lagradost.quicknovel.downloader.BookFiles.updatePersistentData
import com.lagradost.quicknovel.downloader.BookFiles.waitForAction
import com.lagradost.quicknovel.downloader.BookState.currentStatusMap
import com.lagradost.quicknovel.downloader.BookState.getDownloads
import com.lagradost.quicknovel.downloader.BookState.postRemoved
import com.lagradost.quicknovel.downloader.BookState.postStateUpdate
import com.lagradost.quicknovel.downloader.BookState.setDone
import com.lagradost.quicknovel.downloader.BookState.updateDownloadState
import com.lagradost.quicknovel.downloader.BookState.updateState
import com.lagradost.quicknovel.downloader.PDFDownloader.importPdf
import com.lagradost.quicknovel.downloader.PDFDownloader.importPdfWithDirectly
import com.lagradost.quicknovel.extractors.ExtractorApi
import com.lagradost.quicknovel.get
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.mvvm.safeAsync
import com.lagradost.quicknovel.ui.common.ImmutableChapterProgress
import com.lagradost.quicknovel.ui.common.ImmutableDownloadState
import com.lagradost.quicknovel.ui.common.ImmutableLoadData
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse.Companion.epubSize
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse.Companion.timeOfPageOpened
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.ag2s.epublib.epub.EpubReader
import me.ag2s.epublib.util.zip.AndroidZipFile
import java.io.FileNotFoundException
import java.io.IOException
import java.util.BitSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

enum class BookAction {
    Stop,
    Resume,
    Pause,
}

enum class BookDownloadStatus {
    /** Paused by user */
    Paused,

    /** Currently downloading */
    Downloading,

    /** Download fully done */
    Done,

    /** Download failed due to e.g. networking */
    Failed,

    /** Download stopped by the user */
    Stopped,

    /** Download currently pending */
    Pending,

    /** Download in idle, nothing has happened */
    Idle,
}

typealias MonotonicInstant = TimeSource.Monotonic.ValueTimeMark

/**
 * As the regular clock is not monotonic and System ms is to course,
 * this makes (duration) calculations more expected and easier
 * */
fun monotonicNow() = TimeSource.Monotonic.markNow()

@Immutable
data class BookHandle(
    /** Send an action to this handle */
    val notification: Channel<BookAction>,
    val files: BookFileHandle,
    /**
     * The unique mutex for this job, following the principles of (pre-)locked -> unlocked
     *
     * IT should NEVER be relocked after using it once,
     * and should instead be replaced after 1 use.
     *
     * This is so any thread can just wait for the mutex to be unlocked,
     * and then use the built-in replace function in concurrent hashmap.
     * */
    val mutex: Mutex = Mutex(),
    val id: Int,
    val created: Long = System.currentTimeMillis(),
) {
    companion object {
        fun from(context: Context, response: ImmutableSearchResponse, id: Int) =
            BookHandle(
                /** Always create a new channel to avoid old messages */
                notification = Channel(
                    capacity = 1,
                    onBufferOverflow = BufferOverflow.DROP_OLDEST
                ),
                files = BookFileHandle(
                    apiName = response.apiName,
                    author = response.author,
                    name = response.name,
                    fileDir = context.filesDir,
                    cacheDir = context.cacheDir
                ),
                mutex = Mutex(),
                id = id
            )
    }
}

@Immutable
data class BookStateChanged(
    val id: Int,
    val response: ImmutableSearchResponse,
)

@Immutable
data class BookStatus(
    val handle: BookHandle,
    val response: ImmutableSearchResponse,
) {
    val apiName get() = handle.files.apiName
    val id get() = handle.id

    @Throws
    fun getApi() = handle.files.getApi()
}

/** Append some extra information to the download */
@Immutable
sealed class BookDownloadPackage {
    data class Chapters(val set: BitSet) : BookDownloadPackage()
    data class Import(val uri: Uri) : BookDownloadPackage()
    object Nothing : BookDownloadPackage()
    data class AfterOffset(val offset: Int) : BookDownloadPackage()
    data class PDF(val pdfDocument: PDDocument) : BookDownloadPackage()
}

@Immutable
data class BookDownloadPayload(
    val response: ImmutableSearchResponse,
    val bookPackage: BookDownloadPackage,
)

@Immutable
data class BookDownloadListPayload(
    val payloads: PersistentList<BookDownloadPayload>
)

@Immutable
data class BookRefreshListPayload(
    val page: Int,
    val responses: PersistentList<ImmutableSearchResponse>
)

fun BitSet.copy(): BitSet {
    return this.clone() as BitSet
}

/**
 * The core idea of this downloader is to have a persistent single source of truth for the download
 * state.
 *
 * The downloads are cooperative, meaning that we have to manually define suspension points
 * for e.g. pause or stop. This was done as we do not want to cancel the entire job on an action,
 * nor are some transactions pausable.
 *
 * Each active download have a handle (for files), and some metadata (for notifications or epub generation)
 *
 * We have split this downloader up into parts, to make it easier to navigate and read, but these
 * are not iron clad rules:
 *
 * BookState: Listen to this source, and handle state updates to this state
 * BookFiles: Handle file actions
 * BookReader: Henerate the epub for reading
 * BookNotifications: Handle user actions on downloads, and post the UI notifications
 * PDFDownloader: Special case for PDF, as they are complicated
 */
object BookDownloader {
    suspend fun delete(id: Int) = safeAsync {
        deleteDownload(id)
    }

    fun refreshBookmarks(
        data: BookRefreshListPayload,
        context: Context? = BaseApplication.context
    ) {
        val context = context ?: return
        DownloadFileWorkManager.startRefreshBookmark(data = data, context = context)
    }

    fun download(
        response: ImmutableSearchResponse,
        bookPackage: BookDownloadPackage, context: Context? = BaseApplication.context
    ) = safe {
        val id = response.id ?: return@safe
        val current = currentStatusMap[response.id]

        /**
         * Fast case, we avoid the DownloadFileWorkManager if possible as it is slow af
         * */
        when (current?.response?.downloadState?.downloadStatus) {
            BookDownloadStatus.Paused -> {
                applyPackage(id, bookPackage)
                current.handle.notification.trySend(BookAction.Resume)
                return@safe
            }

            BookDownloadStatus.Downloading, BookDownloadStatus.Pending -> {
                applyPackage(id, bookPackage)
                return@safe
            }

            else -> {
                DownloadFileWorkManager.startDownload(
                    BookDownloadPayload(
                        response = response,
                        bookPackage = bookPackage
                    ),
                    downloadId = id,
                    context = context ?: return@safe
                )
            }
        }
    }

    fun downloadList(
        payload: BookDownloadListPayload,
        context: Context? = BaseApplication.context
    ) = safe {
        val copy = payload.payloads.removingAll { (response, bookPackage) ->
            val id = response.id ?: return@removingAll true
            val current = currentStatusMap[response.id]

            /**
             * Fast case, we avoid the DownloadFileWorkManager if possible as it is slow af
             * */
            when (current?.response?.downloadState?.downloadStatus) {
                BookDownloadStatus.Paused -> {
                    applyPackage(id, bookPackage)
                    current.handle.notification.trySend(BookAction.Resume)
                    return@removingAll true
                }

                BookDownloadStatus.Downloading, BookDownloadStatus.Pending -> {
                    applyPackage(id, bookPackage)
                    return@removingAll true
                }

                else -> {
                    false
                }
            }
        }

        if (copy.isNotEmpty()) {
            DownloadFileWorkManager.startRefreshDownloads(
                data = payload.copy(payloads = copy),
                context = context ?: return@safe
            )
        }
    }

    @Throws
    private suspend fun deleteDownload(id: Int) = withContext(Dispatchers.IO) {
        if (!currentStatusMap.containsKey(id)) {
            return@withContext
        }
        val newMutex = Mutex()
        newMutex.withLock {
            /**
             * Acquire the unique lock for this object,
             * by sending stop and waiting for it to unlock,
             * before replacing the ownership with the newMutex
             * */
            val status: BookStatus
            while (true) {
                val currentStatus = currentStatusMap[id] ?: return@withContext
                currentStatus.handle.notification.trySend(BookAction.Stop)
                if (currentStatus.handle.mutex.isLocked) {
                    delay(50.milliseconds)
                    continue
                }
                val newStatus =
                    currentStatus.copy(handle = currentStatus.handle.copy(mutex = newMutex))
                if (currentStatusMap.replace(id, currentStatus, newStatus)) {
                    status = newStatus
                    break
                }
            }

            /** We have full ownership of the local files here, so we can delete them safely */
            BookFiles.readDeleteFileLock.withLock {
                BookFiles.removeDownloadedFiles(status)
            }

            postRemoved(id)
        }
    }

    /**
     * Bookmarks should also migrate information, however this migration should only happen if
     * both the migration from and to is not already downloaded.
     *
     * As the downloader should migrate outdated bookmarks when refreshing a download
     * */
    suspend fun migrateNonDownloadedInformation(
        from: ImmutableSearchResponse,
        to: ImmutableSearchResponse,
        context: Context,
    ) = withContext(Dispatchers.IO) {
        require(from.id != null)
        require(to.id != null)
        if (currentStatusMap.containsKey(from.id) || currentStatusMap.containsKey(to.id)) {
            return@withContext
        }
        val fromHandle = BookHandle.from(context = context, response = from, id = from.id)
        val toHandle = BookHandle.from(context = context, response = to, id = to.id)
        val fromStatus = BookStatus(handle = fromHandle, response = from)
        val toStatus = BookStatus(handle = toHandle, response = to)
        fromHandle.mutex.withLock {
            toHandle.mutex.withLock {
                if (currentStatusMap.putIfAbsent(
                        from.id,
                        fromStatus
                    ) != null || currentStatusMap.putIfAbsent(to.id, toStatus) != null
                ) {
                    return@withContext
                }
                /** We can now safely migrate from A -> B as both A and B are not downloaded */
                migrateAllInformation(fromStatus = fromStatus, toStatus = toStatus)
            }
        }
    }

    @WorkerThread
    private suspend fun startNewDownload(
        context: Context,
        handle: BookHandle,
        response: ImmutableSearchResponse,
        bookPackage: BookDownloadPackage,
        preloaded: suspend (BookStatus) -> Unit
    ) {
        val files = handle.files
        val newMutex = handle.mutex

        val lastDownloaded = getKey<DownloadFragment.DownloadData>(
            DOWNLOAD_FOLDER,
            handle.id.toString()
        )?.lastDownloaded ?: System.currentTimeMillis()
        val downloadOffset = getKey<Int>(DOWNLOAD_OFFSET, handle.id.toString()) ?: 0
        val timeOfPageOpened = timeOfPageOpened(handle.id)
        val epubGenerated = epubSize(handle.id)

        /**
         * Atomically start a new download with this response,
         * by replacing the old state, creating a new state or resuming the old state
         *
         * This ensures global uniqueness
         * */
        newMutex.withLock {
            val id = files.id
            var activeStatus: BookStatus
            /** To do atomic we loop until we have replaced or put the item */
            while (true) {
                val current = currentStatusMap[id]
                val newStatus = if (current != null) {
                    /** Reuse if possible, if not we try to apply the package */
                    when (current.response.downloadState?.downloadStatus) {
                        BookDownloadStatus.Paused -> {
                            applyPackage(id, bookPackage)
                            current.handle.notification.trySend(BookAction.Resume)
                            return
                        }

                        BookDownloadStatus.Downloading, BookDownloadStatus.Pending -> {
                            applyPackage(id, bookPackage)
                            return
                        }

                        else -> {
                            /**
                             * In use, do nothing.
                             *
                             * This should be fine to race as currentStatusMap replace
                             * will handle the race case fine.
                             *
                             * HOWEVER, this will break if we lock->unlock->lock, that is why
                             * we use newMutex.withLock outside the function
                             * */
                            if (current.handle.mutex.isLocked) {
                                applyPackage(id, bookPackage)
                                return
                            }
                        }
                    }

                    /** Replace the mutex with the active mutex */
                    current.copy(
                        handle = handle,
                        /** Use the new response instead of the one that is cached */
                        response = response.copy(
                            /**
                             * Explicitly always use the most up-to-date load data, even if it is null,
                             * as otherwise, new chapters will never get checked
                             * */
                            loadData = response.loadData,
                            epubSize = epubGenerated,
                            timeOfCached = handle.created,
                            timeOfChapterDownloaded = lastDownloaded,
                            timeOfPageOpened = timeOfPageOpened,
                            downloadState = current.response.downloadState?.copy(
                                downloadStatus = BookDownloadStatus.Pending,
                            )
                        )
                    )
                } else {
                    BookStatus(
                        handle = handle,
                        response = response.copy(
                            epubSize = epubGenerated,
                            timeOfCached = handle.created,
                            timeOfChapterDownloaded = lastDownloaded,
                            timeOfPageOpened = timeOfPageOpened,
                            downloadState = ImmutableDownloadState(
                                chapterProgress = null,
                                downloadedChapters = getDownloads(files = files),
                                pendingChapters = BitSet(),
                                downloadedBytes = 0L,
                                totalBytes = 0L,
                                downloadStatus = BookDownloadStatus.Pending,
                                error = null,
                                downloadOffset = downloadOffset
                            )
                        )
                    )
                }

                /** Acquire by replacing / putting */
                if (current == null) {
                    if (currentStatusMap.putIfAbsent(id, newStatus) == null) {
                        activeStatus = newStatus
                        break
                    }
                } else {
                    if (currentStatusMap.replace(id, current, newStatus)) {
                        activeStatus = newStatus
                        break
                    }
                }
            }
            try {
                preloaded(activeStatus)
                postStateUpdate(
                    response = activeStatus.response,
                    id = activeStatus.id,
                    notify = false
                )
                runDownload(status = activeStatus, bookPackage = bookPackage, context = context)
            } catch (error: Throwable) {
                logError(error)
                BookState.setError(id, error)
            }
        }
    }

    suspend fun startNewDownloadOrReturn(
        context: Context,
        response: ImmutableSearchResponse,
        bookPackage: BookDownloadPackage,
        preloaded: suspend (BookStatus) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val id = response.id ?: return@withContext
        val handle = BookHandle.from(context = context, response = response, id = id)

        /** Always try to spawn the notification thread to make sure it is alive */
        ioSafe {
            BookNotifications.notificationThread(context = context)
        }

        startNewDownload(
            context = context,
            handle = handle,
            response = response,
            bookPackage = bookPackage,
            preloaded = preloaded
        )
    }

    @Throws
    private suspend fun runDownload(
        context: Context,
        status: BookStatus,
        bookPackage: BookDownloadPackage
    ) {
        /**
         * Case for imported books, as we do not include the URI directly into the response
         * */
        if (bookPackage is BookDownloadPackage.Import) {
            importObject(context = context, uri = bookPackage.uri, status = status)
            return
        }

        /**
         * This is for the second pass of the PDF import loop
         * */
        if (bookPackage is BookDownloadPackage.PDF) {
            importPdfWithDirectly(status = status, document = bookPackage.pdfDocument)
            return
        }

        /**
         * Case for books that are already loaded, like results page then we can skip reloading
         * the page
         * */
        if (status.response.loadData != null) {
            applyPackage(id = status.id, bookPackage = bookPackage)
            downloadFromLoad(response = status.response, handle = status.handle)
            return
        }

        /**
         * Otherwise we have to load the page again, e.g. if we download directly from the downloads
         * page.
         * */
        if (resetApiLastRequestTime(handle = status.handle)) {
            return
        }
        val api = status.getApi()
        val newResponse = api.loadResult(status.response.url, allowCache = false).getOrThrow()
            .copy(
                timeOfCached = status.response.timeOfCached,
                timeOfChapterDownloaded = status.response.timeOfChapterDownloaded,
                downloadState = status.response.downloadState
            )
        require(newResponse.id != null)

        if (newResponse.id != status.id) {
            /**
             * Lock both this id, and recursively try to load the inner.
             *
             * This can not create a deadlock, because it will ignore the request
             * if we have a pending request.
             * */
            startNewDownloadOrReturn(
                context = context,
                response = newResponse,
                bookPackage = bookPackage,
                preloaded = { newStatus ->
                    migrateAllInformation(fromStatus = status, toStatus = newStatus)
                    postRemoved(status.id)
                })

            /**
             * In case we did not remove it (because we already download it),
             * then mark it as done without removing the files.
             *
             * This is because we do not want to accidentally remove a lot of files here!
             * */
            updateDownloadState(status.id) {
                copy(
                    downloadStatus = BookDownloadStatus.Done,
                    error = null,
                    pendingChapters = BitSet()
                )
            }
            return
        }

        /** We need to update the state information */
        updateState(status.id, notify = false) {
            newResponse.copy(downloadState = status.response.downloadState)
        }
        applyPackage(id = status.id, bookPackage = bookPackage)
        downloadFromLoad(response = newResponse, handle = status.handle)
    }

    /**
     * Applies the package to the downloader, aka adds what chapter should be downloaded
     *
     * This is safe to do even outside the download context
     * */
    private fun applyPackage(
        id: Int,
        bookPackage: BookDownloadPackage,
        notify: Boolean = false,
    ) {
        when (bookPackage) {
            is BookDownloadPackage.Chapters -> {
                updateDownloadState(id, notify = notify) {
                    copy(pendingChapters = pendingChapters.copy().apply {
                        or(bookPackage.set)
                        andNot(downloadedChapters)
                    })
                }
            }

            is BookDownloadPackage.AfterOffset -> {
                setKey(DOWNLOAD_OFFSET, id.toString(), bookPackage.offset)
                updateState(id, notify = notify) {
                    copy(
                        downloadState = downloadState?.copy(
                            downloadOffset = bookPackage.offset,
                            pendingChapters = downloadState.pendingChapters.copy().apply {
                                set(
                                    downloadState.downloadOffset,
                                    chapters ?: downloadState.downloadOffset
                                )
                                andNot(downloadState.downloadedChapters)
                            })
                    )
                }
            }

            else -> {}
        }
    }

    @Throws
    private suspend fun downloadFromLoad(
        response: ImmutableSearchResponse,
        handle: BookHandle,
    ) {
        require(response.id != null)
        require(response.loadData != null)

        updatePersistentData(
            response = response,
            id = response.id,
            timeOfCached = handle.created,
            timeOfChapterDownloaded = null,
        )

        if (response.loadData.chapters != null) {
            setKey(DOWNLOAD_TOTAL, handle.id.toString(), response.loadData.chapters.size)
            updateState(response.id, notify = false) {
                copy(
                    downloadState = downloadState?.copy(downloadStatus = BookDownloadStatus.Downloading),
                    visualTotalChapters = response.loadData.chapters.size
                )
            }
            downloadPoster(response = response, handle = handle)
            if (waitForAction(handle)) {
                return
            }

            var averageDuration = Duration.ZERO
            while (currentStatusMap[response.id]?.response?.downloadState?.downloadStatus == BookDownloadStatus.Downloading) {
                val progress = downloadNextChapter(response = response, handle = handle) ?: return

                /**
                 * Eta = time to download 1 chapter * chapters we want to download,
                 * weighted to average it over a few chapters to make it smoother
                 * */
                averageDuration = if (averageDuration > 100.milliseconds) {
                    progress.writeDuration * 0.2 + averageDuration * 0.8
                } else {
                    progress.writeDuration
                }

                updateDownloadState(response.id) {
                    copy(
                        estimateDownloadTime =
                            if (pendingChapters.isEmpty || downloadStatus != BookDownloadStatus.Downloading || !averageDuration.isPositive()) {
                                null
                            } else {
                                (averageDuration * pendingChapters.cardinality())
                            }
                    )
                }
            }
        } else if (response.loadData.downloadLinks != null || response.loadData.downloadExtractLinks != null) {
            downloadPoster(response = response, handle = handle)
            if (waitForAction(handle)) {
                return
            }
            downloadEpubFromLoad(loadData = response.loadData, handle = handle)
        }
    }

    @Throws
    private suspend fun importObject(
        context: Context,
        uri: Uri,
        status: BookStatus,
    ) {
        val file = StorageFile.from(context, uri) ?: throw FileNotFoundException()
        val mimeType = file.mimeType

        setKey(DOWNLOAD_TOTAL, status.id.toString(), 1)
        if (mimeType == "application/pdf" || file.name.endsWith(".pdf")) {
            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context)
            }
            importPdf(file = file, status = status, context = context)
        } else {
            importEpub(file = file, response = status.response, handle = status.handle)
        }
    }

    private suspend fun importEpub(
        file: StorageFile,
        response: ImmutableSearchResponse,
        handle: BookHandle,
    ) {
        val totalBytes = file.length
        val readStream = file.openInputStream() ?: throw IOException("Unable to open file")
        val targetFile = handle.files.localEpubFile()

        updateDownloadState(handle.id) {
            copy(
                downloadedBytes = 0,
                totalBytes = totalBytes,
                downloadStatus = BookDownloadStatus.Downloading,
            )
        }

        val stoppedByUser = transferTo(
            target = targetFile,
            readStream = readStream,
            handle = handle,
            progress = { transfer ->
                /** Eta = average time as it should be consistent in the entire transfer */
                val bytesRemaining = totalBytes - transfer.totalBytesWritten
                val averageDurationPerByte = transfer.totalDurationPerByte
                val estimateDownloadTime =
                    if (averageDurationPerByte.isPositive()) {
                        averageDurationPerByte * bytesRemaining.toDouble()
                    } else {
                        null
                    }

                updateDownloadState(handle.id) {
                    copy(
                        estimateDownloadTime = estimateDownloadTime,
                        downloadedBytes = transfer.totalBytesWritten,
                        totalBytes = totalBytes
                    )
                }
            })

        if (stoppedByUser) {
            return
        }

        try {
            val lazyEpub = EpubReader().readEpubLazy(AndroidZipFile(targetFile), "utf-8")
            val cover = lazyEpub.coverImage
            val posterFile = handle.files.posterFile()
            if (transferTo(
                    posterFile,
                    readStream = cover.inputStream,
                    handle = handle
                )
            ) {
                return
            }
        } catch (t: Throwable) {
            logError(t)
        }

        setDone(response = response, handle = handle)
    }

    private suspend fun downloadEpubFromLoad(loadData: ImmutableLoadData, handle: BookHandle) {
        var links = ExtractorApi.extract(
            (loadData.downloadLinks ?: emptyList()) + (loadData.downloadLinks ?: emptyList())
        )
        links = links.sortedByDescending { it.kbPerSec }
        var lastError: Throwable? = null
        for (link in links) {
            updateDownloadState(handle.id) {
                copy(
                    totalBytes = 0,
                    downloadedBytes = 0,
                    downloadStatus = BookDownloadStatus.Pending
                )
            }
            if (resetApiLastRequestTime(handle = handle)) {
                return
            }
            val stream = try {
                link.get().body
            } catch (t: Throwable) {
                if (waitForAction(handle = handle, timeout = 1000.milliseconds)) {
                    return
                }
                lastError = t
                continue
            }

            stream.use { stream ->
                val totalBytes = stream.contentLength()
                if (totalBytes <= LOCAL_EPUB_MIN_SIZE) {
                    lastError = ErrorLoadingException("Content length of $totalBytes is too small")
                    if (waitForAction(handle = handle, timeout = 1000.milliseconds)) {
                        return
                    }
                    continue
                }

                updateDownloadState(handle.id) {
                    copy(
                        totalBytes = totalBytes,
                        downloadStatus = BookDownloadStatus.Downloading
                    )
                }

                val target = handle.files.localEpubFile()
                if (transferTo(
                        target,
                        stream.byteStream(),
                        handle = handle,
                        progress = { transfer ->
                            val averageDurationPerByte = transfer.totalDurationPerByte

                            val bytesRemaining = totalBytes - transfer.totalBytesWritten
                            val estimateDownloadTime =
                                if (averageDurationPerByte.isPositive()) {
                                    averageDurationPerByte * bytesRemaining.toDouble()
                                } else {
                                    null
                                }

                            updateDownloadState(handle.id) {
                                copy(
                                    downloadedBytes = transfer.totalBytesWritten,
                                    estimateDownloadTime = estimateDownloadTime
                                )
                            }
                        })
                ) {
                    return
                }
            }
        }

        lastError?.let(::logError)
        BookState.setError(handle.id, lastError)
    }

    @Throws
    private suspend fun downloadPoster(response: ImmutableSearchResponse, handle: BookHandle) {
        if (response.posterUrl.isNullOrBlank()) return
        val posterFile = handle.files.posterFile()
        val key = handle.id.toString()

        /** Only redownload if we do not have a file, or if the poster was changed */
        if (posterFile.exists() && getKey<String>(
                DOWNLOAD_POSTER,
                key,
                response.posterUrl
            ) == response.posterUrl
        ) {
            return
        }

        // Clear the key so if we have a partial download it fixes it
        setKey(
            DOWNLOAD_POSTER,
            key,
            ""
        )

        if (resetApiLastRequestTime(handle)) {
            return
        }

        val getResponse = app.get(
            url = response.posterUrl,
            headers = response.posterHeaders ?: emptyMap()
        )

        getResponse.body.use { body ->
            /** Early exit in case we already have a same file length file */
            if (body.contentLength() == posterFile.length()) {
                return
            }
            if (transferTo(
                    target = posterFile,
                    readStream = body.byteStream(),
                    handle = handle
                )
            ) {
                return
            }
        }

        // This acts as a checkpoint
        setKey(
            DOWNLOAD_POSTER,
            key,
            response.posterUrl
        )
    }

    @Throws
    private suspend fun downloadNextChapter(
        response: ImmutableSearchResponse,
        handle: BookHandle,
    ): ChapterProgress? {
        val files = handle.files
        val current =
            currentStatusMap[handle.id]
                ?: throw IllegalStateException("No current download state exists")
        val total = current.response.loadData?.chapters?.size ?: return null
        val pendingChapters = current.response.downloadState?.pendingChapters ?: return null
        val chapterIndex: Int = pendingChapters.nextSetBit(0)
        if (chapterIndex == -1) {
            setDone(response = response, handle = handle)
            return null
        }

        if (waitForAction(handle = handle, Duration.ZERO)) {
            return null
        }

        updateDownloadState(handle.id) {
            copy(
                chapterProgress = ImmutableChapterProgress(progress = chapterIndex, total = total),
                downloadStatus = BookDownloadStatus.Downloading
            )
        }

        var lastError: Throwable? = null
        val retries = 5
        var writeDuration = Duration.ZERO

        for (retry in 1..retries) {
            try {
                if (waitForAction(handle = handle, Duration.ZERO)) {
                    return null
                }
                val startTime = monotonicNow()
                val success = if (chapterExists(files = files, chapterIndex = chapterIndex)) {
                    true
                } else {
                    /** Only request the api if not downloaded */
                    if (resetApiLastRequestTime(handle = handle)) {
                        return null
                    }
                    try {
                        downloadChapter(
                            files = files,
                            response = response,
                            chapterIndex = chapterIndex,
                        )
                    } catch (t: Throwable) {
                        writeDuration += (monotonicNow() - startTime)
                        throw t
                    }
                }

                if (!success) {
                    /** We failed without any possiblity of retry */
                    break
                }

                /**
                 * Download time for this chapter = time to do all requests + penalty for failure
                 * */
                val downloadTime = writeDuration + (monotonicNow() - startTime)
                val time = System.currentTimeMillis()
                updatePersistentData(
                    response = response,
                    id = handle.id,
                    timeOfChapterDownloaded = time,
                    timeOfCached = handle.created
                )
                updateState(files.id) {
                    if (downloadState != null) {
                        copy(
                            timeOfChapterDownloaded = time,
                            timeOfCached = handle.created,
                            downloadState = downloadState.copy(
                                error = null,
                                downloadedChapters = downloadState.downloadedChapters.copy()
                                    .apply { set(chapterIndex) },
                                pendingChapters = downloadState.pendingChapters.copy()
                                    .apply { clear(chapterIndex) }
                            )
                        )
                    } else {
                        this
                    }
                }
                return ChapterProgress(downloadTime)
            } catch (e: IllegalStateException) {
                /**
                 * Fail directly if we have an IllegalStateException,
                 * as some info is missing, so retry will not get you anything
                 * */
                lastError = e
                break
            } catch (_: CancellationException) {
                return null
            } catch (t: Throwable) {
                lastError = t
            }

            val artificialRetryDelay = if (retry == retries) {
                Duration.ZERO
            } else {
                1000L.milliseconds
            }

            writeDuration += artificialRetryDelay

            // Put suspend point here to wait for action + delay at the same time
            if (waitForAction(handle = handle, timeout = artificialRetryDelay)) {
                return null
            }
        }

        lastError?.let(::logError)
        BookState.setError(handle.id, lastError)
        return null
    }

    @Throws
    private fun chapterExists(
        files: BookFileHandle,
        chapterIndex: Int,
    ): Boolean {
        val file = files.chapterFile(chapterIndex)
        return file.exists() && file.length() > 0
    }

    @Throws
    private suspend fun downloadChapter(
        files: BookFileHandle,
        response: ImmutableSearchResponse,
        chapterIndex: Int,
    ): Boolean {
        val api = files.getApi()
        val load =
            response.loadData ?: throw IllegalStateException("Require non-null loaded data")
        val chapters = load.chapters ?: throw IllegalStateException("Require non-null chapters")
        val chapter = chapters.getOrNull(chapterIndex)
            ?: throw IllegalStateException("Require non-null chapters")

        return downloadChapter(
            files = files,
            api = api,
            chapterUrl = chapter.url,
            chapterIndex = chapterIndex,
            chapterName = chapter.name
        )
    }

    private val chapterWriteMutex = Mutex()

    @Throws
    suspend fun downloadChapter(
        files: BookFileHandle,
        api: APIRepository,
        chapterUrl: String,
        chapterIndex: Int,
        chapterName: String,
    ): Boolean {
        val html = api.loadHtml(chapterUrl)
        if (html.isNullOrBlank()) {
            return false
        }
        val file = files.chapterFile(chapterIndex)

        /**
         * File io is fast, so we can just have 1 mutex tbh,
         * this fixes some *minor* issues where we e.g. stream
         * */
        chapterWriteMutex.withLock {
            file.writeText("${chapterName}\n${html}")
        }
        return true
    }
}