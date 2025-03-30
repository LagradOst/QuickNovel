package com.lagradost.quicknovel

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import java.lang.ref.WeakReference

class TTSNotificationService : Service() {
    companion object {
        private var _viewModel: WeakReference<ReadActivityViewModel> = WeakReference(null)

        private var isRunning: Mutex = Mutex()
        private var currentJob: Job? = null
        var viewModel: ReadActivityViewModel?
            get() = _viewModel.get()
            set(value) {
                _viewModel = WeakReference(value)
            }

        suspend fun start(viewModel: ReadActivityViewModel, ctx: Context) {
            currentJob?.cancel()
            isRunning.lock()

            this.viewModel = viewModel
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, TTSNotificationService::class.java)
            )
        }
        //fun stop(ctx: Context) {
        //    ctx.stopService(Intent(ctx, TTSNotificationService::class.java))
        //}

        //private fun isRunning(ctx: Context): Boolean =
        //    ctx.isServiceRunning(TTSNotificationService::class.java)
    }

    override fun onCreate() {
        currentJob?.cancel()
        currentJob = null

        viewModel?.let { viewModel ->
            TTSNotifications.setMediaSession(viewModel)
            val notification = TTSNotifications.createNotification(
                viewModel.book.title(),
                txt(""),
                viewModel.book.poster(),
                viewModel.currentTTSStatus,
                viewModel.context
            )

            try {
                startForeground(TTSNotifications.TTS_NOTIFICATION_ID, notification)
            } catch (t: Throwable) {
                showToast(t.toString())
                stopSelf()
                return@let
            }

            currentJob = ioSafe {
                try {
                    viewModel.startTTSThread()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                } catch (t: Throwable) {
                    // just in case
                    logError(t)
                } finally {
                    stopSelf()
                }
            }
        }

        super.onCreate()
    }

    override fun onDestroy() {
        try {
            // close notification
            TTSNotifications.releaseMediaSession()
            currentJob?.cancel()
            viewModel = null
            super.onDestroy()
        } catch (t : Throwable) {
            logError(t)
        } finally {
            isRunning.unlock()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TTSNotifications.mediaSession?.let {
            MediaButtonReceiver.handleIntent(it, intent)
        }
        if (intent == null) return START_NOT_STICKY
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}