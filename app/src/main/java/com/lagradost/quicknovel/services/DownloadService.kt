package com.lagradost.quicknovel.services

import android.app.IntentService
import android.content.Intent
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.DownloadActionType

class DownloadService : IntentService("DownloadService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val id = intent.getIntExtra("id", -1)
            val type = intent.getStringExtra("type")
            if (id != -1 && type != null) {
                val action = when(type) {
                    "resume" -> DownloadActionType.Resume
                    "pause" -> DownloadActionType.Pause
                    "stop" -> DownloadActionType.Stop
                    else -> null
                }

                if(action != null)
                    BookDownloader2.addPendingAction(id, action)
            }
        }
    }
}