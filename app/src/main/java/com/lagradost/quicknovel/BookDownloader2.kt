package com.lagradost.quicknovel
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import coil3.Extras
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2.LOCAL_EPUB
import com.lagradost.quicknovel.BookDownloader2.LOCAL_EPUB_MIN_SIZE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.createQuickStream
import com.lagradost.quicknovel.BookDownloader2Helper.generateId
import com.lagradost.quicknovel.BookDownloader2Helper.getDirectory
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.DataStore.mapper
import com.lagradost.quicknovel.ImageDownloader.getImageBitmapFromUrl
import com.lagradost.quicknovel.NotificationHelper.etaToString
import com.lagradost.quicknovel.extractors.ExtractorApi
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.ui.settings.SettingsFragment.Companion.getBasePath
import com.lagradost.quicknovel.ui.settings.SettingsFragment.Companion.getDefaultDir
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName
import com.lagradost.quicknovel.util.AppUtils.textToHtmlChapter
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.main
import com.lagradost.quicknovel.util.Event
import com.lagradost.quicknovel.util.ResultCached
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.pmap
import com.lagradost.safefile.SafeFile
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import me.ag2s.epublib.domain.Author
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.MediaType
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.epub.EpubWriter
import me.ag2s.epublib.domain.MediaTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.ag2s.epublib.epub.EpubReader
import me.ag2s.epublib.util.zip.AndroidZipFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.text.endsWith

enum class DownloadActionType {
    Pause,
    Resume,
    Stop,
}

data class DownloadProgress(
    val progress: Long,
    val total: Long,
    val downloaded: Long,
)

data class DownloadProgressState(
    var state: DownloadState,
    // How many chapters/bytes much have we downloaded, not including skipped chapters
    var progress: Long,
    // How many have we actually downloaded
    var downloaded: Long,
    // How many is there in total
    var total: Long,
    var lastUpdatedMs: Long,
    var etaMs: Long?
) {
    fun eta(context: Context): String {
        return when (state) {
            DownloadState.IsDownloading -> etaToString(etaMs)
            DownloadState.IsDone -> ""
            DownloadState.IsFailed -> context.getString(R.string.failed)
            DownloadState.IsStopped -> context.getString(R.string.stopped)
            DownloadState.Nothing -> ""
            DownloadState.IsPending -> ""
            DownloadState.IsPaused -> context.getString(R.string.paused)
        }
    }
}

data class LoadedChapter(val title: String, val html: String)
enum class DownloadState {
    IsPaused,
    IsDownloading,
    IsDone,
    IsFailed,
    IsStopped,
    IsPending,
    Nothing,
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

object BookDownloader2Helper {
    const val IMPORT_SOURCE = "Download"
    const val IMPORT_SOURCE_PDF = "importsourcepdf"
    private val fs = File.separatorChar
    private const val reservedChars = "|\\?*<\":>+[]/'"

    fun sanitizeFilename(name: String): String {
        var tempName = name
        for (c in reservedChars) {
            tempName = tempName.replace(c, ' ')
        }
        return tempName.replace(Regex("\\s+"), " ").trim()
    }

    fun getDirectory(apiName: String, author: String, name: String): String {
        return "$fs$apiName$fs$author$fs$name".replace("$fs$fs", "$fs")
    }

    fun getFilename(apiName: String, author: String, name: String, index: Int): String {
        return "${getDirectory(apiName, author, name)}$fs$index.txt".replace("$fs$fs", "$fs")
    }

    fun getFilenameIMG(apiName: String, author: String, name: String): String {
        return "${getDirectory(apiName, author, name)}${fs}poster.jpg".replace("$fs$fs", "$fs")
    }

