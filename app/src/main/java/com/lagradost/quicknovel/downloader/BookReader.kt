package com.lagradost.quicknovel.downloader

import android.app.Activity
import android.content.Intent
import com.anggrayudi.storage.extension.closeStreamQuietly
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream4.AppSettings
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2.openEpub
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.createQuickStream
import com.lagradost.quicknovel.BookDownloader2Helper.getSafeByteArray
import com.lagradost.quicknovel.BuildConfig
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.DOWNLOAD_OFFSET
import com.lagradost.quicknovel.EPUB_EXPECTED_CONTENT
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.FileHelper
import com.lagradost.quicknovel.QuickStreamData
import com.lagradost.quicknovel.QuickStreamMetaData
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ReadActivity2
import com.lagradost.quicknovel.downloader.BookState.updateState
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName
import com.lagradost.quicknovel.util.cmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.ag2s.epublib.domain.Author
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.MediaTypes
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.epub.EpubWriter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import java.io.File
import java.io.OutputStream
import kotlin.concurrent.read
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

private data class FileChapter(
    val title: String,
    val content: Element,
)

/**
 * To avoid the possibility of 2 books having the same name or not re-generated when switching options
 * we store some extra information, about how it was generated.
 *
 * File length + last modified is used as a fast and easy check for changes.
 *
 * We could add a digest here, but that would invalidate the
 * */
private data class CachedFileOptions(
    @JsonProperty("length")
    val length: Long,
    @JsonProperty("lastModified")
    val lastModified: Long,
    @JsonProperty("chapters")
    val chapters : Int,
    @JsonProperty("options")
    val options: GenerationOptions,
)

private data class GenerationOptions(
    @JsonProperty("authorNotes")
    val authorNotes: Boolean
)

object BookReader {
    @OptIn(ExperimentalContracts::class)
    suspend fun readEpub(
        response: ImmutableSearchResponse,
        allowCache: Boolean = true,
        generating: () -> Unit = {}
    ) {
        contract {
            callsInPlace(generating, InvocationKind.AT_MOST_ONCE)
        }
        withContext(Dispatchers.IO) {
            val activity = activity ?: return@withContext
            try {
                readEpub(
                    activity = activity,
                    response = response,
                    generating = generating,
                    allowCache = allowCache
                )
            } catch (t: Throwable) {
                logError(t)
                if (t.message != null) {
                    showToast(t.message)
                } else {
                    showToast(R.string.error_loading_novel)
                }
            }
        }
    }

    @OptIn(ExperimentalContracts::class)
    suspend fun streamRead(
        response: ImmutableSearchResponse,
        allowCache: Boolean = true,
        generating: () -> Unit = {}
    ) {
        contract {
            callsInPlace(generating, InvocationKind.AT_MOST_ONCE)
        }
        withContext(Dispatchers.IO) {
            val activity = activity ?: return@withContext
            try {
                streamRead(
                    activity = activity,
                    response = response,
                    generating = generating,
                    allowCache = allowCache
                )
            } catch (t: Throwable) {
                logError(t)
                if (t.message != null) {
                    showToast(t.message)
                } else {
                    showToast(R.string.error_loading_novel)
                }
            }
        }
    }

    @OptIn(ExperimentalContracts::class)
    @Throws
    suspend fun streamRead(
        activity: Activity,
        response: ImmutableSearchResponse,
        allowCache: Boolean = true,
        generating: () -> Unit = {}
    ) {
        contract {
            callsInPlace(generating, InvocationKind.AT_MOST_ONCE)
        }

        if (response.isImported) {
            readEpub(activity, response, allowCache, generating)
            return
        }
        generating.invoke()

        val api = getApiFromName(response.apiName)
        val newResponse = if (response.loadData == null) {
            api.loadResult(response.url, allowCache = allowCache).getOrThrow()
        } else {
            response
        }

        require(newResponse.loadData != null)
        require(newResponse.id != null)

        val loadData = newResponse.loadData

        if (loadData.chapters == null) {
            /**
             * As we want to await the download to finish before reading,
             * we download it directly, instead of using the work manager
             * */
            BookDownloader.startNewDownloadOrReturn(
                context = activity,
                response = newResponse,
                bookPackage = BookDownloadPackage.Nothing,
                preloaded = {}
            )
            readEpub(activity, newResponse, allowCache, generating = { })
        } else {
            streamReadChapters(activity = activity, response = newResponse)
        }
    }

