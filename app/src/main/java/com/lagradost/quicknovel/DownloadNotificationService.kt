package com.lagradost.quicknovel

import android.app.Service
import android.content.Intent
import android.os.IBinder

class DownloadNotificationService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val id = intent.getIntExtra("id", -1)
            val type = intent.getStringExtra("type")
            if (id != -1 && type != null) {
                val action = when (type) {
                    "resume" -> DownloadActionType.Resume
                    "pause" -> DownloadActionType.Pause
                    "stop" -> DownloadActionType.Stop
                    else -> null
                }

                if (action != null)
                    BookDownloader2.addPendingAction(id, action)
            }
        }
        if (intent == null) return START_NOT_STICKY
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}