    private val cachedBitmaps = hashMapOf<String, Bitmap>()
    fun getCachedBitmap(
        activity: Activity?,
        apiName: String,
        author: String?,
        name: String
    ): Bitmap? {
        try {
            val filePath = getFilenameIMG(
                sanitizeFilename(apiName),
                sanitizeFilename(author ?: ""),
                sanitizeFilename(name)
            )

            val existing = cachedBitmaps.get(filePath)
            if (existing != null) return existing
            if (activity == null) return null

            val file = activity.filesDir.toString() + filePath
            val data = File(file).readBytes()
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            cachedBitmaps[filePath] = bitmap

            return bitmap
        } catch (t: Throwable) {
            logError(t)
            return null
        }
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

    fun Activity.checkWrite(): Boolean {
        return (ContextCompat.checkSelfPermission(
            this,
            WRITE_EXTERNAL_STORAGE
        )
                == PackageManager.PERMISSION_GRANTED
                // Since Android 13, we can't request external storage permission,
                // so don't check it.
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    }

    fun Activity.requestRW() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                WRITE_EXTERNAL_STORAGE,
                READ_EXTERNAL_STORAGE
            ),
            1337
        )
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
            return fileLength != 0L
        } else {
            val normalPath =
                "${Environment.getExternalStorageDirectory()}${fs}$relativePath$displayName"

            val bookFile = File(normalPath)
            return bookFile.exists()
        }
    }

    fun copyAllData(
        activity: Activity?,
        fromAuthor: String?,
        fromName: String,
        fromApiName: String,
        toAuthor: String?,
        toName: String,
        toApiName: String
    ) {
        if (activity == null) return
        val sFromApiName = sanitizeFilename(fromApiName)
        val sFromAuthor = if (fromAuthor == null) "" else sanitizeFilename(fromAuthor)
        val sFromName = sanitizeFilename(fromName)

        val sToApiName = sanitizeFilename(toApiName)
        val sToAuthor = if (toAuthor == null) "" else sanitizeFilename(toAuthor)
        val sToName = sanitizeFilename(toName)

        //val fromId = "$sFromApiName$sFromAuthor$sFromName".hashCode()
        //val toId = "$sToApiName$sToAuthor$sToName".hashCode()

        val fromDir =
            File(
                activity.filesDir.toString() + getDirectory(sFromApiName, sFromAuthor, sFromName)
            )
        val toDir =
            File(
                activity.filesDir.toString() + getDirectory(sToApiName, sToAuthor, sToName)
            )
        toDir.mkdirs()
        fromDir.copyRecursively(toDir, overwrite = false)
    }

    fun deleteNovel(activity: Activity?, author: String?, name: String, apiName: String) {
        if (activity == null) return
        try {
            val sApiName = sanitizeFilename(apiName)
            val sAuthor = if (author == null) "" else sanitizeFilename(author)
            val sName = sanitizeFilename(name)
            val id = generateId(apiName, author, name)

            val dir =
                File(
                    activity.filesDir.toString() + getDirectory(sApiName, sAuthor, sName)
                )

            removeKey(DOWNLOAD_SIZE, id.toString())
            removeKey(DOWNLOAD_TOTAL, id.toString())
            removeKey(DOWNLOAD_EPUB_SIZE, id.toString())
            removeKey(DOWNLOAD_OFFSET, id.toString())

            if (dir.isDirectory) {
                dir.deleteRecursively()
            }
        } catch (t: Throwable) {
            logError(t)
        }
    }

    //here you decide the progress and downloaded
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

            val epub = File(
                context.filesDir.toString() + getDirectory(
                    sApiname,
                    sAuthor,
                    sName
                ), LOCAL_EPUB
            )

            val (count, downloaded) =
                if (epub.exists()) {
                    var value:Pair<Int, Int> = 1 to 1
                    //if is an epub, calculate progress
                    if (sApiname == IMPORT_SOURCE_PDF)  {
                        val tempFolder = File(context.cacheDir, "temp_$id")

                        if (tempFolder.exists()) {
                            val xhtmlCount = tempFolder.listFiles { _, name ->
                                name.endsWith(".xhtml", ignoreCase = true)
                            }?.size ?: 0
                            value = xhtmlCount to xhtmlCount
                        }
                        else
                        {
                            val total = getKey<Int>(DOWNLOAD_TOTAL, id.toString()) ?: return null
                            value = total to total
                        }
                    }
                    else
                        if (epub.length() > LOCAL_EPUB_MIN_SIZE)
                            value = 1 to 1
                        else
                            value = 0 to 0

                    value
                } else {
                    val existingFiles = File(
                        context.filesDir.toString() + getDirectory(
                            sApiname,
                            sAuthor,
                            sName
                        )
                    ).listFiles()?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }?.sorted()

                    if (existingFiles.isNullOrEmpty()) {
                        0 to 0
                    } else {
                        // find first continued subsequence after DOWNLOAD_OFFSET
                        // and mark downloaded as <= sequence end
                        val start = getKey<Int>(DOWNLOAD_OFFSET, id.toString()) ?: 0
                        val startIndex = maxOf(existingFiles.indexOfFirst { x -> x >= start }, 0)
                        var last = existingFiles[startIndex]
                        var downloads = startIndex + 1
                        for (i in startIndex + 1 until existingFiles.size) {
                            if (existingFiles[i] == last + 1) {
                                downloads += 1
                                last = existingFiles[i]
                            } else {
                                break
                            }
                        }

                        (last + 1) to downloads
                    }
                }
            if (count <= 0) return null

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
            return DownloadProgress(count.toLong(), total.toLong(), downloaded.toLong())
        } catch (e: Exception) {
            return null
        }
    }

    fun openQuickStream(activity: Activity?, uri: Uri?) {
        if (uri == null || activity == null) return
        val myIntent = Intent(activity, ReadActivity2::class.java)
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

    private fun Context.getFilePath(meta: QuickStreamMetaData, index: Int): String {
        return filesDir.toString() + getFilename(
            sanitizeFilename(meta.apiName),
            if (meta.author == null) "" else sanitizeFilename(meta.author),
            sanitizeFilename(meta.name),
            index
        )
    }

    suspend fun Context.getQuickChapter(
        meta: QuickStreamMetaData,
        chapter: ChapterData,
        index: Int,
        forceReload: Boolean
    ): LoadedChapter? {
        val path = getFilePath(meta, index)
        downloadIndividualChapter(path, getApiFromName(meta.apiName), chapter, forceReload)
        return getChapter(path, index, getStripHtml())
    }

    @WorkerThread
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

    @Throws
    fun openEpub(activity: Activity?, name: String, openInApp: Boolean? = null) {
        if (activity == null) throw IOException("No activity")

        if (!activity.checkWrite()) {
            activity.requestRW()
            return
        }

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(activity)
        val subDir =
            activity.getBasePath().first ?: getDefaultDir(activity) ?: throw IOException("No file")
        val displayName = "${sanitizeFilename(name)}.epub"
        val foundFile = subDir.findFileOrThrow(displayName)

        if (openInApp ?: !(settingsManager.getBoolean(
                activity.getString(R.string.external_reader_key),
                true
            ))
        ) {
            val myIntent = Intent(activity, ReadActivity2::class.java)
            myIntent.setDataAndType(foundFile.uriOrThrow(), "application/epub+zip")
            activity.startActivity(myIntent)
            return
        }

        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                        or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val type = "application/epub+zip"
        intent.setDataAndType(
            foundFile.uriOrThrow(), type
        )
        activity.startActivity(intent)
        //this.startActivityForResult(intent,1337) // SEE @moonreader
    }

    private fun Context.getStripHtml(): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        return (settingsManager.getBoolean(this.getString(R.string.remove_external_key), true))
    }

    private fun getChapter(
        filepath: String,
        index: Int,
        stripHtml: Boolean
    ): LoadedChapter? {
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

    suspend fun downloadIndividualChapter(
        filepath: String,
        api: APIRepository,
        data: ChapterData,
        forceReload: Boolean = false,
        maxTries: Int = 5
    ): Boolean = withContext(Dispatchers.IO) {
        val rFile = File(filepath)
        if (rFile.exists() && rFile.length() > 0 && !forceReload) {
            return@withContext true
        }
        rFile.parentFile?.mkdirs()
        if (rFile.isDirectory) rFile.delete()
        val rateLimit = api.rateLimitTime > 0
        for (i in 0..maxTries) {
            if (rateLimit) {
                api.api.rateLimitMutex.lock()
            }
            try {
                val page = api.loadHtml(data.url)

                if (!page.isNullOrBlank()) {
                    rFile.createNewFile() // only create the file when actually needed
                    rFile.writeText("${data.name}\n${page}")
                    if (api.rateLimitTime > 0) {
                        delay(api.rateLimitTime)
                    }
                    return@withContext true
                } else {
                    delay(5000) // ERROR
                    if (api.rateLimitTime > 0) {
                        delay(api.rateLimitTime)
                    }
                }
            } finally {
                if (rateLimit) {
                    api.api.rateLimitMutex.unlock()
                }
            }
        }
        return@withContext false
    }

    @WorkerThread
    @Throws
    fun turnToEpub(
        activity: Activity?,
        author: String?,
        name: String,
        apiName: String,
        synopsis: String?
    ) {
        if (activity == null) throw ErrorLoadingException("No activity")
        if (!activity.checkWrite()) {
            activity.requestRW()
            return
        }

        try {
            val sApiName = sanitizeFilename(apiName)
            val sAuthor = if (author == null) "" else sanitizeFilename(author)
            val sName = sanitizeFilename(name)
            val id = "$sApiName$sAuthor$sName".hashCode()

            val subDir = activity.getBasePath().first ?: getDefaultDir(activity)
            ?: throw IOException("No file")

            //val subDir = baseFile.gotoDirectoryOrThrow("Epub", createMissingDirectories = true)
            val displayName = "${sanitizeFilename(name)}.epub"

            //val relativePath = (Environment.DIRECTORY_DOWNLOADS + "${fs}Epub${fs}")
            subDir.findFile(displayName)?.delete()
            val file = subDir.createFileOrThrow(displayName)

            val fileStream =
                file.openOutputStream(append = false) ?: throw IOException("No outputfile")

            /*if (isScopedStorage()) {
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
            }*/

            val epubFile = File(
                activity.filesDir.toString() + getDirectory(sApiName, sAuthor, sName),
                LOCAL_EPUB
            )
            if (epubFile.exists() && epubFile.length() > LOCAL_EPUB_MIN_SIZE) {
                fileStream.write(epubFile.readBytes())

                setKey(DOWNLOAD_EPUB_SIZE, id.toString(), 1)
            } else {

                val book = EpubBook()
                val metadata = book.metadata
                if (author != null) {
                    metadata.addAuthor(Author(author))
                }

                if (synopsis != null) {
                    metadata.addDescription(synopsis)
                }

                metadata.addTitle(name)

                val posterFilepath =
                    activity.filesDir.toString() + getFilenameIMG(sApiName, sAuthor, sName)
                val pFile = File(posterFilepath)
                if (pFile.exists()) {
                    book.coverImage = Resource(pFile.readBytes(), MediaType("cover", ".jpg"))
                }

                val stripHtml = activity.getStripHtml()
                val head = activity.filesDir.toString()
                val dir = File(head + getDirectory(sApiName, sAuthor, sName))
                // do not include chapters that are stream read downloaded in partial chapter generation
                val start = getKey<Int>(DOWNLOAD_OFFSET, id.toString()) ?: 0
                val chapters = dir.listFiles()?.toList()?.mapNotNull { fileName ->
                    fileName.nameWithoutExtension.toIntOrNull() ?: return@mapNotNull null
                }?.filter { x -> x >= start }?.sorted()

                chapters?.pmap { threadIndex ->

                    val filepath =
                        head + getFilename(
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
                            MediaTypes.XHTML
                        ),
                        threadIndex,
                        chap.title
                    )
                }?.sortedBy {
                    it?.second
                }?.also { list ->
                    if (list.isEmpty()) {
                        throw ErrorLoadingException("Unable to create an empty book")
                    }
                }?.forEach { chapter ->
                    if (chapter == null) {
                        return@forEach
                    }
                    book.addSection(chapter.third, chapter.first)
                }

                val largestChapter = chapters?.maxOrNull()?.plus(1) ?: 0

                val epubWriter = EpubWriter()
                epubWriter.write(book, fileStream)
                setKey(DOWNLOAD_EPUB_SIZE, id.toString(), largestChapter)
            }
            fileStream.close()
        } catch (e: Exception) {
            logError(e)
            throw e
        }
    }
}

