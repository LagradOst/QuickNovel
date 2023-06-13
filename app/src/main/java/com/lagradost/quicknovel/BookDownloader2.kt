package com.lagradost.quicknovel

import android.app.Activity
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
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader.checkWrite
import com.lagradost.quicknovel.BookDownloader.requestRW
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.main
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
    )

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
            ).listFiles()?.size ?: return null

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

class BookDownloader2 : ViewModel() {
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

    fun openEpub(name: String, openInApp: Boolean? = null) = main {
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

    private val downloadInfoMutex = Mutex()
    private val downloadProgress: HashMap<Int, BookDownloader2Helper.DownloadProgressState> =
        hashMapOf()
    private val downloadData: HashMap<Int, DownloadFragment.DownloadData> = hashMapOf()

    private fun initDownloadProgress() = ioSafe {
        downloadInfoMutex.withLock {
            val keys = getKeys(DOWNLOAD_FOLDER) ?: return@ioSafe
            for (key in keys) {
                val res =
                    getKey<DownloadFragment.DownloadData>(key) ?: continue

                val localId = BookDownloader2Helper.generateId(res.apiName, res.author, res.name)

                downloadData[localId] = res

                BookDownloader2Helper.downloadInfo(
                    context,
                    res.author,
                    res.name,
                    res.apiName
                )?.let { info ->
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
    }

    private val currentDownloadsMutex = Mutex()
    private val currentDownloads: HashSet<Int> = hashSetOf()

    private val pendingActionMutex = Mutex()
    private val pendingAction : HashMap<Int, BookDownloader.DownloadActionType> = hashMapOf()

    suspend fun consumeAction(id : Int) : BookDownloader.DownloadActionType? {
        pendingActionMutex.withLock {
            pendingAction[id]?.let { action ->
                pendingAction -= id
                return action
            }
        }
        return null
    }

    suspend fun changeDownload(id : Int, action : BookDownloader2Helper.DownloadProgressState.() -> Unit) {
        downloadInfoMutex.withLock {
            // TODO PUSH
            downloadProgress[id]?.apply{
                action()
                lastUpdatedMs = System.currentTimeMillis()
            }
        }
    }

    fun download(load: LoadResponse, api: APIRepository, range: ClosedRange<Int>) = ioSafe {
        val filesDir = activity?.filesDir ?: return@ioSafe
        val sApiName = BookDownloader2Helper.sanitizeFilename(api.name)
        val sAuthor =
            if (load.author == null) "" else BookDownloader2Helper.sanitizeFilename(load.author)
        val sName = BookDownloader2Helper.sanitizeFilename(load.name)
        val id = BookDownloader2Helper.generateId(load, api.name)

        currentDownloadsMutex.withLock {
            if (currentDownloads.contains(id)) {
                return@ioSafe
            }
            currentDownloads += id
        }

        downloadInfoMutex.withLock {
            // TODO PUSH
            downloadProgress[id]?.apply {
                state = BookDownloader2Helper.DownloadState.IsPending
                lastUpdatedMs = System.currentTimeMillis()
                total = range.endInclusive
            } ?: run {
                downloadProgress[id] = BookDownloader2Helper.DownloadProgressState(
                    BookDownloader2Helper.DownloadState.IsPending,
                    0,
                    range.endInclusive,
                    System.currentTimeMillis(),
                    null
                )
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
                    when(consumeAction(id)) {
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
                        }
                        currentState = newState
                    }
                    if(currentState != BookDownloader2Helper.DownloadState.IsPaused) {
                        break
                    }
                    delay(200)
                }

                changeDownload(id) {
                    this.progress = index
                    state = currentState
                    etaMs = (timePerLoadMs * (range.endInclusive - index + 1)).toLong()
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

                when(currentState) {
                    BookDownloader2Helper.DownloadState.IsStopped -> return@ioSafe
                    BookDownloader2Helper.DownloadState.IsFailed -> {
                        changeDownload(id) {
                            this.progress = index
                            state = currentState
                        }
                        return@ioSafe
                    }
                    else -> {}
                }
            }
            changeDownload(id) {
                this.progress = range.endInclusive
                state = BookDownloader2Helper.DownloadState.IsDone
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