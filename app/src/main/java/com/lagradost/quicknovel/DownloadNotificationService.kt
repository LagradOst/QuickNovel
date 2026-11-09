package com.lagradost.quicknovel

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.lagradost.quicknovel.downloader.BookAction
import com.lagradost.quicknovel.downloader.BookNotifications

class DownloadNotificationService : Service() {
    /*override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
    }*/

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return START_NOT_STICKY
        }

        val id = intent.getIntExtra("id", -1)
        if (id == -1) {
            return START_STICKY
        }
        val type = intent.getIntExtra("type", -1)
        val action = when (type) {
            BookAction.Resume.ordinal -> BookAction.Resume
            BookAction.Pause.ordinal -> BookAction.Pause
            BookAction.Stop.ordinal -> BookAction.Stop
            else -> return START_STICKY
        }
        BookNotifications.notify(id, action)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}