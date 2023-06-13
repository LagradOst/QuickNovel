package com.lagradost.quicknovel

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader.checkWrite
import com.lagradost.quicknovel.BookDownloader.createQuickStream
import com.lagradost.quicknovel.BookDownloader.getFileLength
import com.lagradost.quicknovel.BookDownloader.requestRW
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.DataStore.removeKey
import com.lagradost.quicknovel.ImageDownloader.getImageBitmapFromUrl
import com.lagradost.quicknovel.NotificationHelper.etaToString
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.services.DownloadService
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.Event
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.pmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.siegmann.epublib.domain.Author
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.MediaType
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubWriter
import nl.siegmann.epublib.service.MediatypeService
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


object BookDownloader2Helper {
    data class DownloadProgress(
        val progress: Int,
        val total: Int,
    )

    data class DownloadProgressState(
        var state: DownloadState,
        var progress: Int,
        var total: Int,
        var lastUpdatedMs: Long,
        var etaMs: Long?
    ) {
        fun eta(): String {
            // TODO STRINGS
            return when (state) {
                DownloadState.IsDownloading -> etaToString(etaMs)
                DownloadState.IsDone -> ""
                DownloadState.IsFailed -> "Failed"
                DownloadState.IsStopped -> "Stopped"
                DownloadState.Nothing -> ""
                DownloadState.IsPending -> ""
                DownloadState.IsPaused -> "Paused"
            }
        }
    }

    enum class DownloadState {
        IsPaused,
        IsDownloading,
        IsDone,
        IsFailed,
        IsStopped,
        IsPending,
        Nothing,
    }

    private val fs = File.separatorChar
    private const val reservedChars = "|\\?*<\":>+[]/'"
    fun sanitizeFilename(name: String): String {
        var tempName = name
        for (c in reservedChars) {
            tempName = tempName.replace(c, ' ')
        }
        return tempName.replace("  ", " ")
    }

    private fun getDirectory(apiName: String, author: String, name: String): String {
        return "$fs$apiName$fs$author$fs$name".replace("$fs$fs", "$fs")
    }

    fun getFilename(apiName: String, author: String, name: String, index: Int): String {
        return "${getDirectory(apiName, author, name)}$fs$index.txt".replace("$fs$fs", "$fs")
    }

    fun getFilenameIMG(apiName: String, author: String, name: String): String {
        return "$fs$apiName$fs$author$fs$name${fs}poster.jpg".replace("$fs$fs", "$fs")
    }

    fun generateId(apiName: String, author: String?, name: String): Int {
        val sApiname = sanitizeFilename(apiName)
        val sAuthor = if (author == null) "" else sanitizeFilename(author)
        val sName = sanitizeFilename(name)
        return "$sApiname$sAuthor$sName".hashCode()
    }

    fun generateId(load: LoadResponse, apiName: String): Int {
        return generateId(apiName, load.author, load.name)
    }

    fun hasEpub(activity: Activity?, name: String): Boolean {
        if (activity == null) return false

        if (!activity.checkWrite()) {
            activity.requestRW()
            return false
        }

        val relativePath = (Environment.DIRECTORY_DOWNLOADS + "${fs}Epub${fs}")
        val displayName = "${sanitizeFilename(name)}.epub"

        if (isScopedStorage()) {
            val cr = activity.contentResolver ?: return false
            val fileUri =
                cr.getExistingDownloadUriOrNullQ(relativePath, displayName) ?: return false
            val fileLength = cr.getFileLength(fileUri) ?: return false
            if (fileLength == 0L) return false
            return true
        } else {
            val normalPath =
                "${Environment.getExternalStorageDirectory()}${fs}$relativePath$displayName"

            val bookFile = File(normalPath)
            return bookFile.exists()
        }
    }

    fun deleteNovel(activity: Activity?, author: String?, name: String, apiName: String) {
        if (activity == null) return
        try {
            val sApiName = sanitizeFilename(apiName)
            val sAuthor = if (author == null) "" else sanitizeFilename(author)
            val sName = sanitizeFilename(name)
            val id = "$sApiName$sAuthor$sName".hashCode()

            val dir =
                File(
                    activity.filesDir.toString() + "${fs}$sApiName${fs}$sAuthor${fs}$sName".replace(
                        "${fs}${fs}",
                        fs.toString()
                    )
                )

            removeKey(DOWNLOAD_SIZE, id.toString())
            removeKey(DOWNLOAD_TOTAL, id.toString())

            if (dir.isDirectory) {
                dir.deleteRecursively()
            }
        } catch (t: Throwable) {
            logError(t)
        }
    }