    @Throws
    fun streamReadChapters(activity: Activity, response: ImmutableSearchResponse) {
        require(response.loadData?.chapters != null)
        val uri =
            activity.createQuickStream(
                QuickStreamData(
                    QuickStreamMetaData(
                        response.author,
                        response.name,
                        response.apiName,
                    ),
                    response.posterUrl,
                    response.loadData.chapters.sortedBy { it.index }.map { data ->
                        ChapterData(
                            name = data.name,
                            url = data.url,
                            dateOfRelease = data.dateOfRelease,
                            views = data.views
                        )
                    }.toMutableList()
                )
            )
        require(uri != null)
        val myIntent = Intent(activity, ReadActivity2::class.java)
        myIntent.setDataAndType(uri, "quickstream")
        activity.startActivity(myIntent)
        ImmutableSearchResponse.addToHistory(response = response)
    }

    @OptIn(ExperimentalContracts::class)
    @Throws
    private suspend fun readEpub(
        activity: Activity,
        response: ImmutableSearchResponse,
        allowCache: Boolean,
        generating: () -> Unit = {},
    ) {
        contract {
            callsInPlace(generating, InvocationKind.AT_MOST_ONCE)
        }

        ImmutableSearchResponse.addToHistory(response = response)
        val id = response.id ?: throw IllegalArgumentException("Response needs id")

        val settings = AppSettings(activity)
        val options = GenerationOptions(authorNotes = settings.reader.authorNotes.get())

        /** We create a dummy handle here, but the features like pause/resume is not used */
        val handle = BookHandle.from(context = activity, response = response, id = id)

        if (!allowCache) {
            generating.invoke()
            generateAndReadEpub(
                activity = activity,
                response = response,
                options = options,
                handle = handle
            )
            return
        }

        val file = FileHelper.epub.openFile(activity, response.name, false)
        val expectedFile = getKey<CachedFileOptions>(EPUB_EXPECTED_CONTENT, id.toString())
        val uri = file?.uri
        val currentLength = file?.length

        val internalEpub = handle.files.localEpubFile()

        val downloadedChapters = BookState.downloadStatus(id)?.downloadState?.totalDownloadedAfterOffset ?: 1

        /**
         * Re-generate the file if anything has changed,
         * or if we have an internal epub that not matching (like a partial epub)
         * */
        if (uri == null
            || expectedFile == null
            || expectedFile.options != options
            || expectedFile.length != currentLength
            || expectedFile.lastModified != file.lastModified
            || expectedFile.chapters != downloadedChapters
            || currentLength <= 0
            || (internalEpub.exists() && internalEpub.length() != currentLength)
        ) {
            generating.invoke()
            generateAndReadEpub(
                activity = activity,
                response = response,
                options = options,
                handle = handle
            )
        } else {
            openEpub(activity, uri)
        }
    }

    @Throws
    private suspend fun generateAndReadEpub(
        activity: Activity,
        response: ImmutableSearchResponse,
        options: GenerationOptions,
        handle: BookHandle,
    ) = withContext(Dispatchers.IO) {
        val file = FileHelper.epub.createFile(activity, response.name)
            ?: throw ErrorLoadingException("Unable to create file")
        val writeStream = file.openOutputStream(append = false)
            ?: throw ErrorLoadingException("Unable to open input stream")

        try {
            /**
             * Lock for reading to prevent someone
             * from deleting the file while we are generating
             * */
            BookFiles.readDeleteFileLock.withLock {
                turnToEpub(
                    writeStream = writeStream,
                    handle = handle,
                    response = response,
                    options = options,
                )
            }
        } catch (t: Throwable) {
            /** Delete the file if it is partial to avoid reusing it */
            file.delete()
            throw t
        } finally {
            writeStream.closeStreamQuietly()
        }

        setKey(
            EPUB_EXPECTED_CONTENT,
            response.id.toString(),
            CachedFileOptions(
                options = options,
                /** The stream was closed so this should be safe to do */
                length = file.length,
                lastModified = file.lastModified,
                chapters = ImmutableSearchResponse.epubSize(handle.id)
            )
        )
        openEpub(activity = activity, uri = file.uri)
    }

