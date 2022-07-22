package com.lagradost.quicknovel

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.Intent.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.mapper
import com.lagradost.quicknovel.DataStore.removeKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.services.DownloadService
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName
import com.lagradost.quicknovel.util.Event
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import kotlinx.coroutines.delay
import nl.siegmann.epublib.domain.Author
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.domain.MediaType
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubWriter
import nl.siegmann.epublib.service.MediatypeService
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


const val CHANNEL_ID = "epubdownloader.general"
const val CHANNEL_NAME = "Downloads"
const val CHANNEL_DESCRIPT = "The download notification channel"

object BookDownloader {
    data class DownloadResponse(
        val progress: Int,
        val total: Int,
        val id: Int,
    )

    data class DownloadNotification(
        val progress: Int,
        val total: Int,
        val id: Int,
        val ETA: String,
        val state: DownloadType,
    )

    enum class DownloadActionType {
        Pause,
        Resume,
        Stop,
    }

    enum class DownloadType {
        IsPaused,
        IsDownloading,
        IsDone,
        IsFailed,
        IsStopped,
    }

    private const val reservedChars = "|\\?*<\":>+[]/'"
    private fun sanitizeFilename(name: String): String {
        var tempName = name
        for (c in reservedChars) {
            tempName = tempName.replace(c, ' ')
        }
        return tempName.replace("  ", " ")
    }

    private val fs = File.separatorChar

    private fun getFilename(apiName: String, author: String, name: String, index: Int): String {
        return "$fs$apiName$fs$author$fs$name$fs$index.txt".replace("$fs$fs", "$fs")
    }

    private fun getFilenameIMG(apiName: String, author: String, name: String): String {
        return "$fs$apiName$fs$author$fs$name${fs}poster.jpg".replace("$fs$fs", "$fs")
    }

    private val cachedBitmaps = hashMapOf<String, Bitmap>()

    fun Context.updateDownload(id: Int, state: DownloadType) {
        if (state == DownloadType.IsStopped || state == DownloadType.IsFailed || state == DownloadType.IsDone) {
            if (isRunning.containsKey(id)) {
                isRunning.remove(id)
            }
        } else {
            isRunning[id] = state
        }

        val not = cachedNotifications[id]
        if (not != null) {
            createNotification(
                not.source,
                not.id,
                not.load,
                not.progress,
                not.total,
                not.eta,
                state,
                true
            )
        }
    }

    private fun Context.getImageBitmapFromUrl(url: String): Bitmap? {
        if (cachedBitmaps.containsKey(url)) {
            return cachedBitmaps[url]
        }

        val bitmap = Glide.with(this)
            .asBitmap()
            .load(url).into(720, 720)
            .get()
        if (bitmap != null) {
            cachedBitmaps[url] = bitmap
        }
        return null
    }

    val isRunning = hashMapOf<Int, DownloadType>()
    val isTurningIntoEpub = hashMapOf<Int, Boolean>()

    val downloadNotification = Event<DownloadNotification>()
    val downloadRemove = Event<Int>()

    fun generateId(load: LoadResponse, apiName: String): Int {
        return generateId(apiName, load.author, load.name)
    }

    fun generateId(apiName: String, author: String?, name: String): Int {
        val sApiname = sanitizeFilename(apiName)
        val sAuthor = if (author == null) "" else sanitizeFilename(author)
        val sName = sanitizeFilename(name)
        return "$sApiname$sAuthor$sName".hashCode()
    }

    fun Context.downloadInfo(
        author: String?,
        name: String,
        total: Int,
        apiName: String,
        start: Int = -1,
    ): DownloadResponse? {
        try {
            val sApiname = sanitizeFilename(apiName)
            val sAuthor = if (author == null) "" else sanitizeFilename(author)
            val sName = sanitizeFilename(name)
            val id = "$sApiname$sAuthor$sName".hashCode()

            var sStart = start
            if (sStart == -1) { // CACHE DATA
                sStart = maxOf(getKey(DOWNLOAD_SIZE, id.toString(), 0)!! - 1, 0)
            }

            var count = sStart
            for (index in sStart..total) {
                val filepath =
                    filesDir.toString() + getFilename(sApiname, sAuthor, sName, index)
                val rFile = File(filepath)
                if (rFile.exists() && rFile.length() > 10) {
                    count++
                } else {
                    break
                }
            }

            if (sStart == count && start > 0) {
                return downloadInfo(author, name, total, apiName, maxOf(sStart - 100, 0))
            }
            setKey(DOWNLOAD_SIZE, id.toString(), count)
            return DownloadResponse(count, total, id)
        } catch (e: Exception) {
            return null
        }
    }

