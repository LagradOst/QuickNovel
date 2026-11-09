package com.lagradost.quicknovel.downloader

import android.content.Context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2.bookmarkChanged
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.DOWNLOAD_FOLDER
import com.lagradost.quicknovel.DOWNLOAD_OFFSET
import com.lagradost.quicknovel.DOWNLOAD_TOTAL
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.downloader.BookFiles.updatePersistentData
import com.lagradost.quicknovel.mvvm.launchSafe
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.ui.common.ImmutableDownloadState
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Event
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * State of each download in the app, with the aim of keeping each
 * operation atomic due to the multithreaded purpose of a downloader
 * */
object BookState {
    fun downloadStatus(id: Int): ImmutableSearchResponse? = currentStatusMap[id]?.response
    val currentStatusMap = ConcurrentHashMap<Int, BookStatus>()
    val onDataRefreshed = Event<Unit>()
    val onStatusChanged = Event<BookStateChanged>()
    val onRemoved = Event<Int>()
    val setupMutex = Mutex()

    fun postRemoved(id: Int) {
        safe {
            currentStatusMap.remove(id)
            onRemoved(id)
        }
        safe {
            BookNotifications.postNotification(
                response = null,
                id = id
            )
        }
    }

    /** Unique refresh reading check to allow many jobs to spawn the same */
    private val updateReadingProgress = ConcurrentHashMap<Int, Unit>()
    suspend fun updateReadingProgress(response: ImmutableSearchResponse, page : Int, context: Context)  {
        if(response.id == null) {
            return
        }
        val api = Apis.getApiFromNameOrNull(response.apiName) ?: return
        if(updateReadingProgress.putIfAbsent(response.id, Unit) != null) {
            return
        }
        try {
            BookDownloader2.refreshingChanged(BookDownloader2.RefreshQuery(id = response.id, refreshing = true, page = page))
            BookFiles.resetApiLastRequestTime(api)
            api.loadResult(url = response.url).onFailure { error ->
                logError(error)
            }.onSuccess { newResponse ->
                val cached = getKey<ResultCached>(RESULT_BOOKMARK, response.id.toString()) ?: return
                val newCache = newResponse.toResultCached(response.id).copy(cachedTime = cached.cachedTime)
                setKey(
                    RESULT_BOOKMARK,
                    response.id.toString(),
                    newCache
                )
                bookmarkChanged(cached.id)

                // Also migrate if possible
                if (response.id != newResponse.id) {
                    BookDownloader.migrateNonDownloadedInformation(
                        context = context,
                        from = response,
                        to = newResponse
                    )
                }
            }
        } finally {
            updateReadingProgress.remove(response.id)
            BookDownloader2.refreshingChanged(BookDownloader2.RefreshQuery(id = response.id, refreshing = false, page = page))
        }
    }

    /** Automatically */
    @OptIn(ExperimentalContracts::class)
    fun updateState(
        id: Int,
        notify: Boolean = true,
        updater: ImmutableSearchResponse.() -> ImmutableSearchResponse
    ) {
        contract {
            callsInPlace(lambda = updater, kind = InvocationKind.UNKNOWN)
        }
        while (true) {
            val current = currentStatusMap[id] ?: return
            val newResponse = updater(current.response)
            val newStatus = current.copy(response = newResponse)
            if (currentStatusMap.replace(id, current, newStatus)) {
                //println("New state ($id) = ${newStatus.response.downloadState?.pendingChapters}")
                postStateUpdate(response = newResponse, id = id, notify = notify)
                break
            }
        }
    }

    @OptIn(ExperimentalContracts::class)
    fun updateDownloadState(
        id: Int,
        notify: Boolean = true,
        updater: ImmutableDownloadState.() -> ImmutableDownloadState
    ) {
        contract {
            callsInPlace(lambda = updater, kind = InvocationKind.UNKNOWN)
        }
        updateState(id, notify) {
            copy(downloadState = downloadState?.let(updater))
        }
    }

    fun postStateUpdate(response: ImmutableSearchResponse, id: Int, notify: Boolean) {
        if (notify) {
            safe {
                BookNotifications.postNotification(response = response, id = id)
            }
        }
        safe {
            onStatusChanged.invoke(BookStateChanged(id = id, response = response))
        }
    }