    fun newBook(response: ImmutableSearchResponse): EpubBook {
        return EpubBook().apply {
            metadata.addTitle(response.name)
            metadata.addAuthor(Author(response.author))
            metadata.addPublisher("QuickNovel ${BuildConfig.VERSION_NAME}")
            response.tags?.let { tags ->
                metadata.subjects = tags
            }
            response.synopsis?.let { synopsis ->
                metadata.addDescription(synopsis)
            }

            /**
            Add this when we want to add the option to re-import an epub,
            this is currently removed to avoid any reader problems caused by the null name

            metadata.otherProperties[QName("id")] = response.apiName
            metadata.otherProperties[QName("apiName")] = response.apiName
            try {
            val scheme = response.url.toUri().scheme
            if (scheme == "http" || scheme == "https") {
            /**
             * We embed an additional source url so we can track this if we want to re-import it
             * we only accept http(s) sources to avoid leaking sensative information (like file path)
             * */
            metadata.otherProperties[QName("sourceUrl")] = response.url
            }
            } catch (_: Throwable) {
            /** Silent ignore, as the uri parse might have failed */
            }
             */
        }
    }

    @Throws
    private suspend fun turnToEpub(
        writeStream: OutputStream,
        handle: BookHandle,
        response: ImmutableSearchResponse,
        options: GenerationOptions,
    ) = withContext(Dispatchers.IO) {
        val files = handle.files

        /** Fast case, epub already exists, just copy it over */
        val localFile = files.localEpubFile()
        if (localFile.exists()) {
            localFile.inputStream().use { readStream ->
                readStream.copyTo(writeStream)
            }
            writeStream.flush()
            ImmutableSearchResponse.setEpubSize(handle.id, 1)
            updateState(handle.id, notify = false) {
                copy(epubSize = 1)
            }
            return@withContext
        }

        val writeBook = newBook(response)

        /**
         * If we have a PDF that is currently being imported,
         * we still allow it to write an epub directly to the output stream
         * */
        val epubWriter = EpubWriter()
        if (response.apiName == IMPORT_SOURCE_PDF) {
            PDFDownloader.writeEpubFromPdfFiles(handle = handle, writeBook = writeBook)
            epubWriter.write(writeBook, writeStream)
            writeStream.flush()
            ImmutableSearchResponse.setEpubSize(handle.id, 1)
            updateState(handle.id, notify = false) {
                copy(epubSize = 1)
            }
            return@withContext
        }
        /**
         * Otherwise we must have chapter based content
         * */
        val poster = files.posterFile()
        if (poster.exists()) {
            writeBook.coverImage = Resource(getSafeByteArray(poster.readBytes()), poster.name)
        }
        writeBook.addResource(getGeneralCSStyleForEPUBs())

        val startChapter = getKey<Int>(DOWNLOAD_OFFSET, handle.id.toString()) ?: 0

        /**
         * 1. Filter out chapter < start, as we do not want to include e.g. streamed chapters
         * into the generation
         * */
        val chapterFiles = files.directoryFile().listFiles()?.mapNotNull { file ->
            val name = file?.nameWithoutExtension ?: return@mapNotNull null
            /** Avoid leading zeros as that parses to the same number, breaking the generation */
            if (name.startsWith("0") && name != "0") {
                return@mapNotNull null
            }
            val index = name.toIntOrNull() ?: return@mapNotNull null
            if (index < startChapter) {
                return@mapNotNull null
            }
            return@mapNotNull (index to file)
        } ?: throw IllegalStateException("No chapters found")

        val sortedFiles = chapterFiles.sortedBy { (index, _) -> index }

        /**
         * 2. Take a continuous sequence of chapters from the start,
         * as we want to avoid holes
         * */
        var length = 0
        var expectedIndex = startChapter
        for ((index, _) in sortedFiles) {
            if (index != expectedIndex) {
                break
            }
            expectedIndex++
            length++
        }

        if (length == 0) {
            throw IllegalStateException("No chapters found")
        }

        /** 3. Read all these files concurrently to make it faster */
        val processedItems = sortedFiles.take(length).cmap { (index, file) ->
            val chapter = processChapterFile(file = file, options = options) ?: return@cmap null
            val resource = Resource(
                "id$index",
                packageAsXHtml(chapter.title, chapter.content.html()).toByteArray(),
                "chapter$index.html",
                MediaTypes.XHTML
            )
            return@cmap chapter.title to resource
        }

        /** 4. Again, only take a continuous section */
        var processedLength = 0
        for (item in processedItems) {
            if (item == null) {
                break
            }
            processedLength++
            val (title, resource) = item
            writeBook.addSection(title, resource)
        }

        if (processedLength == 0) {
            throw IllegalStateException("No chapters found")
        }

        epubWriter.write(writeBook, writeStream)
        writeStream.flush()
        val writeEpubSize = startChapter + processedLength
        ImmutableSearchResponse.setEpubSize(handle.id, writeEpubSize)
        updateState(handle.id, notify = false) {
            copy(epubSize = writeEpubSize)
        }
    }

    fun packageAsXHtml(title: String, bodyHtml: String): String =
        """<?xml version='1.0' encoding='utf-8'?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"> 
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
    <title>$title</title>
    <link rel="stylesheet" type="text/css" href="style.css"/>
</head>
<body>
    $bodyHtml
</body>
</html>"""

    private val chapterRegex = Regex("Chapter \\d+", RegexOption.IGNORE_CASE)
    private val translatorRegex = Regex("Translator:.*?Editor:.*", RegexOption.IGNORE_CASE)

    private fun processChapterFile(file: File, options: GenerationOptions): FileChapter? {
        if (!file.exists()) {
            return null
        }
        val text = file.readText()
        val firstChar = text.indexOf('\n')
        if (firstChar == -1) {
            file.delete() // Delete invalid chapters to avoid this again
            return null
        }

        val title = text.substring(0, firstChar)
        val data = text.substring(firstChar + 1)
        val document = Jsoup.parse(data)

        document.outputSettings().apply {
            //translate <br> to <br />, <img> to <img... /> etc.
            syntax(Document.OutputSettings.Syntax.xml)
            //translate special characters to their Unicode equivalent in xhtml
            escapeMode(Entities.EscapeMode.xhtml)
        }

        if (!options.authorNotes) {
            document.select("div.qnauthornotecontainer").remove()
        }

        // We do not want to create a nested <body> will cause some readers to fail
        val body: Element = document.selectFirst("body") ?: document

        // Strip the first line if it is useless
        val trimTitle = title.trim()
        for (first in arrayOf("p", "h1", "h2", "h3", "h4", "h5")) {
            val firstText = body.selectFirst(first) ?: continue
            val text = firstText.text().trim()

            // Do not apply filtering on multiline text as that might contain other info
            if (text.contains('\n')) {
                continue
            }

            if (
                text.equals(trimTitle, true)
                || text.matches(chapterRegex)
                || text.matches(translatorRegex)
            ) {
                firstText.remove()
                break
            }
        }

        return FileChapter(title = title, content = body)
    }

    fun getGeneralCSStyleForEPUBs(): Resource {
        val cssContent = """
            body {
              display: block;
              font-size: 1em;
              padding-left: 0;
              padding-right: 0;
              margin: 0 5pt;
            }
            p {
              display: block;
              margin: 1em 0;
            }
            @page {
              margin-bottom: 5pt;
              margin-top: 5pt;
            }
        """.trimIndent()
        return Resource(
            "style.css",
            cssContent.toByteArray(),
            "style.css",
            MediaTypes.CSS
        )
    }
}