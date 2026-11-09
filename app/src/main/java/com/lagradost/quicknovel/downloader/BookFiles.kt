package com.lagradost.quicknovel.downloader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.CheckResult
import androidx.compose.runtime.Immutable
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2.LOCAL_EPUB
import com.lagradost.quicknovel.BookDownloader2Helper.decodeSafeBitmap
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DOWNLOAD_EPUB_SIZE
import com.lagradost.quicknovel.DOWNLOAD_FOLDER
import com.lagradost.quicknovel.DOWNLOAD_OFFSET
import com.lagradost.quicknovel.DOWNLOAD_TOTAL
import com.lagradost.quicknovel.EPUB_BITSET_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_CHAPTER
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_SCROLL
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION_SCROLL_CHAR
import com.lagradost.quicknovel.EPUB_CURRENT_VISUAL_POSITION
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.downloader.BookState.currentStatusMap
import com.lagradost.quicknovel.downloader.BookState.updateDownloadState
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class WriteProgress(
    val totalBytesWritten: Long,
    val bytesWritten: Long,
    val totalWriteDuration: Duration,
    val writeDuration: Duration,
) {
    val durationPerByte
        get() =
            if (bytesWritten <= 0) {
                Duration.ZERO
            } else {
                (writeDuration * (1.0 / bytesWritten.toDouble()))
            }
    val totalDurationPerByte
        get() =
            if (totalBytesWritten <= 0) {
                Duration.ZERO
            } else {
                (totalWriteDuration * (1.0 / totalBytesWritten.toDouble()))
            }
}

@Immutable
data class ChapterProgress(
    val writeDuration: Duration,
)

data class BookFileHandle(
    val apiName: String,
    private val author: String?,
    private val name: String,
    private val fileDir: File,
    private val cacheDir: File,
) {
    @Throws
    fun getApi() =
        getApiOrNull() ?: throw IllegalStateException("API $apiName not found")

    @Throws
    fun getApiOrNull() =
        Apis.getApiFromNameOrNull(apiName)

    private val sanitizedApiName: String = sanitizeFilename(apiName)
    private val sanitizedAuthor: String = if (author == null) "" else sanitizeFilename(author)
    private val sanitizedName: String = sanitizeFilename(name)
    val id: Int = "$sanitizedApiName$sanitizedAuthor$sanitizedName".hashCode()

    private fun directory(): String {
        return "$fs$sanitizedApiName$fs$sanitizedAuthor$fs$sanitizedName".replace("$fs$fs", "$fs")
    }

    fun filenameImage(): String = "poster.jpg"

    fun tmpFile(suffix: String): File = File.createTempFile("tmp", suffix, cacheDir)
    fun directoryFile(): File = File(fileDir, directory())
    fun localEpubFile(): File = File(directoryFile(), LOCAL_EPUB)
    fun chapterFile(index: Int): File = File(directoryFile(), "$index.txt")
    fun posterFile() = File(directoryFile(), filenameImage())
    fun pdfImage(page: Int, index: Int) = File(directoryFile(), "pdf_img_${page}_${index}.jpg")

    /** [start, end) */
    fun pdfChapter(start: Int, end: Int) = File(directoryFile(), "pdf_chapter_${start}_${end}.txt")

    companion object {
        private val fs = File.separatorChar
        private const val RESERVED_CHARS = "|\\?*<\":>+[]/'"
        fun sanitizeFilename(name: String): String {
            var tempName = name
            for (c in RESERVED_CHARS) {
                tempName = tempName.replace(c, ' ')
            }
            return tempName.replace(Regex("\\s+"), " ").trim()
        }
    }
}

object BookFiles {
    /**
     * This prevents deleting while generating the epub, as that will cause issues otherwise.
     *
     * Note that downloading and deleting can not be done at the same time, so it is not necessary
     * to lock for "read" while we are inside a download context.
     *
     * We really want ReentrantReadWriteLock here, but ReentrantReadWriteLock throws an error due to
     * suspended functions in kotlin. Kotlin does not have a better option than a mutex :(
     * */
    val readDeleteFileLock = Mutex()
    private val lastUpdatedMainAPI = ConcurrentHashMap<String, Long>()
    fun updatePersistentData(
        response: ImmutableSearchResponse,
        id: Int,
        timeOfChapterDownloaded: Long?,
        timeOfCached: Long
    ) =
        setKey(
            DOWNLOAD_FOLDER, id.toString(),
            DownloadFragment.DownloadData(
                source = response.url,
                name = response.name,
                author = response.author,
                posterUrl = response.posterUrl,
                rating = response.rating,
                peopleVoted = response.loadData?.peopleVoted,
                views = response.loadData?.views,
                synopsis = response.synopsis,
                tags = response.tags,
                apiName = response.apiName,
                lastUpdated = timeOfCached,
                lastDownloaded = timeOfChapterDownloaded ?: (getKey<DownloadFragment.DownloadData>(
                    DOWNLOAD_FOLDER,
                    id.toString()
                )?.lastDownloaded)
                ?: System.currentTimeMillis(),
                posterHeaders = response.posterHeaders
            )
        )