object NotificationHelper {
    const val CHANNEL_ID = "epubdownloader.general"
    const val CHANNEL_NAME = "Downloads"
    const val CHANNEL_DESCRIPT = "The download notification channel"
    private var hasCreatedNotChanel = false

    fun ComponentActivity.requestNotifications() {
        // Ask for notification permissions on Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val requestPermissionLauncher = this.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean ->
                println("Notification permission: $isGranted")
            }
            requestPermissionLauncher.launch(
                POST_NOTIFICATIONS
            )
        }
    }

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
        stateProgressState: DownloadProgressState,
        showNotification: Boolean,
        progressInBytes: Boolean,
    ) {
        if (context == null) return
        val state = stateProgressState.state
        var timeFormat = ""
        if (state == DownloadState.IsDownloading) { // ETA
            timeFormat = etaToString(stateProgressState.etaMs)
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
                    if (stateProgressState.total > 1) {
                        val extra = if (progressInBytes) {
                            val bytesToKiloBytes = 1024
                            "${stateProgressState.progress / bytesToKiloBytes} Kb/${stateProgressState.total / bytesToKiloBytes} Kb"
                        } else {
                            "${stateProgressState.progress}/${stateProgressState.total}"
                        }

                        when (state) {
                            DownloadState.IsDone -> "Download Done - ${load.name}"
                            DownloadState.IsDownloading -> "Downloading ${load.name} - $extra"
                            DownloadState.IsPaused -> "Paused ${load.name} - $extra"
                            DownloadState.IsFailed -> "Error ${load.name} - $extra"
                            DownloadState.IsStopped -> "Stopped ${load.name} - $extra"
                            else -> throw NotImplementedError()
                        }
                    } else {
                        when (state) {
                            DownloadState.IsDone -> "Download Done - ${load.name}"
                            DownloadState.IsDownloading -> "Downloading ${load.name}"
                            DownloadState.IsPaused -> "Paused ${load.name}"
                            DownloadState.IsFailed -> "Error ${load.name}"
                            DownloadState.IsStopped -> "Stopped ${load.name}"
                            else -> throw NotImplementedError()
                        }
                    }
                )
                .setSmallIcon(
                    when (state) {
                        DownloadState.IsDone -> R.drawable.rddone
                        DownloadState.IsDownloading -> R.drawable.rdload
                        DownloadState.IsPaused -> R.drawable.rdpause
                        DownloadState.IsFailed -> R.drawable.rderror
                        DownloadState.IsStopped -> R.drawable.rderror
                        else -> throw NotImplementedError()
                    }
                )
                .setContentIntent(pendingIntent)

            if (state == DownloadState.IsDownloading && stateProgressState.total > 2) {
                builder.setSubText("$timeFormat remaining")
            }

            if (state == DownloadState.IsDownloading || state == DownloadState.IsPaused) {
                builder.setProgress(
                    stateProgressState.total.toInt(),
                    stateProgressState.progress.toInt(),
                    stateProgressState.total <= 1
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                load.posterUrl?.let { url ->
                    val poster = context.getImageBitmapFromUrl(url)
                    if (poster != null)
                        builder.setLargeIcon(poster)
                }
            }

            if ((state == DownloadState.IsDownloading || state == DownloadState.IsPaused) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val actionTypes: MutableList<DownloadActionType> = ArrayList()
                // INIT
                if (state == DownloadState.IsDownloading) {
                    actionTypes.add(DownloadActionType.Pause)
                    actionTypes.add(DownloadActionType.Stop)
                }

                if (state == DownloadState.IsPaused) {
                    actionTypes.add(DownloadActionType.Resume)
                    actionTypes.add(DownloadActionType.Stop)
                }

                // ADD ACTIONS
                for ((index, i) in actionTypes.withIndex()) {
                    val resultIntent = Intent(context, DownloadNotificationService::class.java)

                    resultIntent.putExtra(
                        "type", when (i) {
                            DownloadActionType.Resume -> "resume"
                            DownloadActionType.Pause -> "pause"
                            DownloadActionType.Stop -> "stop"
                        }
                    )

                    resultIntent.putExtra("id", id)

                    val pending: PendingIntent =
                        PendingIntent.getService(
                            context, 4337 + index + id,
                            resultIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                        PendingIntent.FLAG_MUTABLE else 0
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
                context.createNotificationChannel()
            }

            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification that you must define
                try {
                    notify(id, builder.build())
                } catch (t: Throwable) {
                    logError(t)
                }
            }
        }
    }
}

object ImageDownloader {
    private val cachedBitmapMutex = Mutex()
    private val cachedBitmaps = hashMapOf<String, Bitmap>()

    suspend fun Context.getImageBitmapFromUrl(url: String, headers: Map<String, String>? = null): Bitmap? {
        try {
            with(cachedBitmapMutex) {
                if (cachedBitmaps.containsKey(url)) {
                    return cachedBitmaps[url]
                }
            }

            val imageLoader = SingletonImageLoader.get(this)

            val request = ImageRequest.Builder(this)
                .data(url)
                .apply {
                    headers?.forEach { (key, value) ->
                        extras[Extras.Key<String>(key)] = value
                    }
                }
                .build()

            val bitmap = runBlocking {
                val result = imageLoader.execute(request)
                (result as? SuccessResult)?.image?.asDrawable(applicationContext.resources)
                    ?.toBitmap()
            }

            bitmap?.let {
                with(cachedBitmapMutex) {
                    cachedBitmaps[url] = it
                }
            }

            return bitmap
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }
}

object BookDownloader2 {
    fun openQuickStream(uri: Uri?) = main {
        BookDownloader2Helper.openQuickStream(activity, uri)
    }

    private val streamMutex = Mutex()

    @WorkerThread
    suspend fun stream(res: LoadResponse, apiName: String) {
        when (res) {
            is StreamResponse -> {
                stream(res, apiName)
            }

            is EpubResponse -> {
                stream(res, apiName)
            }
        }
    }

    @WorkerThread
    suspend fun stream(res: EpubResponse, apiName: String) {
        downloadWorkThread(res, getApiFromName(apiName))
        readEpub(res.author, res.name, apiName, res.synopsis)
    }

    @WorkerThread
    suspend fun stream(res: StreamResponse, apiName: String) {
        if (streamMutex.isLocked) return
        streamMutex.withLock {
            if (res.data.isEmpty()) {
                showToast(
                    R.string.no_chapters_found,
                    Toast.LENGTH_SHORT
                )
                return
            }

            val uri =
                activity?.createQuickStream(
                    QuickStreamData(
                        QuickStreamMetaData(
                            res.author,
                            res.name,
                            apiName,
                        ),
                        res.posterUrl,
                        res.data.toMutableList()
                    )
                )

            openQuickStream(uri)
        }
    }