    fun downloadInfo(
        context: Context?,
        author: String?,
        name: String,
        apiName: String,
    ): DownloadProgress? {
        if (context == null) return null

        try {
            val sApiname = sanitizeFilename(apiName)
            val sAuthor = if (author == null) "" else sanitizeFilename(author)
            val sName = sanitizeFilename(name)
            val id = generateId(apiName, author, name)

            val count = File(
                context.filesDir.toString() + getDirectory(
                    sApiname,
                    sAuthor,
                    sName
                )
            ).listFiles()?.count { it.name.endsWith(".txt") } ?: return null

            /*var sStart = start
            if (sStart == -1) { // CACHE DATA
                sStart = maxOf(getKey(DOWNLOAD_SIZE, id.toString(), 0)!! - 1, 0)
            }

            var count = sStart
            for (index in sStart..total) {
                val filepath =
                    context.filesDir.toString() + getFilename(
                        sApiname,
                        sAuthor,
                        sName,
                        index
                    )
                val rFile = File(filepath)
                if (rFile.exists() && rFile.length() > 10) {
                    count++
                } else {
                    break
                }
            }

            if (sStart == count && start > 0) {
                return downloadInfo(context, author, name, total, apiName, maxOf(sStart - 100, 0))
            }
            */

            setKey(DOWNLOAD_SIZE, id.toString(), count)
            val total = getKey<Int>(DOWNLOAD_TOTAL, id.toString()) ?: return null
            return DownloadProgress(count, total)
        } catch (e: Exception) {
            return null
        }
    }

    @JvmName("openEpub1")
    fun Activity.openEpub(name: String, openInApp: Boolean? = null): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

        if (openInApp ?: !(settingsManager.getBoolean(
                this.getString(R.string.external_reader_key),
                true
            ))
        ) {
            val myIntent = Intent(this, ReadActivity::class.java)

            val relativePath = (Environment.DIRECTORY_DOWNLOADS + "${fs}Epub${fs}")
            val displayName = "${sanitizeFilename(name)}.epub"

            val context = this

            val fileUri = if (isScopedStorage()) {
                val cr = context.contentResolver ?: return false
                cr.getExistingDownloadUriOrNullQ(relativePath, displayName) ?: return false
            } else {
                val normalPath =
                    "${Environment.getExternalStorageDirectory()}${fs}$relativePath$displayName"

                val bookFile = File(normalPath)
                bookFile.toUri()
            }
            myIntent.setDataAndType(fileUri, "application/epub+zip")

            startActivity(myIntent)
            return true
        }

        if (!checkWrite()) {
            requestRW()
            if (!checkWrite()) return false
        }

        val context = this

        val relativePath = (Environment.DIRECTORY_DOWNLOADS + "${fs}Epub${fs}")
        val displayName = "${sanitizeFilename(name)}.epub"

