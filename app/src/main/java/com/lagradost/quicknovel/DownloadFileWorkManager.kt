package com.lagradost.quicknovel

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.generateId
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.ui.download.DownloadViewModel
import com.lagradost.quicknovel.util.Apis
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

// This is needed to fix downloads, as newer android versions pause network connections in the background
class DownloadFileWorkManager(val context: Context, private val workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    companion object {
        const val DATA = "data"
        const val ID = "id"

        // Survives process death, unlike the payload in workData
        const val DOWNLOAD_ID = "download_id"

        const val ID_REFRESH_DOWNLOADS = "REFRESH_DOWNLOADS"
        const val ID_REFRESH_READINGPROGRESS = "REFRESH_READINGPROGRESS"
        const val ID_DOWNLOAD = "ID_DOWNLOAD"


        private var _viewModel: WeakReference<DownloadViewModel> = WeakReference(null)
        var viewModel: DownloadViewModel?
            get() = _viewModel.get()
            set(value) {
                _viewModel = WeakReference(value)
            }

        private var workNumber: Int = 0
        private val workData: ConcurrentHashMap<Int, Any> = ConcurrentHashMap()

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

            getWorkerManager(context).enqueueUniqueWork(
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

        fun getWorkerManager(context: Context): WorkManager = try {
            WorkManager.getInstance(context.applicationContext)
        } catch (t: Throwable) {
            logError(t)
            val config = androidx.work.Configuration.Builder().build()
            WorkManager.initialize(context.applicationContext, config)
            WorkManager.getInstance(context.applicationContext)
        }


        fun refreshAllReadingProgress(from: DownloadViewModel, context: Context, currentTab: Int) {
            viewModel = from
            val uniqueWorkName = "${ID_REFRESH_READINGPROGRESS}_$currentTab"
            getWorkerManager(context).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequest.Builder(DownloadFileWorkManager::class.java)
                    .setInputData(
                        Data.Builder()
                            .putString(ID, ID_REFRESH_READINGPROGRESS)
                            .putInt(CURRENT_TAB, currentTab)
                            .build()
                    )
                    .build()
            )
        }

        fun refreshAllReadingProgress(context: Context, currentTab: Int) {
            val uniqueWorkName = "${ID_REFRESH_READINGPROGRESS}_$currentTab"
            getWorkerManager(context).enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequest.Builder(DownloadFileWorkManager::class.java)
                    .setInputData(
                        Data.Builder()
                            .putString(ID, ID_REFRESH_READINGPROGRESS)
                            .putInt(CURRENT_TAB, currentTab)
                            .build()
                    )
                    .build()
            )
        }

        private fun startDownload(data: Any, downloadId: Int, context: Context) {
            getWorkerManager(context).enqueueUniqueWork(
                ID_DOWNLOAD + downloadId, // Only 1 download / worker
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequest.Builder(DownloadFileWorkManager::class.java)
                    .setInputData(
                        Data.Builder()
                            .putString(ID, ID_DOWNLOAD)
                            .putInt(DATA, insertWork(data))
                            .putInt(DOWNLOAD_ID, downloadId)
                            .build()
                    )
                    .build()
            )
        }

        fun download(
            card: DownloadFragment.DownloadDataLoaded,
            context: Context
        ) {
            startDownload(card, card.id, context)
        }

        fun download(
            card: ImmutableSearchResponse,
            context: Context
        ) {
            if (card.isImported) {
                return
            }
            startDownload(card, card.id ?: return, context)
        }

        fun download(
            load: LoadResponse,
            context: Context
        ) {
            if (load.apiName == IMPORT_SOURCE || load.apiName == IMPORT_SOURCE_PDF) {
                return
            }
            startDownload(load, generateId(load, load.apiName), context)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationHelper.buildForegroundNotification(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NotificationHelper.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    /** Without this the worker is stopped after ~10 min and the process is frozen while cached */
    private suspend fun runInForeground() {
        try {
            setForeground(getForegroundInfo())
        } catch (t: Throwable) {
            // ForegroundServiceStartNotAllowedException and friends, the download still runs
            logError(t)
        }
    }

    /** The in memory payload is lost on process death, so rebuild it from the stored keys */
    private suspend fun retryFromStoredData(): Result {
        val downloadId = this.workerParams.inputData.getInt(DOWNLOAD_ID, -1)
        if (downloadId == -1) return Result.failure()

        val stored = getKey<DownloadFragment.DownloadData>(DOWNLOAD_FOLDER, downloadId.toString())
            ?: return Result.failure()

        val api = Apis.getApiFromNameOrNull(stored.apiName) ?: return Result.failure()
        val data = api.load(stored.source, allowCache = false)
        // Without the cap a dead source retries forever, showing a notification on every backoff
        if (data !is com.lagradost.quicknovel.mvvm.Resource.Success)
            return if (runAttemptCount < 3) Result.retry() else Result.failure()

        when (val res = data.value) {
            is EpubResponse -> BookDownloader2.downloadWorkThread(res, api, context)
            is StreamResponse -> BookDownloader2.downloadWorkThread(res, api, context)
        }
        return Result.success()
    }

    @WorkerThread
    override suspend fun doWork(): Result {
        val id = this.workerParams.inputData.getString(ID)
        when (id) {
            ID_DOWNLOAD -> {
                runInForeground()
                when (val data = popWork(this.workerParams.inputData.getInt(DATA, -1))) {
                    is StreamResponse -> {
                        BookDownloader2.downloadWorkThread(
                            data,
                            Apis.getApiFromName(data.apiName),
                            context
                        )
                    }

                    is EpubResponse -> {
                        BookDownloader2.downloadWorkThread(
                            data,
                            Apis.getApiFromName(data.apiName),
                            context
                        )
                    }

                    is ImmutableSearchResponse -> {
                        BookDownloader2.downloadWorkThread(data, context)
                    }

                    is DownloadFragment.DownloadDataLoaded -> {
                        if (data.apiName == IMPORT_SOURCE_PDF)
                            BookDownloader2.downloadPDFWorkThread(data.source.toUri(), context)
                        else
                            BookDownloader2.downloadWorkThread(data, context)
                    }

                    else -> return retryFromStoredData()
                }
            }

            ID_REFRESH_DOWNLOADS -> {
                runInForeground()
                viewModel?.refreshInternal()
            }

            ID_REFRESH_READINGPROGRESS -> {
                val currentTab = this.workerParams.inputData.getInt(CURRENT_TAB, 1)
                viewModel?.setIsLoading(true, currentTab)
                BookDownloader2.getOldDataReadingProgress(currentTab)
                viewModel?.setIsLoading(false, currentTab)
            }

            else -> return Result.failure()
        }

        // no clue why, but returning success instantly freezes the UI for up to 50 frames
        // might be some GC, but this is stupid. I can not figure out the cause given that the
        // app cpu is low af, but "background" goes wild
        delay(500.milliseconds)
        return Result.success()
    }
}