    private val streamResultMutex = Mutex()
    fun stream(card: ResultCached) = ioSafe {
        if (streamResultMutex.isLocked) return@ioSafe
        streamResultMutex.withLock {
            val api = getApiFromName(card.apiName)
            val data = api.load(card.source)

            if (data is com.lagradost.quicknovel.mvvm.Resource.Success) {
                stream(data.value, card.apiName)
            } else {
                showToast(R.string.error_loading_novel, Toast.LENGTH_SHORT)
            }
        }
    }

    private fun generateAndReadEpub(
        author: String?,
        name: String,
        apiName: String,
        synopsis: String?
    ) {
        showToast(R.string.generating_epub)
        try {
            turnToEpub(author, name, apiName, synopsis)
        } catch (e: ErrorLoadingException) {
            if (e.message != null) {
                showToast(e.message)
            } else {
                throw e
            }
        } catch (e: IOException) {
            if (e.message != null) {
                showToast(e.message)
            } else {
                throw e
            }
        } catch (t: Throwable) {
            showToast(R.string.error_loading_novel)
        }
        openEpub(name)
    }

    private fun readEpub(author: String?, name: String, apiName: String, synopsis: String?) {
        if (hasEpub(name)) {
            openEpub(name)
        } else {
            generateAndReadEpub(author, name, apiName, synopsis)
        }
    }

    private val readEpubMutex = Mutex()

    @WorkerThread
    suspend fun readEpub(
        id: Int,
        downloadedCount: Int,
        author: String?,
        name: String,
        apiName: String,
        synopsis: String?
    ) {
        if (readEpubMutex.isLocked) return
        readEpubMutex.withLock {
            val downloaded = getKey(DOWNLOAD_EPUB_SIZE, id.toString(), 0)!!
            val shouldUpdate = downloadedCount - downloaded != 0
            if (shouldUpdate) {
                generateAndReadEpub(author, name, apiName, synopsis)
            } else {
                readEpub(author, name, apiName, synopsis)
            }
        }
    }

    private val deleteNovelMutex = Mutex()
    private suspend fun deleteNovelAsync(author: String?, name: String, apiName: String) {
        if (deleteNovelMutex.isLocked) return
        deleteNovelMutex.withLock {
            val id = generateId(apiName, author, name)

            // send stop action
            addPendingActionAsync(id, DownloadActionType.Stop)

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
    }

    fun deleteNovel(author: String?, name: String, apiName: String) = ioSafe {
        deleteNovelAsync(author, name, apiName)
    }

    @WorkerThread
    @Throws
    private fun turnToEpub(
        author: String?,
        name: String,
        apiName: String,
        synopsis: String?
    ) {
        return BookDownloader2Helper.turnToEpub(activity, author, name, apiName, synopsis)
    }

    private fun hasEpub(name: String): Boolean {
        return BookDownloader2Helper.hasEpub(activity, name)
    }

    private fun openEpub(name: String, openInApp: Boolean? = null) {
        try {
            BookDownloader2Helper.openEpub(activity, name, openInApp)
        } catch (e: ErrorLoadingException) {
            if (e.message != null) {
                showToast(e.message)
            } else {
                throw e
            }
        } catch (e: IOException) {
            if (e.message != null) {
                showToast(e.message)
            } else {
                throw e
            }
        } catch (t: Throwable) {
            showToast(R.string.error_loading_novel)
        }
    }

    val downloadInfoMutex = Mutex()
    val downloadProgress: HashMap<Int, DownloadProgressState> =
        hashMapOf()
    val downloadData: HashMap<Int, DownloadFragment.DownloadData> = hashMapOf()

    val downloadProgressChanged = Event<Pair<Int, DownloadProgressState>>()
    val downloadDataChanged = Event<Pair<Int, DownloadFragment.DownloadData>>()
    val downloadRemoved = Event<Int>()
    val downloadDataRefreshed = Event<Int>()

    private fun initDownloadProgress() = ioSafe {
        downloadInfoMutex.withLock {
            val keys = getKeys(DOWNLOAD_FOLDER) ?: return@ioSafe
            for (key in keys) {
                val res =
                    getKey<DownloadFragment.DownloadData>(key) ?: continue

                val localId = generateId(res.apiName, res.author, res.name)

                BookDownloader2Helper.downloadInfo(
                    context,
                    res.author,
                    res.name,
                    res.apiName
                )?.let { info ->
                    downloadData[localId] = res

                    downloadProgress[localId] = DownloadProgressState(
                        state = DownloadState.Nothing,
                        progress = info.progress,
                        total = info.total,
                        downloaded = info.downloaded,
                        lastUpdatedMs = System.currentTimeMillis(),
                        etaMs = null
                    )
                }
            }
        }

        downloadDataRefreshed.invoke(0)
    }

    val currentDownloadsMutex = Mutex()
    val currentDownloads: HashSet<Int> = hashSetOf()

    private val pendingActionMutex = Mutex()
    private val pendingAction: HashMap<Int, DownloadActionType> = hashMapOf()

    fun addPendingAction(id: Int, action: DownloadActionType) = ioSafe {
        addPendingActionAsync(id, action)
    }

    private suspend fun addPendingActionAsync(id: Int, action: DownloadActionType) {
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
        stateProgressState: DownloadProgressState,
        show: Boolean = true,
        progressInBytes: Boolean = false
    ) {
        NotificationHelper.createNotification(
            activity,
            load.url, id, load, stateProgressState, show, progressInBytes
        )
    }

    private suspend fun consumeAction(id: Int): DownloadActionType? {
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
        action: DownloadProgressState.() -> Unit
    ): DownloadProgressState? {
        val data = downloadInfoMutex.withLock {
            downloadProgress[id]?.apply {
                action()
                lastUpdatedMs = System.currentTimeMillis()
            }
        }
        downloadProgressChanged.invoke(id to (data ?: return null))
        return data
    }

    private fun migrateKeys(from: Int, to: Int, oldName: String, newName: String) {
        setKey(
            DOWNLOAD_TOTAL, to.toString(),
            getKey<Int>(DOWNLOAD_TOTAL, from.toString())
        )
        setKey(
            DOWNLOAD_FOLDER, to.toString(),
            getKey<DownloadFragment.DownloadData>(DOWNLOAD_FOLDER, from.toString())
        )
        setKey(
            DOWNLOAD_EPUB_SIZE, to.toString(),
            getKey<Int>(DOWNLOAD_EPUB_SIZE, from.toString())
        )

        setKey(
            DOWNLOAD_OFFSET, to.toString(),
            getKey<Int>(DOWNLOAD_OFFSET, from.toString())
        )

        setKey(
            HISTORY_FOLDER, to.toString(),
            getKey<ResultCached>(HISTORY_FOLDER, from.toString())
        )
        removeKey(HISTORY_FOLDER, from.toString())

        setKey(
            RESULT_BOOKMARK, to.toString(),
            getKey<ResultCached>(RESULT_BOOKMARK, from.toString())
        )
        removeKey(RESULT_BOOKMARK, from.toString())

        setKey(
            RESULT_BOOKMARK_STATE, to.toString(),
            getKey<Int>(RESULT_BOOKMARK_STATE, from.toString())
        )
        removeKey(RESULT_BOOKMARK_STATE, from.toString())

        setKey(
            DOWNLOAD_EPUB_LAST_ACCESS, to.toString(),
            getKey<Long>(DOWNLOAD_EPUB_LAST_ACCESS, from.toString())
        )

        setKey(
            EPUB_CURRENT_POSITION, newName,
            getKey<Int>(EPUB_CURRENT_POSITION, oldName)
        )

        setKey(
            EPUB_CURRENT_POSITION_CHAPTER, newName,
            getKey<String>(EPUB_CURRENT_POSITION_CHAPTER, oldName)
        )

        setKey(
            EPUB_CURRENT_POSITION_SCROLL, newName,
            getKey<Int>(EPUB_CURRENT_POSITION_SCROLL, oldName)
        )
        setKey(
            EPUB_CURRENT_POSITION_SCROLL_CHAR, newName,
            getKey<Int>(EPUB_CURRENT_POSITION_SCROLL_CHAR, oldName)
        )
    }

    private val migrationNovelMutex = Mutex()

    @WorkerThread
    suspend fun downloadWorkThread(
        card: DownloadFragment.DownloadDataLoaded,
    ) {
        if(card.isImported) {
            return
        }

        currentDownloadsMutex.withLock {
            if (currentDownloads.contains(card.id)) {
                return
            }
        }

        // set pending before download
        downloadInfoMutex.withLock {
            downloadProgress[card.id]?.apply {
                state = DownloadState.IsPending
                lastUpdatedMs = System.currentTimeMillis()
                downloadProgressChanged.invoke(card.id to this)
            }
        }

        try {
            val api = getApiFromName(card.apiName)
            val data = api.load(card.source, allowCache = false)

            if (data is com.lagradost.quicknovel.mvvm.Resource.Success) {
                val res = data.value
                val newId = generateId(res, card.apiName)
                val oldId = card.id
                // migrate all data here and delete the old
                if (oldId != newId) {
                    // we cant have 2 migrations happening at the same time in case they overlap somehow, this is a *very* cold path anyways
                    migrationNovelMutex.withLock {
                        //showToast("Id mismatch, migrating data from ${card.name} to ${res.name}")
                        migrateKeys(oldId, newId, card.name, res.name)
                        BookDownloader2Helper.copyAllData(
                            activity,
                            card.author,
                            card.name,
                            card.apiName,
                            res.author,
                            res.name,
                            api.name
                        )
                        deleteNovelAsync(card.author, card.name, card.apiName)
                    }
                }

                when (res) {
                    is EpubResponse -> {
                        downloadWorkThread(
                            res, api
                        )
                    }

                    is StreamResponse -> {
                        downloadWorkThread(
                            res, api
                        )
                    }
                }
            } else {
                // failed to get, but not inside download function, so fail here
                downloadInfoMutex.withLock {
                    downloadProgress[card.id]?.apply {
                        state = DownloadState.IsFailed
                        lastUpdatedMs = System.currentTimeMillis()
                        downloadProgressChanged.invoke(card.id to this)
                    }
                }
            }
        } catch (t: Throwable) {
            logError(t)
        }
    }

    fun changeDownloadStart(load: LoadResponse, api: APIRepository, to: Int?) {
        val id = generateId(load, api.name)
        if (to == null) {
            removeKey(DOWNLOAD_OFFSET, id.toString())
            return
        }

        setKey(
            DOWNLOAD_OFFSET, id.toString(), to,
        )
    }

    fun download(load: LoadResponse, context: Context) {
        DownloadFileWorkManager.download(load, context)
    }

    private suspend fun setSuffixData(load: LoadResponse, apiName: String) {
        val id = generateId(load, apiName)

        val newData = DownloadFragment.DownloadData(
            load.url,
            load.name,
            load.author,
            load.posterUrl,
            load.rating,
            load.peopleVoted,
            load.views,
            load.synopsis,
            load.tags,
            apiName,
            System.currentTimeMillis(),
            System.currentTimeMillis()
        )

        setKey(
            DOWNLOAD_FOLDER, id.toString(), newData
        )

        downloadInfoMutex.withLock {
            downloadData[id] = newData
            downloadDataChanged.invoke(id to newData)
        }
    }

    private suspend fun setPrefixData(
        load: LoadResponse,
        apiName: String,
        total: Long,
        downloaded: Long
    ) {
        val id = generateId(load, apiName)

        // cant download the same thing twice at the same time
        currentDownloadsMutex.withLock {
            if (currentDownloads.contains(id)) {
                return
            }
            currentDownloads += id
        }
        val prevDownloadData =
            getKey<DownloadFragment.DownloadData>(DOWNLOAD_FOLDER, id.toString())

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
            apiName,
            System.currentTimeMillis(),
            prevDownloadData?.lastDownloaded
        )
        setKey(DOWNLOAD_FOLDER, id.toString(), currentDownloadData)
        setKey(DOWNLOAD_TOTAL, id.toString(), total)

        downloadInfoMutex.withLock {
            downloadData[id] = currentDownloadData
            downloadDataChanged.invoke(id to currentDownloadData)

            downloadProgress[id]?.apply {
                state = DownloadState.IsPending
                lastUpdatedMs = System.currentTimeMillis()
                this.total = total
                downloadProgressChanged.invoke(id to this)
            } ?: run {

                /*state = DownloadState.Nothing,
                progress = info.progress,
                total = info.total,
                downloaded = info.downloaded,
                lastUpdatedMs = System.currentTimeMillis(),
                etaMs = null*/


                downloadProgress[id] = DownloadProgressState(
                    state = DownloadState.IsPending,
                    progress = 0,
                    total = total,
                    lastUpdatedMs = System.currentTimeMillis(),
                    etaMs = null,
                    downloaded = downloaded,
                ).also {
                    downloadProgressChanged.invoke(id to it)
                }
            }
        }
    }