        try {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            // val mime = MimeTypeMap.getSingleton()
            //  val ext: String = ".epub" //bookFile.name.substring(bookFile.name.lastIndexOf(".") + 1)
            val type = "application/epub+zip"//mime.getMimeTypeFromExtension(ext)

            if (isScopedStorage()) {
                val cr = context.contentResolver ?: return false

                val fileUri =
                    cr.getExistingDownloadUriOrNullQ(relativePath, displayName) ?: return false
                intent.setDataAndType(
                    fileUri, type
                )
            } else {
                val normalPath =
                    "${Environment.getExternalStorageDirectory()}${fs}$relativePath$displayName"

                val bookFile = File(normalPath)

                intent.setDataAndType(
                    FileProvider.getUriForFile(
                        this,
                        this.applicationContext.packageName + ".provider",
                        bookFile
                    ), type
                ) // THIS IS NEEDED BECAUSE A REGULAR INTENT WONT OPEN MOONREADER
            }

            this.startActivity(intent)
            //this.startActivityForResult(intent,1337) // SEE @moonreader
        } catch (e: Exception) {
            return false
        }
        return true
    }

    fun openQuickStream(activity: Activity?, uri: Uri?) {
        if (uri == null || activity == null) return
        val myIntent = Intent(activity, ReadActivity::class.java)
        myIntent.setDataAndType(uri, "quickstream")
        activity.startActivity(myIntent)
    }

    private fun isScopedStorage(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ContentResolver.getExistingDownloadUriOrNullQ(
        relativePath: String,
        displayName: String
    ): Uri? {
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                //MediaStore.MediaColumns.DISPLAY_NAME,   // unused (for verification use only)
                //MediaStore.MediaColumns.RELATIVE_PATH,  // unused (for verification use only)
            )

            val selection =
                "${MediaStore.MediaColumns.RELATIVE_PATH}='$relativePath' AND " + "${MediaStore.MediaColumns.DISPLAY_NAME}='$displayName'"

            val result = this.query(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection, selection, null, null
            )

            result.use { c ->
                if (c != null && c.count >= 1) {
                    c.moveToFirst().let {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                        /*
                        val cDisplayName = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                        val cRelativePath = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH))*/

                        return ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                    }
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    @JvmName("openEpub2")
    fun openEpub(activity: Activity?, name: String, openInApp: Boolean? = null): Boolean {
        if (activity == null) return false

        if (!activity.checkWrite()) {
            activity.requestRW()
            return false
        }

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)

        if (openInApp ?: !(settingsManager.getBoolean(
                activity.getString(R.string.external_reader_key),
                true
            ))
        ) {
            val myIntent = Intent(activity, ReadActivity::class.java)

            val relativePath = (Environment.DIRECTORY_DOWNLOADS + "${fs}Epub${fs}")
            val displayName = "${sanitizeFilename(name)}.epub"

            val fileUri = if (isScopedStorage()) {
                val cr = activity.contentResolver ?: return false
                cr.getExistingDownloadUriOrNullQ(relativePath, displayName) ?: return false
            } else {
                val normalPath =
                    "${Environment.getExternalStorageDirectory()}${fs}$relativePath$displayName"

                val bookFile = File(normalPath)
                bookFile.toUri()
            }
            myIntent.setDataAndType(fileUri, "application/epub+zip")

            activity.startActivity(myIntent)
            return true
        }

        val relativePath = (Environment.DIRECTORY_DOWNLOADS + "${fs}Epub${fs}")
        val displayName = "${sanitizeFilename(name)}.epub"

        try {
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            // val mime = MimeTypeMap.getSingleton()
            //  val ext: String = ".epub" //bookFile.name.substring(bookFile.name.lastIndexOf(".") + 1)
            val type = "application/epub+zip"//mime.getMimeTypeFromExtension(ext)

            if (isScopedStorage()) {
                val cr = activity.contentResolver ?: return false

                val fileUri =
                    cr.getExistingDownloadUriOrNullQ(relativePath, displayName) ?: return false
                intent.setDataAndType(
                    fileUri, type
                )
            } else {
                val normalPath =
                    "${Environment.getExternalStorageDirectory()}${fs}$relativePath$displayName"

                val bookFile = File(normalPath)

                intent.setDataAndType(
                    FileProvider.getUriForFile(
                        activity,
                        activity.applicationContext.packageName + ".provider",
                        bookFile
                    ), type
                ) // THIS IS NEEDED BECAUSE A REGULAR INTENT WONT OPEN MOONREADER
            }

            activity.startActivity(intent)
            //this.startActivityForResult(intent,1337) // SEE @moonreader
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun Context.getStripHtml(): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return (settingsManager.getBoolean(this.getString(R.string.remove_external_key), true))
    }

    private fun getChapter(
        filepath: String,
        index: Int,
        stripHtml: Boolean
    ): BookDownloader.LoadedChapter? {
        val rFile = File(filepath)
        if (rFile.exists()) {
            val text = rFile.readText()
            val firstChar = text.indexOf('\n')
            if (firstChar == -1) {
                return null
            } // Invalid File
            val title = text.substring(0, firstChar)
            val data = text.substring(firstChar + 1)
            val html = (if (stripHtml) stripHtml(data, title, index) else data)
            return BookDownloader.LoadedChapter(title, html)
        }
        return null
    }

    suspend fun downloadIndividualChapter(
        filepath: String,
        api: APIRepository,
        data: ChapterData,
        forceReload: Boolean = false,
        maxTries: Int = 5
    ): Boolean = withContext(Dispatchers.IO) {
        val rFile = File(filepath)
        if (rFile.exists() && !forceReload) {
            return@withContext true
        }
        rFile.parentFile?.mkdirs()
        if (rFile.isDirectory) rFile.delete()
        rFile.createNewFile()

        for (i in 0..maxTries) {
            val page = api.loadHtml(data.url)

            if (page != null) {
                rFile.writeText("${data.name}\n${page}")
                return@withContext true
            } else {
                delay(5000) // ERROR
            }
            if (api.rateLimitTime > 0) {
                delay(api.rateLimitTime)
            }
        }
        return@withContext false
    }

    fun turnToEpub(activity: Activity?, author: String?, name: String, apiName: String): Boolean {
        if (activity == null) return false
        if (!activity.checkWrite()) {
            activity.requestRW()
            return false
        }

        try {
            val sApiName = sanitizeFilename(apiName)
            val sAuthor = if (author == null) "" else sanitizeFilename(author)
            val sName = sanitizeFilename(name)
            val id = "$sApiName$sAuthor$sName".hashCode()

            val book = Book()
            val metadata = book.metadata
            if (author != null) {
                metadata.addAuthor(Author(author))
            }
            metadata.addTitle(name)

            val posterFilepath =
                activity.filesDir.toString() + getFilenameIMG(sApiName, sAuthor, sName)
            val pFile = File(posterFilepath)
            if (pFile.exists()) {
                book.coverImage = Resource(pFile.readBytes(), MediaType("cover", ".jpg"))
            }

            val stripHtml = activity.getStripHtml()
            var index = 0

            // This overshoots on lower chapter counts but the loading time is negligible
            // This parallelization can reduce generation from ~5s to ~3s.
            val threads = 100
            var hasChapters = true
            while (hasChapters) {
                // Using any async does not give performance improvements
                val chapters = (index until index + threads).pmap { threadIndex ->
                    val filepath =
                        activity.filesDir.toString() + getFilename(
                            sApiName,
                            sAuthor,
                            sName,
                            threadIndex
                        )
                    val chap = getChapter(filepath, threadIndex, stripHtml) ?: return@pmap null
                    Triple(
                        Resource(
                            "id$threadIndex",
                            chap.html.toByteArray(),
                            "chapter$threadIndex.html",
                            MediatypeService.XHTML
                        ), threadIndex, chap.title
                    )
                }
                // This needs to be in series
                chapters.sortedBy { it?.second }.forEach { chapter ->
                    if (chapter == null) {
                        hasChapters = false
                        return@forEach
                    }
                    book.addSection(chapter.third, chapter.first)
                }
                index += threads
            }

            val epubWriter = EpubWriter()

            val fileStream: OutputStream

            val relativePath = (Environment.DIRECTORY_DOWNLOADS + "${fs}Epub${fs}")
            val displayName = "${sanitizeFilename(name)}.epub"

            if (isScopedStorage()) {
                val cr = activity.contentResolver ?: return false

                val currentExistingFile =
                    cr.getExistingDownloadUriOrNullQ(
                        relativePath,
                        displayName
                    ) // CURRENT FILE WITH THE SAME PATH

                if (currentExistingFile != null) { // DELETE FILE IF FILE EXITS AND NOT RESUME
                    val rowsDeleted =
                        activity.contentResolver.delete(currentExistingFile, null, null)
                    if (rowsDeleted < 1) {
                        println("ERROR DELETING FILE!!!")
                    }
                }

                val contentUri =
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) // USE INSTEAD OF MediaStore.Downloads.EXTERNAL_CONTENT_URI

                val newFile = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.TITLE, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/epub+zip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }

                val newFileUri = cr.insert(
                    contentUri,
                    newFile
                ) ?: return false

                fileStream = cr.openOutputStream(newFileUri, "w")
                    ?: return false
            } else {
                val normalPath =
                    "${Environment.getExternalStorageDirectory()}${fs}$relativePath$displayName"

                // NORMAL NON SCOPED STORAGE FILE CREATION
                val rFile = File(normalPath)
                if (!rFile.exists()) {
                    rFile.parentFile?.mkdirs()
                    if (!rFile.createNewFile()) return false
                } else {
                    rFile.parentFile?.mkdirs()
                    if (!rFile.delete()) return false
                    if (!rFile.createNewFile()) return false
                }
                fileStream = FileOutputStream(rFile, false)
            }

            setKey(DOWNLOAD_EPUB_SIZE, id.toString(), book.contents.size)
            epubWriter.write(book, fileStream)
            return true
        } catch (e: Exception) {
            logError(e)
            return false
        }
    }
}

