package com.lagradost.quicknovel.util

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadNotificationService
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.ImageDownloader.getImageBitmapFromUrl
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute

object GenericNotification {
    const val CHANNEL_ID = "epubdownloader.general"
    const val CHANNEL_NAME = "Downloads"
    const val CHANNEL_DESCRIPT = "The download notification channel"
    private var hasCreatedNotChanel = false

    fun ComponentActivity.requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            val requestPermissionLauncher = this.registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted: Boolean -> println("Notification permission: $isGranted") }
            requestPermissionLauncher.launch(POST_NOTIFICATIONS)
        }
    }

    private fun Context.createNotificationChannel() {
        hasCreatedNotChanel = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Usamos IMPORTANCE_LOW para que el progreso no haga ruido/vibre constantemente
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPT
            }
            val notificationManager: NotificationManager =
                this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun etaToString(etaMs: Long?): String {
        if (etaMs == null) return ""
        val eta = (etaMs / 1000.0).toInt()
        val hours: Int = eta / 3600
        val minutes: Int = (eta % 3600) / 60
        val seconds: Int = eta % 60
        return when {
            hours > 0 -> String.format("%02d h %02d min %02d s", hours, minutes, seconds)
            minutes > 0 -> String.format("%02d min %02d s", minutes, seconds)
            else -> String.format("%02d s", seconds)
        }
    }

    /**
     * @param name Nombre a mostrar (Novela o "App Update")
     * @param posterUrl URL de imagen opcional
     * @param isActionable Si es true, muestra botones de Pausa/Stop (usado en novelas)
     */
    suspend fun createNotification(
        context: Context?,
        source: String,
        id: Int,
        name: String,
        stateProgressState: DownloadProgressState,
        posterUrl: String? = null,
        showNotification: Boolean = true,
        progressInBytes: Boolean = true,
        isActionable: Boolean = false
    ) {
        if (context == null || !showNotification) return
        val state = stateProgressState.state
        val timeFormat = if (state == DownloadState.IsDownloading) etaToString(stateProgressState.etaMs) else ""

        val intent = Intent(context, MainActivity::class.java).apply {
            data = source.toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setAutoCancel(true)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.colorFromAttribute(R.attr.colorPrimary))
            .setContentTitle(name)
            .setContentIntent(pendingIntent)


        val extraText = if (stateProgressState.total > 1) {
            val unit = if (progressInBytes) "Kb" else ""
            val div = if (progressInBytes) 1024 else 1
            "${stateProgressState.progress / div} $unit / ${stateProgressState.total / div} $unit"
        } else ""

        val statusText = when (state) {
            DownloadState.IsDone -> "Download Done"
            DownloadState.IsDownloading -> "Downloading $extraText"
            DownloadState.IsPaused -> "Paused $extraText"
            DownloadState.IsFailed -> "Error $extraText"
            DownloadState.IsStopped -> "Stopped $extraText"
            else -> ""
        }
        builder.setContentText(statusText)

        builder.setSmallIcon(
            when (state) {
                DownloadState.IsDone -> R.drawable.rddone
                DownloadState.IsDownloading -> R.drawable.rdload
                DownloadState.IsPaused -> R.drawable.rdpause
                else -> R.drawable.rderror
            }
        )


        if (state == DownloadState.IsDownloading || state == DownloadState.IsPaused) {
            builder.setProgress(
                stateProgressState.total.toInt(),
                stateProgressState.progress.toInt(),
                stateProgressState.total <= 1
            )
            if (timeFormat.isNotEmpty()) builder.setSubText("$timeFormat remaining")
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && posterUrl != null) {
            context.getImageBitmapFromUrl(posterUrl)?.let { builder.setLargeIcon(it) }
        }


        if (isActionable && (state == DownloadState.IsDownloading || state == DownloadState.IsPaused)) {
            val actionTypes = if (state == DownloadState.IsDownloading)
                listOf(DownloadActionType.Pause, DownloadActionType.Stop)
            else
                listOf(DownloadActionType.Resume, DownloadActionType.Stop)

            actionTypes.forEachIndexed { index, action ->
                val resultIntent = Intent(context, DownloadNotificationService::class.java).apply {
                    putExtra("type", action.name.lowercase())
                    putExtra("id", id)
                }

                val pending: PendingIntent = PendingIntent.getService(
                    context, 4337 + index + id, resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                )

                builder.addAction(
                    NotificationCompat.Action(
                        if (action == DownloadActionType.Resume) R.drawable.rdload else R.drawable.rdpause,
                        action.name, pending
                    )
                )
            }
        }

        if (!hasCreatedNotChanel) {
            context.createNotificationChannel()
        }

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(id, builder.build())
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }
}