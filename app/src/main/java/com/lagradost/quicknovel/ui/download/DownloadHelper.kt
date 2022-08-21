package com.lagradost.quicknovel.ui.download

import android.content.Context
import com.lagradost.quicknovel.BookDownloader
import com.lagradost.quicknovel.BookDownloader.download
import com.lagradost.quicknovel.BookDownloader.updateDownload
import com.lagradost.quicknovel.DOWNLOAD_FOLDER
import com.lagradost.quicknovel.DOWNLOAD_TOTAL
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName
import com.lagradost.quicknovel.util.Coroutines
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.Coroutines.main

const val DEFAULT_SORT = 0
const val ALPHA_SORT = 1
const val REVERSE_ALPHA_SORT = 2
const val DOWNLOADSIZE_SORT = 3
const val REVERSE_DOWNLOADSIZE_SORT = 4
const val DOWNLOADPRECENTAGE_SORT = 5
const val REVERSE_DOWNLOADPRECENTAGE_SORT = 6
const val LAST_ACCES_SORT = 7
const val REVERSE_LAST_ACCES_SORT = 8

object DownloadHelper {
    fun updateDownloadFromResult(
        context: Context,
        res: LoadResponse,
        localId: Int,
        apiName: String,
        pauseOngoing: Boolean = false,
    ) {
        context.setKey(DOWNLOAD_TOTAL,
            localId.toString(),
            res.data.size) // FIX BUG WHEN DOWNLOAD IS OVER TOTAL

        context.setKey(DOWNLOAD_FOLDER, BookDownloader.generateId(res, apiName).toString(),
            DownloadFragment.DownloadData(res.url,
                res.name,
                res.author,
                res.posterUrl,
                res.rating,
                res.peopleVoted,
                res.views,
                res.synopsis,
                res.tags,
                apiName
            ))
        val api = getApiFromName(apiName)
        ioSafe {
            when (if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped) {
                BookDownloader.DownloadType.IsFailed -> context.download(res, api)
                BookDownloader.DownloadType.IsStopped -> context.download(res, api)
                BookDownloader.DownloadType.IsDownloading -> context.updateDownload(localId,
                    if (pauseOngoing) BookDownloader.DownloadType.IsPaused else BookDownloader.DownloadType.IsDownloading)
                BookDownloader.DownloadType.IsPaused -> context.updateDownload(localId,
                    BookDownloader.DownloadType.IsDownloading)
                else -> println("ERROR")
            }
        }
    }

    fun updateDownloadFromCard(
        context: Context,
        card: DownloadFragment.DownloadDataLoaded,
        pauseOngoing: Boolean = false,
    ) {
        Coroutines.main {
            val api = getApiFromName(card.apiName)
            val data = api.load(card.source)

            if (data is Resource.Success) {
                val res = data.value
                updateDownloadFromResult(context, res, card.id, api.name, pauseOngoing)
            }
        }
    }
}