object NotificationHelper {

    private var hasCreatedNotChanel = false
    private fun Context.createNotificationChannel() {
        hasCreatedNotChanel = true
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = CHANNEL_NAME //getString(R.string.channel_name)
            val descriptionText = CHANNEL_DESCRIPT//getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun etaToString(etaMs: Long?): String {
        if (etaMs == null) return ""
        val eta = (etaMs / 1000.0).toInt()
        val hours: Int = eta / 3600
        val minutes: Int = (eta % 3600) / 60
        val seconds: Int = eta % 60
        var timeformat = String.format("%02d h %02d min %02d s", hours, minutes, seconds)
        if (minutes <= 0 && hours <= 0) {
            timeformat = String.format("%02d s", seconds)
        } else if (hours <= 0) {
            timeformat = String.format("%02d min %02d s", minutes, seconds)
        }
        return timeformat
    }

    suspend fun createNotification(
        context: Context?,
        source: String,
        id: Int,
        load: LoadResponse,
        stateProgressState: BookDownloader2Helper.DownloadProgressState,
        state: BookDownloader2Helper.DownloadState,
        showNotification: Boolean,
    ) {
        if (context == null) return

        var timeformat = ""
        if (state == BookDownloader2Helper.DownloadState.IsDownloading) { // ETA
            timeformat = etaToString(stateProgressState.etaMs)
        }

        if (showNotification) {
            val intent = Intent(context, MainActivity::class.java).apply {
                data = source.toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_MUTABLE else 0
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setAutoCancel(true)
                .setColorized(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setColor(context.colorFromAttribute(R.attr.colorPrimary))
                .setContentText(
                    when (state) {
                        BookDownloader2Helper.DownloadState.IsDone -> "Download Done - ${load.name}"
                        BookDownloader2Helper.DownloadState.IsDownloading -> "Downloading ${load.name} - ${stateProgressState.progress}/${stateProgressState.total}"
                        BookDownloader2Helper.DownloadState.IsPaused -> "Paused ${load.name} - ${stateProgressState.progress}/${stateProgressState.total}"
                        BookDownloader2Helper.DownloadState.IsFailed -> "Error ${load.name} - ${stateProgressState.progress}/${stateProgressState.total}"
                        BookDownloader2Helper.DownloadState.IsStopped -> "Stopped ${load.name} - ${stateProgressState.progress}/${stateProgressState.total}"
                        else -> throw NotImplementedError()
                    }
                )
                .setSmallIcon(
                    when (state) {
                        BookDownloader2Helper.DownloadState.IsDone -> R.drawable.rddone
                        BookDownloader2Helper.DownloadState.IsDownloading -> R.drawable.rdload
                        BookDownloader2Helper.DownloadState.IsPaused -> R.drawable.rdpause
                        BookDownloader2Helper.DownloadState.IsFailed -> R.drawable.rderror
                        BookDownloader2Helper.DownloadState.IsStopped -> R.drawable.rderror
                        else -> throw NotImplementedError()
                    }
                )
                .setContentIntent(pendingIntent)

            if (state == BookDownloader2Helper.DownloadState.IsDownloading) {
                builder.setSubText("$timeformat remaining")
            }
            if (state == BookDownloader2Helper.DownloadState.IsDownloading || state == BookDownloader2Helper.DownloadState.IsPaused) {
                builder.setProgress(stateProgressState.total, stateProgressState.progress, false)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (load.posterUrl != null) {
                    val poster = getImageBitmapFromUrl(load.posterUrl)
                    if (poster != null)
                        builder.setLargeIcon(poster)
                }
            }

            if ((state == BookDownloader2Helper.DownloadState.IsDownloading || state == BookDownloader2Helper.DownloadState.IsPaused) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val actionTypes: MutableList<BookDownloader.DownloadActionType> = ArrayList()
                // INIT
                if (state == BookDownloader2Helper.DownloadState.IsDownloading) {
                    actionTypes.add(BookDownloader.DownloadActionType.Pause)
                    actionTypes.add(BookDownloader.DownloadActionType.Stop)
                }

                if (state == BookDownloader2Helper.DownloadState.IsPaused) {
                    actionTypes.add(BookDownloader.DownloadActionType.Resume)
                    actionTypes.add(BookDownloader.DownloadActionType.Stop)
                }

                // ADD ACTIONS
                for ((index, i) in actionTypes.withIndex()) {
                    val _resultIntent = Intent(context, DownloadService::class.java)

                    _resultIntent.putExtra(
                        "type", when (i) {
                            BookDownloader.DownloadActionType.Resume -> "resume"
                            BookDownloader.DownloadActionType.Pause -> "pause"
                            BookDownloader.DownloadActionType.Stop -> "stop"
                        }
                    )

                    _resultIntent.putExtra("id", id)

                    val pending: PendingIntent =
                        PendingIntent.getService(
                            context, 4337 + index + id,
                            _resultIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                        PendingIntent.FLAG_MUTABLE else 0
                        )


                    builder.addAction(
                        NotificationCompat.Action(
                            when (i) {
                                BookDownloader.DownloadActionType.Resume -> R.drawable.rdload
                                BookDownloader.DownloadActionType.Pause -> R.drawable.rdpause
                                BookDownloader.DownloadActionType.Stop -> R.drawable.rderror
                            }, when (i) {
                                BookDownloader.DownloadActionType.Resume -> "Resume"
                                BookDownloader.DownloadActionType.Pause -> "Pause"
                                BookDownloader.DownloadActionType.Stop -> "Stop"
                            }, pending
                        )
                    )
                }
            }

            if (!hasCreatedNotChanel) {
                context.createNotificationChannel()
            }

            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification that you must define
                notify(id, builder.build())
            }
        }
    }
}

object ImageDownloader {
    private val cachedBitmapMutex = Mutex()
    private val cachedBitmaps = hashMapOf<String, Bitmap>()