    /**
     * Acquires a timeout from the given api, this is used instead of mutex as we want to handle
     * instant pause/resume
     *
     * Returns true if we should stop
     * */
    @Throws
    @CheckResult
    suspend fun resetApiLastRequestTime(handle: BookHandle): Boolean {
        val api = handle.files.getApiOrNull() ?: return false
        return resetApiLastRequestTime(api = api, handle = handle)
    }

    @Throws
    suspend fun resetApiLastRequestTime(api: APIRepository, handle: BookHandle? = null): Boolean {
        val addedTime = api.rateLimitTime
        if (addedTime <= 0L) return false
        val key = api.name
        lastUpdatedMainAPI.putIfAbsent(key, 0L)
        while (true) {
            val current = lastUpdatedMainAPI[key] ?: 0L
            val remain = current - System.currentTimeMillis()
            if (remain > 0L) {
                if (handle != null) {
                    if (waitForAction(handle = handle, timeout = remain.milliseconds)) {
                        return true
                    }
                } else {
                    delay(remain.milliseconds)
                }
            }
            /** Pause resume -> this is early */
            if (System.currentTimeMillis() < current) {
                continue
            }
            val newTime = System.currentTimeMillis() + addedTime
            if (lastUpdatedMainAPI.replace(key, current, newTime)) {
                return false
            }
        }
    }

    /** Returns true if we have stopped */
    @CheckResult
    suspend fun waitForAction(
        handle: BookHandle,
        timeout: Duration = Duration.ZERO
    ): Boolean {
        val id = handle.id
        val currentStatus = currentStatusMap[id]?.response?.downloadState?.downloadStatus
        if (currentStatus == null || currentStatus == BookDownloadStatus.Stopped) {
            return true
        }
        if (currentStatus == BookDownloadStatus.Paused) {
            return waitForUnpause(handle)
        }

        val action =
            if (timeout.isPositive()) {
                withTimeoutOrNull(timeout) {
                    handle.notification.receive()
                }
            } else {
                handle.notification.tryReceive().getOrNull()
            }

        when (action) {
            BookAction.Stop -> {
                BookState.setStop(id)
                return true
            }

            BookAction.Resume -> {
                updateDownloadState(id) {
                    copy(downloadStatus = BookDownloadStatus.Downloading)
                }
            }

            BookAction.Pause -> {
                updateDownloadState(id) {
                    copy(downloadStatus = BookDownloadStatus.Paused)
                }
                return waitForUnpause(handle)
            }

            null -> {

            }
        }
        return false
    }

    /** Returns true if we have stopped */
    @Throws
    @CheckResult
    suspend fun transferTo(
        target: File,
        readStream: InputStream,
        handle: BookHandle,
        progress: ((WriteProgress) -> Unit)? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val tmpFile = handle.files.tmpFile(".tmp")

            var totalWriteDuration = Duration.ZERO
            tmpFile.outputStream().use { writeStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytesWritten = 0L
                var bytesWritten: Int
                progress?.invoke(
                    WriteProgress(
                        totalBytesWritten = 0L,
                        bytesWritten = 0L,
                        totalWriteDuration = Duration.ZERO,
                        writeDuration = Duration.ZERO
                    )
                )
                while (true) {
                    val startTime = monotonicNow()
                    if ((readStream.read(buffer, 0, DEFAULT_BUFFER_SIZE)
                            .also { bytesWritten = it }) < 0
                    ) {
                        break
                    }
                    writeStream.write(buffer, 0, bytesWritten)

                    if (progress != null) {
                        val writeDuration = monotonicNow() - startTime
                        totalWriteDuration += writeDuration
                        totalBytesWritten += bytesWritten
                        progress.invoke(
                            WriteProgress(
                                totalBytesWritten = totalBytesWritten,
                                bytesWritten = bytesWritten.toLong(),
                                totalWriteDuration = totalWriteDuration,
                                writeDuration = writeDuration
                            )
                        )
                    }

                    // Pause will probably fuck up the read, but it is better than not responding to it
                    if (waitForAction(handle = handle)) {
                        return@withContext true
                    }
                }
                writeStream.flush()
            }

            target.parentFile?.mkdirs()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Files.move(
                        tmpFile.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        tmpFile.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            } else {
                target.delete()
                if (!tmpFile.renameTo(target)) {
                    throw IOException("Invalid destination")
                }
            }
            return@withContext false
        }


    @CheckResult
    private suspend fun waitForUnpause(handle: BookHandle): Boolean {
        /** Wait for valid */
        while (true) {
            val action = handle.notification.receive()
            when (action) {
                BookAction.Stop -> {
                    BookState.setStop(handle.id)
                    return true
                }

                BookAction.Resume -> {
                    updateDownloadState(handle.id) {
                        copy(downloadStatus = BookDownloadStatus.Downloading)
                    }
                    break
                }

                else -> {}
            }
        }
        return false
    }

