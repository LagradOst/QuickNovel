package com.lagradost.quicknovel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lagradost.quicknovel.ui.UiText
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.services.TTSPauseService
import java.util.ArrayList

object TTSNotifications {
    private const val TTS_CHANNEL_ID = "QuickNovelTTS"
    const val TTS_NOTIFICATION_ID = 133742

    private var hasCreateedNotificationChannel = false

    private fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.text_to_speech)
            val descriptionText = context.getString(R.string.text_to_speech_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                TTS_CHANNEL_ID,
                name,
                importance
            ).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notify(
        title: String,
        chapter: UiText,
        icon: Bitmap?,
        status: TTSHelper.TTSStatus,
        context: Context?
    ) {
        if (context == null) return

        if (status == TTSHelper.TTSStatus.IsStopped) {
            NotificationManagerCompat.from(context).cancel(TTS_NOTIFICATION_ID)
            return
        }

        if (!hasCreateedNotificationChannel) {
            hasCreateedNotificationChannel = true
            createNotificationChannel(context)
        }
        val builder = NotificationCompat.Builder(context, TTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_volume_up_24)
            .setContentTitle(title)
            .setContentText(chapter.asString(context))

            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)

        if (icon != null) builder.setLargeIcon(icon)

        builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle())
        // .setMediaSession(mediaSession?.sessionToken))

        val actionTypes: MutableList<TTSHelper.TTSActionType> = ArrayList()

        if (status == TTSHelper.TTSStatus.IsPaused) {
            actionTypes.add(TTSHelper.TTSActionType.Resume)
        } else if (status == TTSHelper.TTSStatus.IsRunning) {
            actionTypes.add(TTSHelper.TTSActionType.Pause)
        }
        actionTypes.add(TTSHelper.TTSActionType.Stop)

        for ((index, i) in actionTypes.withIndex()) {
            val resultIntent = Intent(context, TTSPauseService::class.java)
            resultIntent.putExtra("id", i.ordinal)

            val pending: PendingIntent = PendingIntent.getService(
                context, 3337 + index,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_MUTABLE else 0
            )

            builder.addAction(
                NotificationCompat.Action(
                    when (i) {
                        TTSHelper.TTSActionType.Resume -> R.drawable.ic_baseline_play_arrow_24
                        TTSHelper.TTSActionType.Pause -> R.drawable.ic_baseline_pause_24
                        TTSHelper.TTSActionType.Stop -> R.drawable.ic_baseline_stop_24
                        else -> return
                    }, when (i) {
                        TTSHelper.TTSActionType.Resume -> "Resume"
                        TTSHelper.TTSActionType.Pause -> "Pause"
                        TTSHelper.TTSActionType.Stop -> "Stop"
                        else -> return
                    }, pending
                )
            )
        }

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }

                notify(TTS_NOTIFICATION_ID, builder.build())
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }
}