    const val LOCAL_EPUB: String = "local_epub.epub"
    const val LOCAL_EPUB_MIN_SIZE: Long = 1000
    //to avoid img headers or icons
    val MIN_IMAGE_SIZE = 30 * 1024


    private fun createHtmlWrapper(title: String, content: String): String {
        return """
        <html xmlns="http://www.w3.org/1999/xhtml">
            <head><meta charset="utf-8"/><title>$title</title></head>
            <body>
                $content
            </body>
        </html>
    """.trimIndent()
    }


    //Import pdf area -----------------------------------------------
    private suspend fun handleDownloadActions(id: Int, load: EpubResponse, current: DownloadState): DownloadState {
        val action = consumeAction(id)
        val newState = when (action) {
            DownloadActionType.Pause -> DownloadState.IsPaused
            DownloadActionType.Resume -> DownloadState.IsDownloading
            DownloadActionType.Stop -> DownloadState.IsStopped
            else -> current
        }
        if (newState != current || newState == DownloadState.IsPaused) updateDownloadNotificationState(id, load, newState)
        return newState
    }
    private suspend fun updateDownloadNotificationState(id: Int, load: EpubResponse, state: DownloadState, progress: Int? = null) {
        changeDownload(id) {
            this.state = state
            progress?.let {
                this.progress = it.toLong()
                this.downloaded = it.toLong()
            }
        }?.let { createNotification(id, load, it) }
    }

    fun pdfPageWithoutHAndF(page: PDPage, stripper: PDFTextStripperByArea):String
    {
        //try to delete footer and header
        val percentajeToDelete = 0.03f//3%
        val mediaBox = page.mediaBox
        val fullHeight = mediaBox.height
        val marginHeight = fullHeight * percentajeToDelete
        val bodyArea = RectF(
            mediaBox.lowerLeftX,
            marginHeight,
            mediaBox.upperRightX,
            fullHeight - (marginHeight * 2)
        )
        stripper.addRegion("body", bodyArea)
        stripper.extractRegions(page)
        return  stripper.getTextForRegion("body")
    }

