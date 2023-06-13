package com.lagradost.quicknovel.services

import android.app.IntentService
import android.content.Intent
import com.lagradost.quicknovel.BookDownloader
import com.lagradost.quicknovel.BookDownloader.updateDownload
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2Helper

class DownloadService : IntentService("DownloadService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val id = intent.getIntExtra("id", -1)
            val type = intent.getStringExtra("type")
            if (id != -1 && type != null) {
                val state = when (type) {
                    "resume" -> BookDownloader.DownloadType.IsDownloading
                    "pause" -> BookDownloader.DownloadType.IsPaused
                    "stop" -> BookDownloader.DownloadType.IsStopped
                    else -> BookDownloader.DownloadType.IsDownloading
                }
                updateDownload(id, state)

                val action = when(type) {
                    "resume" -> BookDownloader.DownloadActionType.Resume
                    "pause" -> BookDownloader.DownloadActionType.Pause
                    "stop" -> BookDownloader.DownloadActionType.Stop
                    else -> null
                }

                if(action != null)
                    BookDownloader2.addPendingAction(id, action)
            }
        }
    }
}