    suspend fun getImageBitmapFromUrl(url: String): Bitmap? {
        cachedBitmapMutex.withLock {
            if (cachedBitmaps.containsKey(url)) {
                return cachedBitmaps[url]
            }
        }

        val bitmap =
            withContext(Dispatchers.IO) {
                Glide.with(activity ?: return@withContext null)
                    .asBitmap()
                    .load(url).submit(720, 720).get()
            } ?: return null

        cachedBitmapMutex.withLock {
            cachedBitmaps[url] = bitmap
        }
        return bitmap
    }
}

object BookDownloader2 {
    // These locks are here to prevent duplicate generation of epubs
    private val isTurningIntoEpubMutex = Mutex()
    private val isOpeningEpubMutex = Mutex()
    private val isOpeningQuickStreamMutex = Mutex()

    fun openQuickStream(uri: Uri?) = main {
        if (isOpeningQuickStreamMutex.isLocked) return@main
        isOpeningQuickStreamMutex.withLock {
            BookDownloader2Helper.openQuickStream(activity, uri)
        }
    }

    fun stream(card: ResultCached) = ioSafe {
        val api = Apis.getApiFromName(card.apiName)
        val data = api.load(card.source)

        if (data is com.lagradost.quicknovel.mvvm.Resource.Success) {
            val res = data.value

            if (res.data.isEmpty()) {
                showToast(
                    R.string.no_chapters_found,
                    Toast.LENGTH_SHORT
                )
                return@ioSafe
            }

            val uri =
                createQuickStream(
                    BookDownloader.QuickStreamData(
                        BookDownloader.QuickStreamMetaData(
                            res.author,
                            res.name,
                            card.apiName,
                        ),
                        res.posterUrl,
                        res.data.toMutableList()
                    )
                )

            BookDownloader2.openQuickStream(uri)
        } else {
            showToast(R.string.error_loading_novel, Toast.LENGTH_SHORT)
        }
    }