    private fun processImageObject(
        imageXObject: PDImageXObject,
        tempFolder: File,
        chapterBuilder: StringBuilder,
        api: String, author: String, name: String,
        filesDir: File,
        count: Int
    ):Boolean?{
        val bitmap = imageXObject.image
        if (!bitmap.isRecycled)
        {
            try {
                val outStream = ByteArrayOutputStream()

                //get image compressed as byteArray
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
                val imgBytes = outStream.toByteArray()

                // only over NKB images
                if (imgBytes.size >= MIN_IMAGE_SIZE) {

                    val imgHref = "img_$count.jpeg"
                    File(tempFolder, imgHref).writeBytes(imgBytes).also {
                        //I don’t know why it forces me to include the OEBPS folder.
                        chapterBuilder.append("<div style='text-align:center;'><img src='OEBPS/$imgHref' style='max-width:100%;'/></div>")

                        //if it’s the first image, save it as the cover
                        if (count == 1) {
                            val filepath = BookDownloader2Helper.getFilenameIMG(api, author, name)
                            File(filesDir, filepath).apply {
                                parentFile?.mkdirs()
                                writeBytes(imgBytes)
                            }
                        }
                    }

                    return true
                }
            } catch (e: Exception) {
                logError(e)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle() //delete from ram
            }
        }
        return null
    }

    //init imports
    @WorkerThread
    @Throws
    suspend fun downloadPDFWorkThread(data: Uri, context: Context?) {
        if(context == null)
            return

        //init pdf reader
        if (!PDFBoxResourceLoader.isReady())
            PDFBoxResourceLoader.init(context)

        // open file
        val document:PDDocument = context.contentResolver.openInputStream(data).use { inputStream ->
            PDDocument.load(inputStream)
        }?:run{
            showToast(R.string.failed, Toast.LENGTH_LONG)
            return
        }

        //get info
        val info = document.documentInformation
        val author = if(info.author.isNullOrBlank()) context.getString(R.string.unknown) else info.author
        val fileName = SafeFile.fromUri(context, data)?.name() ?: context.getString(R.string.unknown)
        val name = fileName.removeSuffix(".pdf").removeSuffix(".PDF")
        val apiName = IMPORT_SOURCE_PDF

        //sanitize info
        val sAuthor = BookDownloader2Helper.sanitizeFilename(author)
        val sName = BookDownloader2Helper.sanitizeFilename(name)
        val id = generateId(apiName, sAuthor, sName)


        //where to save the file
        val fileDir = context.filesDir
        val finalEpubFile = File(File(fileDir, getDirectory(apiName, sAuthor, sName)), LOCAL_EPUB)
        val tempFolder = File(context.cacheDir, "temp_$id")
        val load = EpubResponse(
            url = data.toString(),
            name = sName,
            apiName = apiName,
            author = sAuthor,
            downloadLinks = emptyList(),
            downloadExtractLinks = emptyList(),
        )

        try {

            var chapterCount = 1
            var imageCount = 1
            var pageIdx = 1
            //create sections of N pages
            val pagesPerChapter = 10
            val totalPages = document.numberOfPages
            var currentState = DownloadState.IsDownloading
            //to get text from pdf pages
            val stripper = PDFTextStripperByArea()
            //to create content of the chapter
            val currentChapterText = StringBuilder()


            //if already in progress, get info
            if(tempFolder.exists())
            {
                val xhtmlFiles = tempFolder.listFiles { _, name -> name.endsWith(".xhtml") }
                val imageFiles = tempFolder.listFiles { _, name -> name.endsWith(".jpeg") }
                chapterCount = (xhtmlFiles?.size ?: 0) + 1
                imageCount = (imageFiles?.size ?: 0) + 1
                pageIdx = ((chapterCount - 1) * pagesPerChapter) + 1
            }
            else//create files
                tempFolder.mkdirs()


            setPrefixData(load, apiName, ((totalPages + pagesPerChapter - 1) / pagesPerChapter).toLong().coerceAtLeast(1L), 0L)
            changeDownload(id) {
                state = currentState
                this.progress = chapterCount.toLong() - 1
                this.downloaded = chapterCount.toLong() - 1
            }?.let { createNotification(id, load, it) }


            //prototype of book
            val book = EpubBook().apply {
                metadata.addTitle(sName)
                metadata.addAuthor(Author(sAuthor))
            }

            //Necessary so the system recognizes the existing book
            finalEpubFile.parentFile?.mkdirs()
            if(!finalEpubFile.exists())
            {
                FileOutputStream(finalEpubFile).use{fos->
                    EpubWriter().write(book, fos)
                }
            }

            //init progress
            while (true) {
                //check notification options
                currentState = handleDownloadActions(id, load, currentState)
                if(currentState == DownloadState.IsPaused)
                {
                    delay(200)
                    continue
                }
                else if(currentState == DownloadState.IsStopped)
                    break

                val page = document.getPage(pageIdx - 1)
                val bodyText = pdfPageWithoutHAndF(page,stripper)

                //if there's text, include
                if (bodyText.isNotBlank())
                    currentChapterText.append(bodyText.textToHtmlChapter())

                //get and save images from page
                val resources = page.resources
                for (objName in resources.xObjectNames)
                    if (resources.isImageXObject(objName))//is necessary for correct imageCount
                        processImageObject(
                            resources.getXObject(objName) as PDImageXObject,
                            tempFolder,
                            currentChapterText,
                            apiName, sAuthor, sName,
                            fileDir,
                            imageCount
                        )?.let { imageCount++ }

                // collect N pages as sections to chapters
                if (pageIdx % pagesPerChapter == 0 || pageIdx == totalPages) {
                    val sectionTitle = "${context.getString(R.string.chapter)} $chapterCount"
                    val htmlContent = createHtmlWrapper(sectionTitle, currentChapterText.toString())

                    //add chapter to the book
                    File(tempFolder, "chapter$chapterCount.xhtml").writeText(htmlContent)

                    //change notifications
                    //import still in progress
                    if(pageIdx < totalPages)
                    {
                        changeDownload(id) {
                            this.progress = chapterCount.toLong()
                            this.downloaded = chapterCount.toLong()
                        }?.let {
                            createNotification(id, load, it)
                        }
                    }
                    else //finally
                    {
                        currentState = DownloadState.IsDone
                        break
                    }

                    //next chapter
                    currentChapterText.clear()
                    chapterCount++
                }
                pageIdx++

            }

            if(currentState == DownloadState.IsDone)
            {
                //add imgs to book
                tempFolder.listFiles()?.let{
                    for((i,file) in it.withIndex())
                        if(file.name.contains("img_"))
                        {
                            val res = Resource(file.readBytes(), file.name)
                            book.addResource(res)
                            if(i == 0) book.coverImage =res
                        }
                }

                //use twice, to sort chapters
                tempFolder.listFiles { _, name -> name.startsWith("chapter") }?.sortedBy {
                    it.name.filter{char -> char.isDigit()}.toInt()
                }?.forEach { file->
                    val res = Resource(file.readBytes(), file.name)
                    book.addSection("Chapter ${file.name.replace("chapter", "").replace(".xhtml","")}", res)
                }

                //save all the book
                FileOutputStream(finalEpubFile).use{fos->
                    EpubWriter().write(book, fos)
                }
                setSuffixData(load, apiName)


                changeDownload(id) {
                    state = DownloadState.IsDone
                    this.progress =this.total
                    this.downloaded = this.total
                }?.let { createNotification(id, load, it) }
            }
            else{
                changeDownload(id) {
                    state = DownloadState.IsStopped
                }?.let { createNotification(id, load, it) }
            }
        } catch (t: Throwable) {
            logError(t)
            changeDownload(id) {
                state = DownloadState.IsFailed
            }?.let { createNotification(id, load, it) }
        } finally {
            document.close()
            currentDownloadsMutex.withLock { currentDownloads -= id }
            //delete temp
            tempFolder.deleteRecursively()
        }
    }