    fun setStop(id: Int) {
        updateDownloadState(id) {
            copy(
                downloadStatus = BookDownloadStatus.Stopped,
                pendingChapters = BitSet(),
                chapterProgress = null,
                estimateDownloadTime = null,
                error = null,
            )
        }
    }

    fun setError(id: Int, error: Throwable?) {
        updateDownloadState(id) {
            copy(
                downloadStatus = BookDownloadStatus.Failed,
                error = error,
                pendingChapters = BitSet(),
                chapterProgress = null,
                estimateDownloadTime = null
            )
        }
    }

    fun setDone(response: ImmutableSearchResponse, handle: BookHandle) {
        val creationTime = System.currentTimeMillis()
        val timeOfChapterDownloaded = (getKey<DownloadFragment.DownloadData>(
            DOWNLOAD_FOLDER,
            handle.id.toString()
        )?.lastDownloaded)
            ?: System.currentTimeMillis()

        updatePersistentData(
            response = response,
            id = handle.id,
            timeOfChapterDownloaded = timeOfChapterDownloaded,
            timeOfCached = creationTime
        )

        val cancelNotification = timeOfChapterDownloaded < handle.created

        updateState(handle.id, notify = !cancelNotification) {
            copy(
                timeOfChapterDownloaded = timeOfChapterDownloaded,
                timeOfCached = creationTime,
                downloadState = downloadState?.copy(
                    error = null,
                    chapterProgress = null,
                    estimateDownloadTime = null,
                    pendingChapters = BitSet(),
                    downloadStatus = BookDownloadStatus.Done
                )
            )
        }

        /** If we did nothing then clear it, as it is not relevant */
        if (cancelNotification) {
            BookNotifications.postNotification(response = null, id = handle.id)
        }
    }

    suspend fun getDownloads(files: BookFileHandle): BitSet = withContext(Dispatchers.IO) {
        val out = BitSet()
        val epubFile = files.localEpubFile()
        if (epubFile.exists()) {
            out.set(0)
            return@withContext out
        }
        val directoryFiles = files.directoryFile().listFiles() ?: return@withContext out
        for (file in directoryFiles) {
            val index = file.nameWithoutExtension.toIntOrNull() ?: continue
            out.set(index)
        }
        return@withContext out
    }

    fun setup(context: Context) = CoroutineScope(Dispatchers.Default).launchSafe {
        // 1. 1000ms startup time from reading the *first* key <- this cant be optimized without better db
        // 2. 100ms to get all keys in DOWNLOAD_FOLDER
        setupMutex.withLock {
            val keys = getKeys(DOWNLOAD_FOLDER) ?: return@launchSafe
            keys.forEach { key ->
                val res =
                    getKey<DownloadFragment.DownloadData>(key) ?: return@forEach
                val localId = BookDownloader2Helper.generateId(res.apiName, res.author, res.name)
                val response = ImmutableSearchResponse.from(localId, res)
                val files = BookFileHandle(
                    apiName = response.apiName,
                    author = response.author,
                    name = response.name,
                    fileDir = context.filesDir,
                    cacheDir = context.cacheDir
                )
                val downloads = getDownloads(files = files)
                if (downloads.isEmpty) {
                    removeKey(key)
                    return@forEach
                }
                val downloadOffset = getKey<Int>(DOWNLOAD_OFFSET, localId.toString()) ?: 0
                val downloadTotal = getKey<Int>(DOWNLOAD_TOTAL, localId.toString()) ?: 0
                currentStatusMap.putIfAbsent(
                    localId,
                    BookStatus(
                        handle = BookHandle(
                            notification = Channel(
                                capacity = 1,
                                onBufferOverflow = BufferOverflow.DROP_OLDEST
                            ),
                            files = files,
                            mutex = Mutex(),
                            id = localId
                        ),
                        response = response.copy(
                            visualTotalChapters = downloadTotal,
                            downloadState = ImmutableDownloadState(
                                chapterProgress = null,
                                downloadedChapters = downloads,
                                pendingChapters = BitSet(),
                                downloadedBytes = 0L,
                                totalBytes = 0L,
                                downloadStatus = BookDownloadStatus.Idle,
                                error = null,
                                downloadOffset = downloadOffset
                            )
                        ),
                    )
                )
            }
            onDataRefreshed.invoke(Unit)
        }
    }
}