    private fun generateAndReadEpub(author: String?, name: String, apiName: String) {
        if (!turnToEpub(author, name, apiName)) {
            showToast(R.string.error_loading_novel)
            return
        }
        openEpub(name)
    }

    private fun readEpub(author: String?, name: String, apiName: String) {
        if (hasEpub(name)) {
            openEpub(name)
        } else {
            generateAndReadEpub(author, name, apiName)
        }
    }

    fun readEpub(id: Int, downloadedCount: Int, author: String?, name: String, apiName: String) {
        val downloaded = getKey(DOWNLOAD_EPUB_SIZE, id.toString(), 0)!!
        val shouldUpdate = downloadedCount - downloaded != 0
        if (shouldUpdate) {
            generateAndReadEpub(author, name, apiName)
        } else {
            readEpub(author, name, apiName)
        }
    }

    fun deleteNovel(author: String?, name: String, apiName: String) = ioSafe {
        val id = BookDownloader2Helper.generateId(apiName, author, name)

        // send stop action
        addPendingActionAsync(id, BookDownloader.DownloadActionType.Stop)

        // wait until download is stopped
        while (true) {
            if (!currentDownloadsMutex.withLock { currentDownloads.contains(id) }) {
                break
            }
            delay(100)
        }

        // delete the novel
        BookDownloader2Helper.deleteNovel(activity, author, name, apiName)

        // remove from info
        downloadInfoMutex.withLock {
            downloadData -= id
            downloadProgress -= id
        }

        // ping the viewmodels
        downloadRemoved.invoke(id)
    }

    private fun turnToEpub(author: String?, name: String, apiName: String): Boolean {
        return BookDownloader2Helper.turnToEpub(activity, author, name, apiName)
    }

    private fun hasEpub(name: String): Boolean {
        return BookDownloader2Helper.hasEpub(activity, name)
    }

    private fun openEpub(name: String, openInApp: Boolean? = null) = main {
        if (isOpeningEpubMutex.isLocked) return@main
        isOpeningEpubMutex.withLock {
            if (!BookDownloader2Helper.openEpub(activity, name, openInApp)) {
                showToast(R.string.error_loading_novel) // TODO MAKE BETTER STRING
            }
        }
    }

    fun turnIntoEpub(author: String?, name: String, apiName: String) = main {
        if (isTurningIntoEpubMutex.isLocked) return@main
        isTurningIntoEpubMutex.withLock {
            showToast(R.string.generating_epub)
            if (!BookDownloader2Helper.turnToEpub(activity, author, name, apiName)) {
                showToast(R.string.error_loading_novel) // TODO MAKE BETTER STRING
            }
        }
    }

    val downloadInfoMutex = Mutex()
    val downloadProgress: HashMap<Int, BookDownloader2Helper.DownloadProgressState> =
        hashMapOf()
    val downloadData: HashMap<Int, DownloadFragment.DownloadData> = hashMapOf()

    val downloadProgressChanged = Event<Pair<Int, BookDownloader2Helper.DownloadProgressState>>()
    val downloadDataChanged = Event<Pair<Int, DownloadFragment.DownloadData>>()
    val downloadRemoved = Event<Int>()
    val downloadDataRefreshed = Event<Int>()