    fun preloadPartialImportedPdf(bk: DownloadFragment.DownloadDataLoaded, context:Context)
    {
        try
        {
            val finalBook = File(File(context.filesDir, getDirectory(bk.apiName, bk.author?:"", bk.name)), LOCAL_EPUB)
            if(finalBook.exists()) finalBook.delete()
            val tempFolder = File(context.cacheDir, "temp_${bk.id}")
            val book = EpubBook().apply {
                metadata.addTitle(bk.name)
                metadata.addAuthor(Author(bk.author))
            }
            tempFolder.listFiles()?.let{
                for((i,file) in it.withIndex())
                    if(file.name.contains("img_"))
                    {
                        val res = Resource(file.readBytes(), file.name)
                        book.addResource(res)
                        if(i == 0) book.coverImage = res
                    }
            }

            //use twice, to sort chapters
            tempFolder.listFiles { _, name -> name.startsWith("chapter") }?.sortedBy {
                it.name.filter{char -> char.isDigit()}.toInt()
            }?.forEach { file->
                val res = Resource(file.readBytes(), file.name)
                book.addSection("Chapter ${file.name.replace("chapter", "").replace(".xhtml","")}", res)
            }

            finalBook.parentFile?.mkdirs()
            //save all the book
            FileOutputStream(finalBook).use{fos->
                EpubWriter().write(book, fos)
            }
        }
        catch (t: Throwable)
        {
            logError(t)
        }
    }


    //Import epubs area -----------------------------------------------
    @WorkerThread
    @Throws
    suspend fun downloadWorkThread(data: Uri, context: Context) {
        val filesDir = activity?.filesDir ?: return
        val contentResolver = context.contentResolver
        val fd = contentResolver.openFileDescriptor(data, "r")
            ?: throw ErrorLoadingException("Unable to open file descriptor")
        val zipFile = AndroidZipFile(fd, "")
        val book = EpubReader().readEpubLazy(zipFile, "utf-8")
            ?: throw ErrorLoadingException("No epub found")

        val author = book.metadata.authors.firstOrNull()
            ?.let { "${it.firstname ?: ""} ${it.lastname}".trim() }
        val apiName = IMPORT_SOURCE

        //If it doesn’t have a cover, it’s most likely another file that was converted into EPUB, so all the metadata will be wrong. That’s why I use the file name instead of the metadata.
        val name = if(book.coverImage?.data != null) book.metadata.firstTitle
        else {
            contentResolver.query(data, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst())
                    cursor.getString(nameIndex).substringBeforeLast(".")
                else null
            } ?: book.metadata.firstTitle
        }

        val sApiName = BookDownloader2Helper.sanitizeFilename(apiName)
        val sAuthor = BookDownloader2Helper.sanitizeFilename(author ?: "")
        val sName = BookDownloader2Helper.sanitizeFilename(name)

        val id = generateId(apiName, author, name)

        val load = EpubResponse(
            url = "",
            name = name,
            apiName = apiName,
            downloadLinks = emptyList(),
            downloadExtractLinks = emptyList()
        ).apply {
            this.author = author
        }

        try {
            setPrefixData(load, apiName, 1L, 0L)

            try {
                var coverBytes = book.coverImage?.data

                //This is for epubs imported from PDFs that don’t have correct metadata.
                if (coverBytes == null) {
                    zipFile.entries().asSequence()
                        .find { entry ->
                            entry.size >= MIN_IMAGE_SIZE &&
                                    listOf(".jpg", ".jpeg", ".png").any { entry.name.endsWith(it, ignoreCase = true) }
                        }?.let { entry ->
                            coverBytes = zipFile.getInputStream(entry).use { it.readBytes() }
                        }
                }



                if (coverBytes != null) {
                    // Store the image and override it
                    val filepath = BookDownloader2Helper.getFilenameIMG(sApiName, sAuthor, sName)
                    val posterFilepath =
                        filesDir.toString() + filepath
                    val pFile = File(posterFilepath)
                    pFile.parentFile?.mkdirs()
                    pFile.writeBytes(coverBytes)
                }

            } catch (t: Throwable) {
                logError(t)
            }

            val file =
                File(filesDir.toString() + getDirectory(sApiName, sAuthor, sName), LOCAL_EPUB)

            file.parentFile?.mkdirs()
            file.createNewFile()
            file.writeBytes(
                context.contentResolver.openInputStream(data)?.readBytes()
                    ?: throw IOException("No file found")
            )

            setSuffixData(load, apiName)
            changeDownload(id) {
                state = DownloadState.IsDone
                this.progress = this.total
                this.downloaded = this.total
            }?.let { newProgressState ->
                createNotification(
                    id,
                    load,
                    newProgressState
                )
            }
        } finally {
            currentDownloadsMutex.withLock {
                currentDownloads -= id
            }
        }
    }

    @WorkerThread
    suspend fun downloadWorkThread(load: EpubResponse, api: APIRepository) {
        val filesDir = activity?.filesDir ?: return
        val sApiName = BookDownloader2Helper.sanitizeFilename(api.name)
        val sAuthor = BookDownloader2Helper.sanitizeFilename(load.author ?: "")
        val sName = BookDownloader2Helper.sanitizeFilename(load.name)
        val id = generateId(load, api.name)

        setPrefixData(load, api.name, 1L, 0L)

        try {
            // 1. download the image
            downloadImage(load, sApiName, sAuthor, sName, filesDir)

            val file =
                File(filesDir.toString() + getDirectory(sApiName, sAuthor, sName), LOCAL_EPUB)

            if (file.exists() && file.length() > LOCAL_EPUB_MIN_SIZE) {
                changeDownload(id) {
                    state = DownloadState.IsDone
                }
                return
            }

            if (file.exists()) file.delete()

            changeDownload(id) {
                state = DownloadState.IsDownloading
            }?.let { newProgressState ->
                createNotification(
                    id,
                    load,
                    newProgressState
                )
            }

            var links = ExtractorApi.extract(load.downloadExtractLinks + load.downloadLinks)
            links = links.sortedByDescending { it.kbPerSec }
            //println("links $links")
            for (link in links) {
                // consume any action and wait until not paused
                run {
                    var currentState = DownloadState.IsDownloading
                    while (true) {
                        when (consumeAction(id)) {
                            DownloadActionType.Pause -> {
                                DownloadState.IsPaused
                            }

                            DownloadActionType.Resume -> DownloadState.IsDownloading
                            DownloadActionType.Stop -> DownloadState.IsStopped
                            else -> null
                        }?.let { newState ->
                            // if a new state is consumed then push that data instantly
                            changeDownload(id) {
                                state = newState
                            }?.let { progressState ->
                                createNotification(id, load, progressState)
                            }
                            currentState = newState
                        }
                        if (currentState != DownloadState.IsPaused) {
                            break
                        }
                        delay(200)
                    }

                    if (currentState == DownloadState.IsStopped) {
                        return
                    }
                }

                // download into a file
                val stream = try {
                    link.get().body
                } catch (e: Exception) {
                    delay(api.rateLimitTime + 1000)
                    continue
                }

                val length = stream.contentLength()

                if (length <= LOCAL_EPUB_MIN_SIZE) {
                    delay(api.rateLimitTime + 1000)
                    continue
                }

                val totalBytes = ArrayList<Byte>()
                var progress = 0L
                val startedTime = System.currentTimeMillis()
                file.parentFile?.mkdirs()
                file.createNewFile()
                val size = DEFAULT_BUFFER_SIZE
                var lastUpdatedMs = 0L
                stream.byteStream().buffered(size).iterator().asSequence().chunked(size)
                    .forEach { bytes ->
                        progress += bytes.size
                        totalBytes.addAll(bytes)
                        val total = maxOf(length, progress)
                        val currentTime = System.currentTimeMillis()
                        val totalTimeSoFar = currentTime - startedTime
                        val state = DownloadProgressState(
                            state = DownloadState.IsDownloading,
                            progress = progress,
                            total = total,
                            downloaded = progress,
                            lastUpdatedMs = currentTime,
                            etaMs = maxOf(((totalTimeSoFar * total) / progress) - totalTimeSoFar, 0)
                        )

                        run {
                            var currentState = DownloadState.IsDownloading
                            while (true) {
                                when (consumeAction(id)) {
                                    DownloadActionType.Pause -> DownloadState.IsPaused
                                    DownloadActionType.Resume -> DownloadState.IsDownloading
                                    DownloadActionType.Stop -> DownloadState.IsStopped
                                    else -> null
                                }?.let { newState ->
                                    // if a new state is consumed then push that data instantly
                                    changeDownload(id) {
                                        this.state = newState
                                    }
                                    createNotification(
                                        id,
                                        load,
                                        state.copy(state = newState),
                                        progressInBytes = true
                                    )
                                    currentState = newState
                                }
                                if (currentState != DownloadState.IsPaused) {
                                    break
                                }
                                delay(200)
                            }

                            if (currentState == DownloadState.IsStopped) {
                                return
                            }
                        }

                        // don't spam notifications so only update once per sec
                        if (lastUpdatedMs + 1000 < System.currentTimeMillis()) {
                            lastUpdatedMs = System.currentTimeMillis()
                            createNotification(
                                id,
                                load,
                                state,
                                progressInBytes = true
                            )
                        }
                    }

                file.writeBytes(totalBytes.toByteArray())

                setSuffixData(load, api.name)

                changeDownload(id) {
                    state = DownloadState.IsDone
                    this.progress = this.total
                    this.downloaded = this.total
                }?.let { newProgressState ->
                    createNotification(
                        id,
                        load,
                        newProgressState
                    )
                }

                return
            }

            changeDownload(id) {
                state = DownloadState.IsFailed
            }?.let { newProgressState ->
                createNotification(
                    id,
                    load,
                    newProgressState,
                )
            }
        } finally {
            currentDownloadsMutex.withLock {
                currentDownloads -= id
            }
        }
    }

