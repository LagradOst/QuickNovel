package com.lagradost.quicknovel

import android.app.ActivityManager
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
import kotlinx.coroutines.sync.Semaphore
import java.lang.ref.WeakReference

class TTSNotificationService : Service() {
    companion object {
        private var _viewModel: WeakReference<ReadActivityViewModel> = WeakReference(null)

        // we use a binary semaphore to avoid the exception of releasing from a different thread
        private var isRunning: Semaphore = Semaphore(1)
        private var currentJob: Job? = null
        var viewModel: ReadActivityViewModel?
            get() = _viewModel.get()
            set(value) {
                _viewModel = WeakReference(value)
            }

        suspend fun start(viewModel: ReadActivityViewModel, ctx: Context) {
            // if the server is not running, but we somehow did not call release due to android killing it
            // then release a permit
            if ((!isServiceRunning(
                    ctx,
                    TTSNotificationService::class.java
                ) || this._viewModel.get() == null) && isRunning.availablePermits == 0
            ) {
                try {
                    isRunning.release()
                } catch (t: IllegalStateException) {
                    logError(t)
                }
            }

            currentJob?.cancel()
            isRunning.acquire()

            this.viewModel = viewModel
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, TTSNotificationService::class.java)
            )
        }

        private fun isServiceRunning(ctx: Context, service: Class<*>): Boolean =
            try {
                (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(
                    Integer.MAX_VALUE
                ).any { cmp -> service.name == cmp.service.className }
            } catch (t: Throwable) {
                false
            }
    }

    override fun onCreate() {
        currentJob?.cancel()
        currentJob = null

        viewModel?.let { viewModel ->
            TTSNotifications.setMediaSession(viewModel, viewModel.book)

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
        } catch (t: Throwable) {
            logError(t)
        } finally {
            try {
                isRunning.release()
            } catch (t: IllegalStateException) {
                logError(t)
            }
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