    fun Context.checkWrite(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
                == PackageManager.PERMISSION_GRANTED)
    }

    fun Activity.requestRW() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            1337
        )
    }

    fun Activity.hasEpub(name: String): Boolean {
        if (!checkWrite()) {
            requestRW()
            if (!checkWrite()) return false
        }
        val context = this

        val relativePath = (Environment.DIRECTORY_DOWNLOADS + "${fs}Epub${fs}")
        val displayName = "${sanitizeFilename(name)}.epub"

        if (isScopedStorage()) {
            val cr = context.contentResolver ?: return false
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

    fun Activity.openQuickStream(uri: Uri?) {
        if (uri == null) return
        val myIntent = Intent(this, ReadActivity::class.java)
        myIntent.setDataAndType(uri, "quickstream")

        startActivity(myIntent)
    }

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
            intent.action = ACTION_VIEW
            intent.addFlags(FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.addFlags(FLAG_GRANT_PREFIX_URI_PERMISSION)
            intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(FLAG_GRANT_WRITE_URI_PERMISSION)
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

    data class QuickStreamMetaData(
        val author: String?,
        val name: String,
        val apiName: String,
    )

    data class QuickStreamData(
        val meta: QuickStreamMetaData,
        val poster: String?,
        val data: MutableList<ChapterData>,
    )

    data class LoadedChapter(val title: String, val html: String)

    private fun getChapter(filepath: String, index: Int, stripHtml: Boolean): LoadedChapter? {
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
            return LoadedChapter(title, html)
        }
        return null
    }

    private fun Context.getFilePath(meta: QuickStreamMetaData, index: Int): String {
        return filesDir.toString() + getFilename(
            sanitizeFilename(meta.apiName),
            if (meta.author == null) "" else sanitizeFilename(meta.author),
            sanitizeFilename(meta.name),
            index
        )
    }

    private fun Context.getStripHtml(): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return (settingsManager.getBoolean(this.getString(R.string.remove_external_key), true))
    }

    suspend fun Context.getQuickChapter(
        meta: QuickStreamMetaData,
        chapter: ChapterData,
        index: Int,
        forceReload: Boolean
    ): LoadedChapter? {
        val path = getFilePath(meta, index)
        downloadIndividualChapter(path, getApiFromName(meta.apiName), chapter, null, forceReload)
        return getChapter(path, index, getStripHtml())
    }

    fun Activity.createQuickStream(data: QuickStreamData): Uri? {
        try {
            if (data.data.isEmpty()) {
                return null
            }

            if (!checkWrite()) {
                requestRW()
                if (!checkWrite()) return null
            }
            val outputDir: File = this.cacheDir
            val fileName = getFilePath(data.meta, -1)

            val outputFile = File(outputDir, fileName)
            outputFile.parentFile?.mkdirs()
            outputFile.createNewFile()
            outputFile.writeText(mapper.writeValueAsString(data))
            return outputFile.toUri()
        } catch (e: Exception) {
            return null
        }
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

    @RequiresApi(Build.VERSION_CODES.Q)
    fun ContentResolver.getFileLength(fileUri: Uri): Long? {
        return try {
            this.openFileDescriptor(fileUri, "r")
                .use { it?.statSize ?: 0 }
        } catch (e: Exception) {
            null
        }
    }

    private fun isScopedStorage(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    fun Activity.turnToEpub(author: String?, name: String, apiName: String): Boolean {
        if (!checkWrite()) {
            requestRW()
            if (!checkWrite()) return false
        }

        try {
            val sApiName = sanitizeFilename(apiName)
            val sAuthor = if (author == null) "" else sanitizeFilename(author)
            val sName = sanitizeFilename(name)
            val id = "$sApiName$sAuthor$sName".hashCode()
            if (isTurningIntoEpub.containsKey(id)) return false
            isTurningIntoEpub[id] = true
            val book = Book()
            val metadata = book.metadata
            if (author != null) {
                metadata.addAuthor(Author(author))
            }
            metadata.addTitle(name)

            val posterFilepath =
                filesDir.toString() + getFilenameIMG(sApiName, sAuthor, sName)
            val pFile = File(posterFilepath)
            if (pFile.exists()) {
                book.coverImage = Resource(pFile.readBytes(), MediaType("cover", ".jpg"))
            }

            val stripHtml = getStripHtml()
            var index = 0
            while (true) {
                val filepath =
                    filesDir.toString() + getFilename(sApiName, sAuthor, sName, index)
                val chap = getChapter(filepath, index, stripHtml) ?: break
                val chapter =
                    Resource(
                        "id$index",
                        chap.html.toByteArray(),
                        "chapter$index.html",
                        MediatypeService.XHTML
                    )
                book.addSection(chap.title, chapter)
                index++
            }

            val epubWriter = EpubWriter()

            val fileStream: OutputStream

            val relativePath = (Environment.DIRECTORY_DOWNLOADS + "${fs}Epub${fs}")
            val displayName = "${sanitizeFilename(name)}.epub"

            val context = this
            if (isScopedStorage()) {
                val cr = context.contentResolver ?: return false

                val currentExistingFile =
                    cr.getExistingDownloadUriOrNullQ(
                        relativePath,
                        displayName
                    ) // CURRENT FILE WITH THE SAME PATH

                if (currentExistingFile != null) { // DELETE FILE IF FILE EXITS AND NOT RESUME
                    val rowsDeleted =
                        context.contentResolver.delete(currentExistingFile, null, null)
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
            isTurningIntoEpub.remove(id)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun Context.remove(author: String?, name: String, apiName: String) {
        try {
            val sApiName = sanitizeFilename(apiName)
            val sAuthor = if (author == null) "" else sanitizeFilename(author)
            val sName = sanitizeFilename(name)
            val id = "$sApiName$sAuthor$sName".hashCode()

            if (isRunning.containsKey(id)) { // GETS FUCKED WHEN PAUSED AND THEN RESUME FIX
                isRunning.remove(id)
            }

            val dir =
                File(
                    filesDir.toString() + "$fs$sApiName$fs$sAuthor$fs$sName".replace(
                        "$fs$fs",
                        "$fs"
                    )
                )

            removeKey(DOWNLOAD_SIZE, id.toString())
            removeKey(DOWNLOAD_TOTAL, id.toString())
            downloadRemove.invoke(id)

            if (dir.isDirectory) {
                val children = dir.list() ?: return
                for (i in children.indices) {
                    File(dir, children[i]).delete()
                }
            }
        } catch (e: Exception) {
            println(e)
        }
    }

    // 0 = FILE EXITS, 1 = SUCCESS, -1 = STOPPED
    private suspend fun downloadIndividualChapter(
        filepath: String,
        api: APIRepository,
        data: ChapterData,
        runningId: Int?,
        forceReload: Boolean = false,
    ): Int {
        val rFile = File(filepath)
        if (rFile.exists() && !forceReload) {
            return 0
        }
        rFile.parentFile?.mkdirs()
        if (rFile.isDirectory) rFile.delete()
        rFile.createNewFile()
        var page: String? = null
        while (page == null) {
            page = api.loadHtml(data.url)
            if (api.rateLimitTime > 0) {
                delay(api.rateLimitTime)
            }

            if (runningId != null) if (!isRunning.containsKey(runningId)) return -1

            if (page != null) {
                rFile.writeText("${data.name}\n${page}")
                return 1
            } else {
                delay(5000) // ERROR
            }
        }
        return -2 // THIS SHOULD NOT HAPPEND
    }

    suspend fun Context.download(load: LoadResponse, api: APIRepository) {
        try {
            val sApiName = sanitizeFilename(api.name)
            val sAuthor = if (load.author == null) "" else sanitizeFilename(load.author)
            val sName = sanitizeFilename(load.name)

            val id = generateId(load, api.name)
            if (isRunning.containsKey(id)) return // prevent multidownload of same files

            isRunning[id] = DownloadType.IsDownloading

            var timePerLoad = 1.0

            try {
                if (load.posterUrl != null) {
                    val filepath = getFilenameIMG(sApiName, sAuthor, sName)
                    val posterFilepath =
                        filesDir.toString() + filepath
                    val pFile = File(posterFilepath)

                    // dont need to redownload the image every time
                    if (!pFile.exists() || this.getKey<String>(
                            filepath,
                            load.posterUrl
                        ) != load.posterUrl
                    ) {
                        this.setKey(filepath, load.posterUrl)
                        val get = app.get(load.posterUrl)
                        val bytes = get.okhttpResponse.body.bytes()

                        pFile.parentFile?.mkdirs()
                        pFile.writeBytes(bytes)
                    }
                }
            } catch (e: Exception) {
                logError(e)
                //delay(1000)
            }
            val total = load.data.size

            try {
                var lastIndex = 0
                var downloadCount = 0
                for ((index, d) in load.data.withIndex()) {
                    if (!isRunning.containsKey(id)) return
                    while (isRunning[id] == DownloadType.IsPaused) {
                        delay(100)
                    }
                    val lastTime = System.currentTimeMillis() / 1000.0

                    val filepath =
                        filesDir.toString() + getFilename(sApiName, sAuthor, sName, index)
                    val rFile = File(filepath)
                    if (rFile.exists()) {
                        if (rFile.length() > 10) { // TO PREVENT INVALID FILE FROM HAVING TO REMOVE EVERYTHING
                            lastIndex = index + 1
                            continue
                        }
                    }

                    when (downloadIndividualChapter(filepath, api, d, id)) {
                        -1 -> return
                        1 -> downloadCount++
                    }

                    val dloadTime = System.currentTimeMillis() / 1000.0
                    timePerLoad =
                        (dloadTime - lastTime) * 0.05 + timePerLoad * 0.95 // rolling avrage
                    createAndStoreNotification(
                        NotificationData(
                            load.url, id,
                            load,
                            index + 1,
                            total,
                            timePerLoad * (total - index),
                            isRunning[id] ?: DownloadType.IsDownloading
                        )
                    )
                    lastIndex = index + 1
                }
                if (lastIndex == total) {
                    createAndStoreNotification(
                        NotificationData(
                            load.url,
                            id,
                            load,
                            lastIndex,
                            total,
                            0.0,
                            DownloadType.IsDone
                        ), downloadCount > 0
                    )
                }
            } catch (e: Exception) {
                println(e)
            }
            isRunning.remove(id)
        } catch (e: Exception) {
            println(e)
        }
    }

    data class NotificationData(
        val source: String,
        val id: Int,
        val load: LoadResponse,
        val progress: Int,
        val total: Int,
        val eta: Double,
        val _state: DownloadType,
    )

    private val cachedNotifications = hashMapOf<Int, NotificationData>()

    private fun Context.createAndStoreNotification(data: NotificationData, show: Boolean = true) {
        cachedNotifications[data.id] = data
        createNotification(
            data.source,
            data.id,
            data.load,
            data.progress,
            data.total,
            data.eta,
            data._state,
            show
        )
    }

    private fun Context.createNotification(
        source: String,
        id: Int,
        load: LoadResponse,
        progress: Int,
        total: Int,
        eta: Double,
        _state: DownloadType,
        showNotification: Boolean,
    ) {
        var state = _state
        if (progress >= total) {
            state = DownloadType.IsDone
        }

        var timeformat = ""
        if (state == DownloadType.IsDownloading) { // ETA
            val eta_int = eta.toInt()
            val hours: Int = eta_int / 3600
            val minutes: Int = (eta_int % 3600) / 60
            val seconds: Int = eta_int % 60
            timeformat = String.format("%02d h %02d min %02d s", hours, minutes, seconds)
            if (minutes <= 0 && hours <= 0) {
                timeformat = String.format("%02d s", seconds)
            } else if (hours <= 0) {
                timeformat = String.format("%02d min %02d s", minutes, seconds)
            }
        }

        val ETA = when (state) {
            DownloadType.IsDone -> "" //Downloaded
            DownloadType.IsDownloading -> timeformat
            DownloadType.IsPaused -> "Paused"
            DownloadType.IsFailed -> "Error"
            DownloadType.IsStopped -> "Stopped"
        }

        val not = DownloadNotification(progress, total, id, ETA, state)

        downloadNotification.invoke(not)

        if (showNotification) {
            val intent = Intent(this, MainActivity::class.java).apply {
                data = source.toUri()
                flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setAutoCancel(true)
                .setColorized(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setColor(this.colorFromAttribute(R.attr.colorPrimary))
                .setContentText(
                    when (state) {
                        DownloadType.IsDone -> "Download Done - ${load.name}"
                        DownloadType.IsDownloading -> "Downloading ${load.name} - $progress/$total"
                        DownloadType.IsPaused -> "Paused ${load.name} - $progress/$total"
                        DownloadType.IsFailed -> "Error ${load.name} - $progress/$total"
                        DownloadType.IsStopped -> "Stopped ${load.name} - $progress/$total"
                    }
                )
                .setSmallIcon(
                    when (state) {
                        DownloadType.IsDone -> R.drawable.rddone
                        DownloadType.IsDownloading -> R.drawable.rdload
                        DownloadType.IsPaused -> R.drawable.rdpause
                        DownloadType.IsFailed -> R.drawable.rderror
                        DownloadType.IsStopped -> R.drawable.rderror
                    }
                )
                .setContentIntent(pendingIntent)

            if (state == DownloadType.IsDownloading) {
                builder.setSubText("$timeformat remaining")
            }
            if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
                builder.setProgress(total, progress, false)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (load.posterUrl != null) {
                    val poster = getImageBitmapFromUrl(load.posterUrl)
                    if (poster != null)
                        builder.setLargeIcon(poster)
                }
            }

            if ((state == DownloadType.IsDownloading || state == DownloadType.IsPaused) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val actionTypes: MutableList<DownloadActionType> = ArrayList()
                // INIT
                if (state == DownloadType.IsDownloading) {
                    actionTypes.add(DownloadActionType.Pause)
                    actionTypes.add(DownloadActionType.Stop)
                }

                if (state == DownloadType.IsPaused) {
                    actionTypes.add(DownloadActionType.Resume)
                    actionTypes.add(DownloadActionType.Stop)
                }

                // ADD ACTIONS
                for ((index, i) in actionTypes.withIndex()) {
                    val _resultIntent = Intent(this, DownloadService::class.java)

                    _resultIntent.putExtra(
                        "type", when (i) {
                            DownloadActionType.Resume -> "resume"
                            DownloadActionType.Pause -> "pause"
                            DownloadActionType.Stop -> "stop"
                        }
                    )

                    _resultIntent.putExtra("id", id)

                    val pending: PendingIntent = PendingIntent.getService(
                        this, 4337 + index + id,
                        _resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    builder.addAction(
                        NotificationCompat.Action(
                            when (i) {
                                DownloadActionType.Resume -> R.drawable.rdload
                                DownloadActionType.Pause -> R.drawable.rdpause
                                DownloadActionType.Stop -> R.drawable.rderror
                            }, when (i) {
                                DownloadActionType.Resume -> "Resume"
                                DownloadActionType.Pause -> "Pause"
                                DownloadActionType.Stop -> "Stop"
                            }, pending
                        )
                    )
                }
            }

            if (!hasCreatedNotChanel) {
                createNotificationChannel()
            }

            with(NotificationManagerCompat.from(this)) {
                // notificationId is a unique int for each notification that you must define
                notify(id, builder.build())
            }
        }
    }

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
}