    @WorkerThread
    private suspend fun downloadImage(
        load: LoadResponse,
        sApiName: String,
        sAuthor: String,
        sName: String,
        filesDir: File
    ) {
        try {
            if (load.posterUrl != null) {
                val filepath = BookDownloader2Helper.getFilenameIMG(sApiName, sAuthor, sName)
                val posterFilepath =
                    filesDir.toString() + filepath
                val pFile = File(posterFilepath)

                val posterUrl = load.posterUrl
                // don't need to redownload the image every time
                if ((!pFile.exists() || getKey<String>(
                        filepath,
                        posterUrl
                    ) != posterUrl) && posterUrl != null
                ) {
                    setKey(filepath, load.posterUrl)
                    val get =
                        MainActivity.app.get(posterUrl, headers = load.posterHeaders ?: mapOf())
                    val bytes = get.okhttpResponse.body.bytes()

                    pFile.parentFile?.mkdirs()
                    pFile.writeBytes(bytes)
                }
            }
        } catch (t: Throwable) {
            logError(t)
            //delay(1000)
        }
    }

    @WorkerThread
    suspend fun downloadWorkThread(load: StreamResponse, api: APIRepository) {
        val id = generateId(load, api.name)
        val desiredStart = (
                getKey<Int>(
                    DOWNLOAD_OFFSET, id.toString(),
                ) ?: 0
                ).coerceIn(0, load.data.size)
        downloadWorkThread(load, api, desiredStart until load.data.size)
    }

    @WorkerThread
    suspend fun downloadWorkThread(
        load: StreamResponse,
        api: APIRepository,
        range: ClosedRange<Int>
    ) {
        val filesDir = activity?.filesDir ?: return
        val sApiName = BookDownloader2Helper.sanitizeFilename(api.name)
        val sAuthor =
            BookDownloader2Helper.sanitizeFilename(load.author ?: "")
        val sName = BookDownloader2Helper.sanitizeFilename(load.name)
        val id = generateId(load, api.name)

        val totalItems = range.endInclusive + 1
        val alreadyDownloaded =
            (filesDir.listFiles()?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
                ?.count { it < range.start } ?: 0).toLong()
        //println("alreadyDownloaded:$alreadyDownloaded")
        //println("totalItems:$totalItems")
        setPrefixData(load, api.name, totalItems.toLong(), alreadyDownloaded)

        var downloadedTotal = 0L // how many successful get requests

        try {
            // 1. download the image
            downloadImage(load, sApiName, sAuthor, sName, filesDir)

            // 2. download the text files
            var currentState = DownloadState.IsDownloading
            var timePerLoadMs = 1000.0

            for (index in range.start..range.endInclusive) {
                val data = load.data.getOrNull(index) ?: continue

                // consume any action and wait until not paused
                while (true) {
                    when (consumeAction(id)) {
                        DownloadActionType.Pause -> {
                            DownloadState.IsPaused
                        }

                        DownloadActionType.Resume -> DownloadState.IsDownloading
                        DownloadActionType.Stop -> DownloadState.IsStopped
                        else -> null
                    }?.let { newState ->
                        // if a new state is consumed then push that data instantly
                        changeDownload(id) {
                            state = newState
                        }?.let { progressState ->
                            createNotification(id, load, progressState)
                        }
                        currentState = newState
                    }
                    if (currentState != DownloadState.IsPaused) {
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
                val hasDownloadedChapter =
                    BookDownloader2Helper.downloadIndividualChapter(filepath, api, data)

                if (hasDownloadedChapter) {
                    downloadedTotal += 1
                } else {
                    currentState = DownloadState.IsFailed
                }

                val processedItems = index - range.start +
                        if (hasDownloadedChapter) {
                            1
                        } else {
                            0
                        }

                val afterDownloadTime = System.currentTimeMillis()
                timePerLoadMs =
                    (afterDownloadTime - beforeDownloadTime) * 0.05 + timePerLoadMs * 0.95 // rolling average

                changeDownload(id) {
                    this.progress = index.toLong() + 1L
                    this.downloaded = processedItems.toLong() + alreadyDownloaded
                    state = currentState
                    etaMs = (timePerLoadMs * (range.endInclusive - index)).toLong()
                }?.let { progressState ->
                    createNotification(id, load, progressState)
                }

                when (currentState) {
                    DownloadState.IsStopped -> return
                    DownloadState.IsFailed -> {
                        // we are only interested in a notification if we failed
                        changeDownload(id) {
                            this.progress = index.toLong() + 1L
                            this.downloaded = processedItems.toLong() + alreadyDownloaded
                            state = currentState
                        }?.let { newProgressState ->
                            createNotification(
                                id,
                                load,
                                newProgressState,
                            )
                        }
                        return
                    }

                    else -> {}
                }
            }

            // finally call it before changeDownload
            if (downloadedTotal > 0) {
                setSuffixData(load, api.name)
            }

            changeDownload(id) {
                this.progress = totalItems.toLong()
                this.downloaded = range.endInclusive + 1 - range.start + alreadyDownloaded
                state = DownloadState.IsDone
            }?.let { progressState ->
                // only notify done if we have actually done some work
                if (downloadedTotal > 0)
                    createNotification(
                        id,
                        load,
                        progressState
                    )
            }
        } catch (t: Throwable) {
            // also set it here in case of exception
            if (downloadedTotal > 0) {
                setSuffixData(load, api.name)
            }

            logError(t)
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
