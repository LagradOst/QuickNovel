package com.lagradost.quicknovel.downloader

import android.content.Context
import android.graphics.RectF
import androidx.core.graphics.scale
import coil3.Bitmap
import com.anggrayudi.storage.StorageFile
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.generateId
import com.lagradost.quicknovel.BookDownloader2Helper.getSafeByteArray
import com.lagradost.quicknovel.downloader.BookDownloader.startNewDownloadOrReturn
import com.lagradost.quicknovel.downloader.BookFiles.migrateAllInformation
import com.lagradost.quicknovel.downloader.BookFiles.removeDownloadedFiles
import com.lagradost.quicknovel.downloader.BookFiles.transferTo
import com.lagradost.quicknovel.downloader.BookFiles.updatePersistentData
import com.lagradost.quicknovel.downloader.BookFiles.waitForAction
import com.lagradost.quicknovel.downloader.BookReader.newBook
import com.lagradost.quicknovel.downloader.BookState.postRemoved
import com.lagradost.quicknovel.downloader.BookState.setDone
import com.lagradost.quicknovel.downloader.BookState.updateDownloadState
import com.lagradost.quicknovel.downloader.BookState.updateState
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.ui.common.ImmutableChapterProgress
import com.lagradost.quicknovel.util.AppUtils.textToHtmlChapter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.ag2s.epublib.domain.EpubBook
import me.ag2s.epublib.domain.Resource
import me.ag2s.epublib.epub.EpubWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.BitSet
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.Duration
import kotlin.time.times

/**
 * PDFs are sorta complicated, and to make the downloader happy with the ID each PDF gets 2 ids
 *
 * uri =(id 1)=> start download ==> get author ==> import epub
 *                        <===(id 2)===/
 *
 * Each download acts as a migration from id 1 -> id 2
 */
object PDFDownloader {
    private const val PDF_MIN_IMAGE_SIZE = 30 * 1024

    /** This is a guard to prevent delete + create at the same time */
    private fun pdfSplitName(name: String): Pair<Int, Int> {
        val range = name.split('_').mapNotNull { it.toIntOrNull() }
        require(range.size == 2)
        return range[0] to range[1]
    }

    suspend fun writeEpubFromPdfFiles(
        handle: BookHandle, writeBook: EpubBook
    ) {
        val files = handle.files
        val chapters =
            files.directoryFile().listFiles { it.name.startsWith("pdf_chapter_") } ?: emptyArray()
        val chaptersWithIndex =
            chapters.map { it to pdfSplitName(it.nameWithoutExtension.removePrefix("pdf_chapter_")) }
        val sortedChapters = chaptersWithIndex.sortedBy { it.second.first }

        var lookingFor = 0
        for (item in sortedChapters) {
            val (file, pair) = item
            val (a, b) = pair
            if (a != lookingFor) {
                continue
            }
            lookingFor = b
            if (waitForAction(handle = handle)) {
                return
            }
            val res = Resource(file.readBytes(), file.name)
            writeBook.addSection(
                "Pages ${a + 1}-${b + 1}", res
            )
        }

        val images =
            files.directoryFile().listFiles { it.name.startsWith("pdf_img_") } ?: emptyArray()

        var smallestImageIndex = Int.MAX_VALUE
        var smallestImageResource: Resource? = null
        var smallestImageFile: File? = null
        for (img in images) {
            if (img.length() < PDF_MIN_IMAGE_SIZE) {
                continue
            }
            val (a, b) = pdfSplitName(img.nameWithoutExtension.removePrefix("pdf_img_"))
            val imageIndex = a * 100000 + b // Pick first image
            val imageResource = Resource(getSafeByteArray(img.readBytes()), img.name)
            writeBook.addResource(imageResource)
            if (imageIndex < smallestImageIndex) {
                smallestImageResource = imageResource
                smallestImageIndex = imageIndex
                smallestImageFile = img
            }
        }
        smallestImageResource?.let { coverImageResource ->
            writeBook.coverImage = coverImageResource
        }
        val posterFile = files.posterFile()
        if (!posterFile.exists()) {
            smallestImageFile?.let { file ->
                transferTo(posterFile, file.inputStream(), handle = handle)
            }
        }
    }