    /**
     * Checks if a ByteArray image is too large and downsamples it if necessary.
     * This prevents saving massive bitmaps that crash the Canvas later.
     */
    fun getSafeByteArray(data: ByteArray, maxRes: Int = 1200): ByteArray {
        // If the image is already small, don't waste CPU
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        if (options.outWidth <= maxRes && options.outHeight <= maxRes) {
            return data
        }
        // Process and re-compress
        val bitmap = decodeSafeBitmap(data = data, maxRes = maxRes, config = Bitmap.Config.RGB_565)
            ?: return data

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val result = outputStream.toByteArray()
        bitmap.recycle()
        return result
    }

    fun removeDownloadedFiles(status: BookStatus) {
        val fromDir = status.handle.files.directoryFile()
        fromDir.deleteRecursively()
        val from = status.id.toString()
        removeKey(DOWNLOAD_FOLDER, from)
        removeKey(DOWNLOAD_OFFSET, from)
    }

    fun migrateAllInformation(
        fromStatus: BookStatus,
        toStatus: BookStatus,
    ) {
        val fromDir = fromStatus.handle.files.directoryFile()
        val toDir = toStatus.handle.files.directoryFile()
        toDir.mkdirs()
        try {
            fromDir.copyRecursively(toDir, overwrite = false)
        } catch (_: NoSuchFileException) {
            /** Ignore only this error, as it is useless. */
        }

        val to = toStatus.id.toString()
        val from = fromStatus.id.toString()
        setKey(
            DOWNLOAD_TOTAL, to,
            getKey<Int>(DOWNLOAD_TOTAL, from)
        )
        setKey(
            DOWNLOAD_FOLDER, to,
            getKey<DownloadFragment.DownloadData>(DOWNLOAD_FOLDER, from)
        )
        removeKey(DOWNLOAD_FOLDER, from)

        setKey(
            DOWNLOAD_EPUB_SIZE, to,
            getKey<Int>(DOWNLOAD_EPUB_SIZE, from)
        )

        setKey(
            DOWNLOAD_OFFSET, to,
            getKey<Int>(DOWNLOAD_OFFSET, from)
        )

        /**
         * Migrate the bookmark/history without modifying the cache time, as migration should
         * only refresh the information, not remove it
         * */
        val newCache = toStatus.response.toResultCached(toStatus.id)
        val oldHistory = getKey<ResultCached>(HISTORY_FOLDER, from)
        setKey(
            HISTORY_FOLDER, to,
            newCache.copy(cachedTime = oldHistory?.cachedTime ?: newCache.cachedTime)
        )
        removeKey(HISTORY_FOLDER, from)
        val oldBookmark = getKey<ResultCached>(RESULT_BOOKMARK, from)
        setKey(
            RESULT_BOOKMARK, to,
            newCache.copy(cachedTime = oldBookmark?.cachedTime ?: newCache.cachedTime)
        )
        removeKey(RESULT_BOOKMARK, from)

        setKey(
            RESULT_BOOKMARK_STATE, to,
            getKey<Int>(RESULT_BOOKMARK_STATE, from)
        )
        removeKey(RESULT_BOOKMARK_STATE, from)

        setKey(
            DOWNLOAD_EPUB_LAST_ACCESS, to,
            getKey<Long>(DOWNLOAD_EPUB_LAST_ACCESS, from)
        )

        setKey(
            EPUB_CURRENT_POSITION, toStatus.response.name,
            getKey<Int>(EPUB_CURRENT_POSITION, fromStatus.response.name)
        )

        setKey(
            EPUB_CURRENT_POSITION_CHAPTER, toStatus.response.name,
            getKey<String>(EPUB_CURRENT_POSITION_CHAPTER, fromStatus.response.name)
        )

        setKey(
            EPUB_CURRENT_VISUAL_POSITION, to,
            getKey<Int>(EPUB_CURRENT_VISUAL_POSITION, from)
        )
        setKey(
            EPUB_BITSET_POSITION, to,
            getKey<ByteArray>(EPUB_BITSET_POSITION, from)
        )

        setKey(
            EPUB_CURRENT_POSITION_SCROLL, toStatus.response.name,
            getKey<Int>(EPUB_CURRENT_POSITION_SCROLL, fromStatus.response.name)
        )
        setKey(
            EPUB_CURRENT_POSITION_SCROLL_CHAR, toStatus.response.name,
            getKey<Int>(EPUB_CURRENT_POSITION_SCROLL_CHAR, fromStatus.response.name)
        )
        safe {
            removeDownloadedFiles(fromStatus)
        }

        safe {
            BookDownloader2.bookmarkChanged(fromStatus.id)
            BookDownloader2.bookmarkChanged(toStatus.id)
        }
    }
}