    private fun initDownloadProgress() = ioSafe {
        downloadInfoMutex.withLock {
            val keys = getKeys(DOWNLOAD_FOLDER) ?: return@ioSafe
            for (key in keys) {
                val res =
                    getKey<DownloadFragment.DownloadData>(key) ?: continue

                val localId = BookDownloader2Helper.generateId(res.apiName, res.author, res.name)


                BookDownloader2Helper.downloadInfo(
                    context,
                    res.author,
                    res.name,
                    res.apiName
                )?.let { info ->
                    downloadData[localId] = res

                    downloadProgress[localId] = BookDownloader2Helper.DownloadProgressState(
                        BookDownloader2Helper.DownloadState.Nothing,
                        info.progress,
                        info.total,
                        System.currentTimeMillis(),
                        null
                    )
                }
            }
        }

        downloadDataRefreshed.invoke(0)
    }

    private val currentDownloadsMutex = Mutex()
    private val currentDownloads: HashSet<Int> = hashSetOf()

    private val pendingActionMutex = Mutex()
    private val pendingAction: HashMap<Int, BookDownloader.DownloadActionType> = hashMapOf()

    fun addPendingAction(id: Int, action: BookDownloader.DownloadActionType) = ioSafe {
        addPendingActionAsync(id, action)
    }

    private suspend fun addPendingActionAsync(id: Int, action: BookDownloader.DownloadActionType) {
        currentDownloadsMutex.withLock {
            if (!currentDownloads.contains(id)) {
                return
            }
        }

        pendingActionMutex.withLock {
            pendingAction[id] = action
        }
    }

    private suspend fun createNotification(
        id: Int,
        load: LoadResponse,
        stateProgressState: BookDownloader2Helper.DownloadProgressState,
        state: BookDownloader2Helper.DownloadState, show: Boolean = true
    ) {
        NotificationHelper.createNotification(
            activity,
            load.url, id, load, stateProgressState, state, show
        )
    }

    private suspend fun consumeAction(id: Int): BookDownloader.DownloadActionType? {
        pendingActionMutex.withLock {
            pendingAction[id]?.let { action ->
                pendingAction -= id
                return action
            }
        }
        return null
    }

    private suspend fun changeDownload(
        id: Int,
        action: BookDownloader2Helper.DownloadProgressState.() -> Unit
    ): BookDownloader2Helper.DownloadProgressState? {
        val data = downloadInfoMutex.withLock {
            downloadProgress[id]?.apply {
                action()
                lastUpdatedMs = System.currentTimeMillis()
            }
        }

        downloadProgressChanged.invoke(Pair(id, data ?: return null))
        return data
    }

    fun downloadFromCard(
        card: DownloadFragment.DownloadDataLoaded,
    ) =
        ioSafe {
            currentDownloadsMutex.withLock {
                if (currentDownloads.contains(card.id)) {
                    return@ioSafe
                }
            }

            // set pending before download
            downloadInfoMutex.withLock {
                downloadProgress[card.id]?.apply {
                    state = BookDownloader2Helper.DownloadState.IsPending
                    lastUpdatedMs = System.currentTimeMillis()
                    downloadProgressChanged.invoke(Pair(card.id, this))
                }
            }

            val api = Apis.getApiFromName(card.apiName)
            val data = api.load(card.source)

            if (data is com.lagradost.quicknovel.mvvm.Resource.Success) {
                val res = data.value
                download(
                    res, api
                )
            } else {
                // failed to get, but not inside download function, so fail here
                downloadInfoMutex.withLock {
                    downloadProgress[card.id]?.apply {
                        state = BookDownloader2Helper.DownloadState.IsFailed
                        lastUpdatedMs = System.currentTimeMillis()
                        downloadProgressChanged.invoke(Pair(card.id, this))
                    }
                }
            }
        }


    fun download(load: LoadResponse, api: APIRepository) {
        download(load, api, 0 until load.data.size)
    }