    suspend fun importPdf(
        context: Context,
        file: StorageFile,
        status: BookStatus,
    ) = withContext(Dispatchers.IO) {
        val readStream = file.openInputStream() ?: throw IOException("Unable to open file")
        val response = status.response

        val document = PDDocument.load(readStream)
        require(document != null)

        val info = document.documentInformation
        val author = if (info.author.isNullOrBlank()) response.author else info.author
        val name = if (info.title.isNullOrBlank()) response.name else info.title

        val newResponse = response.copy(
            synopsis = info.subject,
            name = name,
            author = author,
            apiName = IMPORT_SOURCE_PDF,
            id = generateId(apiName = IMPORT_SOURCE_PDF, name = name, author = author),
            /** Comma separated keyword list */
            tags = info.keywords?.split(Regex("\\s*[,;]\\s*"))?.toImmutableList()
        )

        if (newResponse.id == status.id) {
            importPdfWithDirectly(status = status.copy(response = newResponse), document = document)
            return@withContext
        }

        /**
         * Lock both this id, and recursively try to load the inner.
         *
         * This can not create a deadlock, because it will ignore the request
         * if we have a pending request.
         * */
        startNewDownloadOrReturn(
            context = context,
            response = newResponse,
            bookPackage = BookDownloadPackage.PDF(pdfDocument = document),
            preloaded = { newStatus ->
                migrateAllInformation(fromStatus = status, toStatus = newStatus)
                postRemoved(status.id)
            })

        /**
         * In case we did not remove it (because we already download it),
         * then remove this.
         * */
        postRemoved(status.id)
        removeDownloadedFiles(status)
        return@withContext
    }

    suspend fun importPdfWithDirectly(
        status: BookStatus,
        document: PDDocument,
    ) = withContext(Dispatchers.IO) {
        val response = status.response
        val handle = status.handle

        val timeOfChapterDownloaded = System.currentTimeMillis()
        updateState(handle.id) {
            copy(
                timeOfChapterDownloaded = timeOfChapterDownloaded,
                timeOfCached = handle.created,
                downloadState = downloadState?.copy(downloadStatus = BookDownloadStatus.Downloading)
            )
        }
        updatePersistentData(
            response = response,
            id = handle.id,
            timeOfChapterDownloaded = timeOfChapterDownloaded,
            timeOfCached = handle.created
        )

        val pagesPerChapter = 10
        val stripper = PDFTextStripperByArea()
        val files = handle.files
        val currentChapterText = StringBuilder()
        val pages = document.pages
        val totalPages = pages.count
        var startingPage = 0

        /** We want to have a fast startup, so we skip pages here if they already exist */
        val existingSkips =
            files.directoryFile().listFiles { it.name.startsWith("pdf_chapter_") } ?: emptyArray()
        val skipPage = BitSet()
        for (file in existingSkips) {
            val (a, b) = pdfSplitName(file.nameWithoutExtension.removePrefix("pdf_chapter_"))
            skipPage.set(a, b)
        }

        var totalTime = Duration.ZERO
        var totalProcessed = 0
        for ((pageIndex, page) in pages.withIndex()) {
            val start = monotonicNow()
            updateDownloadState(handle.id) {
                copy(
                    chapterProgress = ImmutableChapterProgress(
                        progress = pageIndex,
                        total = totalPages
                    ),
                )
            }

            if (waitForAction(handle = handle)) {
                return@withContext
            }

            if (skipPage.get(pageIndex)) {
                /** Just in case we change the size between runs */
                if (currentChapterText.isNotEmpty()) {
                    if (transferTo(
                            target = files.pdfChapter(start = startingPage, end = pageIndex),
                            readStream = currentChapterText.toString().byteInputStream(),
                            handle = handle
                        )
                    ) {
                        return@withContext
                    }
                    currentChapterText.clear()
                }
                startingPage = pageIndex + 1
                continue
            }

            if ((pageIndex + 1) % pagesPerChapter == 0) {
                if (transferTo(
                        target = files.pdfChapter(start = startingPage, end = pageIndex),
                        readStream = currentChapterText.toString().byteInputStream(),
                        handle = handle
                    )
                ) {
                    return@withContext
                }
                currentChapterText.clear()
                startingPage = pageIndex
            }

            val bodyText = pdfPageWithoutHAndF(page, stripper)
            if (bodyText.isNotBlank()) {
                currentChapterText.append(bodyText.textToHtmlChapter())
            }

            val resources = page.resources
            files.directoryFile().mkdirs()
            for ((objIndex, objName) in resources.xObjectNames.withIndex()) {
                if (waitForAction(handle = handle)) {
                    return@withContext
                }
                if (!resources.isImageXObject(objName)) {
                    continue
                }
                val imageXObject = resources.getXObject(objName) as PDImageXObject
                val bitmap = imageXObject.image ?: continue
                if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
                    continue
                }
                val imageFile = files.pdfImage(pageIndex, objIndex)
                val insertedText =
                    "<div style='text-align:center;'><img src=\"${imageFile.name}\" style='max-width:100%;'/></div>"
                if (imageFile.exists()) {
                    currentChapterText.append(insertedText)
                    continue
                }
                val scaledBitmap = scaleBitmap(bitmap = bitmap, maxSize = 1200)
                imageFile.createNewFile()
                scaledBitmap.compress(
                    android.graphics.Bitmap.CompressFormat.JPEG, 85, imageFile.outputStream()
                )
                currentChapterText.append(insertedText)
            }
            val time = monotonicNow() - start
            totalProcessed += 1
            totalTime += time
            val avgTime = time * 0.2 + (totalTime / totalProcessed)
            val remain = totalPages - pageIndex - 1
            val estimateDownloadTime = remain * avgTime
            updateDownloadState(id = handle.id) {
                copy(estimateDownloadTime = estimateDownloadTime)
            }
        }

