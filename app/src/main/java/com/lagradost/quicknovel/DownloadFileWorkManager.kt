package com.lagradost.quicknovel

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.ui.download.DownloadViewModel
import com.lagradost.quicknovel.util.Apis
import java.lang.ref.WeakReference

// This is needed to fix downloads, as newer android versions pause network connections in the background
class DownloadFileWorkManager(val context: Context, private val workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    companion object {
        const val DATA = "data"
        const val ID = "id"

        const val ID_REFRESH_DOWNLOADS = "REFRESH_DOWNLOADS"
        const val ID_DOWNLOAD = "ID_DOWNLOAD"


        private var _viewModel: WeakReference<DownloadViewModel> = WeakReference(null)
        var viewModel: DownloadViewModel?
            get() = _viewModel.get()
            set(value) {
                _viewModel = WeakReference(value)
            }

        private var workNumber: Int = 0
        private val workData: HashMap<Int, Any> = hashMapOf()

        // java.lang.IllegalStateException: Data cannot occupy more than 10240 bytes when serialized
        // This stores the actual data for the WorkManager to use
        private fun insertWork(data: Any): Int {
            synchronized(workData) {
                workNumber += 1
                workData[workNumber] = data
                return workNumber
            }
        }

        private fun popWork(key: Int): Any? {
            synchronized(workData) {
                return workData.remove(key)
            }
        }

        fun refreshAll(from: DownloadViewModel, context: Context) {
            viewModel = from

            (WorkManager.getInstance(context)).enqueueUniqueWork(
                ID_REFRESH_DOWNLOADS,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(DownloadFileWorkManager::class.java)
                    .setInputData(
                        Data.Builder()
                            .putString(ID, ID_REFRESH_DOWNLOADS)
                            .build()
                    )
                    .build()
            )
        }

        private fun startDownload(data: Any, context: Context) {
            (WorkManager.getInstance(context)).enqueueUniqueWork(
                ID_DOWNLOAD + System.currentTimeMillis(),
                ExistingWorkPolicy.APPEND,
                OneTimeWorkRequest.Builder(DownloadFileWorkManager::class.java)
                    .setInputData(
                        Data.Builder()
                            .putString(ID, ID_DOWNLOAD)
                            .putInt(DATA, insertWork(data))
                            .build()
                    )
                    .build()
            )
        }

        fun download(
            card: DownloadFragment.DownloadDataLoaded,
            context: Context
        ) {
            startDownload(card, context)
        }

        fun download(
            load: LoadResponse,
            context: Context
        ) {
            if(load.apiName == IMPORT_SOURCE || load.apiName == IMPORT_SOURCE_PDF) {
                return
            }
            startDownload(load, context)
        }
    }

    @WorkerThread
    override suspend fun doWork(): Result {
        val id = this.workerParams.inputData.getString(ID)
        when (id) {
            ID_DOWNLOAD -> {
                when (val data = popWork(this.workerParams.inputData.getInt(DATA, -1))) {
                    is StreamResponse -> {
                        BookDownloader2.downloadWorkThread(data, Apis.getApiFromName(data.apiName))
                    }

                    is EpubResponse -> {
                        BookDownloader2.downloadWorkThread(data, Apis.getApiFromName(data.apiName))
                    }

                    is DownloadFragment.DownloadDataLoaded -> {
                        if(data.apiName == IMPORT_SOURCE_PDF)
                            BookDownloader2.downloadPDFWorkThread(data.source.toUri(), context)
                        else
                            BookDownloader2.downloadWorkThread(data)
                    }

                    else -> return Result.failure()
                }
            }

            ID_REFRESH_DOWNLOADS -> {
                viewModel?.refreshInternal()
            }

            else -> return Result.failure()
        }
        return Result.success()
    }
}