    fun download(load: LoadResponse, api: APIRepository, range: ClosedRange<Int>) = ioSafe {
        val filesDir = activity?.filesDir ?: return@ioSafe
        val sApiName = BookDownloader2Helper.sanitizeFilename(api.name)
        val sAuthor =
            if (load.author == null) "" else BookDownloader2Helper.sanitizeFilename(load.author)
        val sName = BookDownloader2Helper.sanitizeFilename(load.name)
        val id = BookDownloader2Helper.generateId(load, api.name)

        // cant download the same thing twice at the same time
        currentDownloadsMutex.withLock {
            if (currentDownloads.contains(id)) {
                return@ioSafe
            }
            currentDownloads += id
        }
        val totalItems = range.endInclusive + 1

        val currentDownloadData = DownloadFragment.DownloadData(
            load.url,
            load.name,
            load.author,
            load.posterUrl,
            load.rating,
            load.peopleVoted,
            load.views,
            load.synopsis,
            load.tags,
            api.name
        )
        setKey(DOWNLOAD_FOLDER, id.toString(), currentDownloadData)
        setKey(DOWNLOAD_TOTAL, id.toString(), totalItems)

        downloadInfoMutex.withLock {
            downloadData[id] = currentDownloadData
            downloadDataChanged.invoke(Pair(id, currentDownloadData))

            downloadProgress[id]?.apply {
                state = BookDownloader2Helper.DownloadState.IsPending
                lastUpdatedMs = System.currentTimeMillis()
                total = totalItems
                downloadProgressChanged.invoke(Pair(id, this))
            } ?: run {
                downloadProgress[id] = BookDownloader2Helper.DownloadProgressState(
                    BookDownloader2Helper.DownloadState.IsPending,
                    0,
                    totalItems,
                    System.currentTimeMillis(),
                    null
                ).also {
                    downloadProgressChanged.invoke(Pair(id, it))
                }
            }
        }


        try {
            // 1. download the image
            try {
                if (load.posterUrl != null) {
                    val filepath = BookDownloader2Helper.getFilenameIMG(sApiName, sAuthor, sName)
                    val posterFilepath =
                        filesDir.toString() + filepath
                    val pFile = File(posterFilepath)

                    // dont need to redownload the image every time
                    if (!pFile.exists() || getKey<String>(
                            filepath,
                            load.posterUrl
                        ) != load.posterUrl
                    ) {
                        setKey(filepath, load.posterUrl)
                        val get = MainActivity.app.get(load.posterUrl)
                        val bytes = get.okhttpResponse.body.bytes()

                        pFile.parentFile?.mkdirs()
                        pFile.writeBytes(bytes)
                    }
                }
            } catch (e: Exception) {
                logError(e)
                //delay(1000)
            }

            // 2. download the text files
            var currentState = BookDownloader2Helper.DownloadState.IsDownloading
            var timePerLoadMs = 1000.0
            var downloadedTotal = 0 // how many sucessfull get requests

            for (index in range.start..range.endInclusive) {
                val data = load.data.getOrNull(index) ?: continue

                // consume any action and wait until not paused
                while (true) {
                    when (consumeAction(id)) {
                        BookDownloader.DownloadActionType.Pause -> {
                            BookDownloader2Helper.DownloadState.IsPaused
                        }

                        BookDownloader.DownloadActionType.Resume -> BookDownloader2Helper.DownloadState.IsDownloading
                        BookDownloader.DownloadActionType.Stop -> BookDownloader2Helper.DownloadState.IsStopped
                        else -> null
                    }?.let { newState ->
                        // if a new state is consumed then push that data instantly
                        changeDownload(id) {
                            state = newState
                        }?.let { progressState ->
                            createNotification(id, load, progressState, newState)
                        }
                        currentState = newState
                    }
                    if (currentState != BookDownloader2Helper.DownloadState.IsPaused) {
                        break
                    }
                    delay(200)
                }

                val filepath =
                    filesDir.toString() + BookDownloader2Helper.getFilename(
                        sApiName,
                        sAuthor,
                        sName,
                        index
                    )
                val rFile = File(filepath)
                if (rFile.exists()) {
                    if (rFile.length() > 10) { // TO PREVENT INVALID FILE FROM HAVING TO REMOVE EVERYTHING
                        continue
                    }
                }

                val beforeDownloadTime = System.currentTimeMillis()

                if (!BookDownloader2Helper.downloadIndividualChapter(filepath, api, data)) {
                    currentState = BookDownloader2Helper.DownloadState.IsFailed
                }

                downloadedTotal += 1

                val afterDownloadTime = System.currentTimeMillis()
                timePerLoadMs =
                    (afterDownloadTime - beforeDownloadTime) * 0.05 + timePerLoadMs * 0.95 // rolling average

                changeDownload(id) {
                    this.progress = index
                    state = currentState
                    etaMs = (timePerLoadMs * (range.endInclusive - index)).toLong()
                }?.let { progressState ->
                    createNotification(id, load, progressState, currentState)
                }

                when (currentState) {
                    BookDownloader2Helper.DownloadState.IsStopped -> return@ioSafe
                    BookDownloader2Helper.DownloadState.IsFailed -> {
                        // we are only interested in a notification if we failed
                        changeDownload(id) {
                            this.progress = index
                            state = currentState
                        }?.let { newProgressState ->
                            createNotification(
                                id,
                                load,
                                newProgressState,
                                BookDownloader2Helper.DownloadState.IsFailed
                            )
                        }
                        return@ioSafe
                    }

                    else -> {}
                }
            }
            changeDownload(id) {
                this.progress = totalItems
                state = BookDownloader2Helper.DownloadState.IsDone
            }?.let { progressState ->
                // only notify done if we have actually done some work
                if (downloadedTotal > 0)
                    createNotification(
                        id,
                        load,
                        progressState,
                        BookDownloader2Helper.DownloadState.IsDone
                    )
            }
        } finally {
            currentDownloadsMutex.withLock {
                currentDownloads -= id
            }
        }
    }

    init {
        initDownloadProgress()
    }
}