        if (currentChapterText.isNotEmpty()) {
            if (transferTo(
                    target = files.pdfChapter(start = startingPage, end = totalPages),
                    readStream = currentChapterText.toString().byteInputStream(),
                    handle = handle
                )
            ) {
                return@withContext
            }
        }

        updateDownloadState(handle.id) {
            copy(
                chapterProgress = ImmutableChapterProgress(
                    progress = totalPages,
                    total = totalPages
                ),
            )
        }

        val writeBook = newBook(response = response, offset = 0)

        writeEpubFromPdfFiles(handle = handle, writeBook = writeBook)

        if (waitForAction(handle = handle)) {
            return@withContext
        }

        val tmp = files.tmpFile(".epub")
        FileOutputStream(tmp).use { fos ->
            EpubWriter().write(writeBook, fos)
        }

        if (transferTo(
                target = files.localEpubFile(), readStream = tmp.inputStream(), handle = handle
            )
        ) {
            return@withContext
        }

        /** Clean up after us, to ensure we do not crash another thread that is pre-generating, we lock it */
        BookFiles.readDeleteFileLock.withLock {
            files.directoryFile().listFiles { file -> file.startsWith("pdf_") }?.forEach { file ->
                safe {
                    file.delete()
                }
            }
        }

        updateDownloadState(handle.id) {
            copy(downloadedChapters = BitSet().apply { set(0) })
        }
        setDone(response = response, handle = handle)
    }

    fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val shouldScale = bitmap.width > maxSize || bitmap.height > maxSize

        return if (shouldScale) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val targetW = if (ratio > 1) maxSize else (maxSize * ratio).toInt()
            val targetH = if (ratio > 1) (maxSize / ratio).toInt() else maxSize

            bitmap.scale(targetW, targetH).also {
                bitmap.recycle()
            }
        } else {
            bitmap
        }
    }

    fun pdfPageWithoutHAndF(page: PDPage, stripper: PDFTextStripperByArea): String {
        //try to delete footer and header
        val percentajeToDelete = 0.03f//3%
        val mediaBox = page.mediaBox
        val fullHeight = mediaBox.height
        val marginHeight = fullHeight * percentajeToDelete
        val bodyArea = RectF(
            mediaBox.lowerLeftX, marginHeight, mediaBox.upperRightX, fullHeight - (marginHeight * 2)
        )
        stripper.addRegion("body", bodyArea)
        stripper.extractRegions(page)
        return stripper.getTextForRegion("body")
    }
}