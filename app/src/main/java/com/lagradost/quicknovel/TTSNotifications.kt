package com.lagradost.quicknovel

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lagradost.quicknovel.services.TTSPauseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.ArrayList

object TTSNotifications {
    const val TTS_CHANNEL_ID = "QuickNovelTTS"
    const val TTS_NOTIFICATION_ID = 133742

    var hasCreateedNotificationChannel = false

    private fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Text To Speech"//getString(R.string.channel_name)
            val descriptionText =
                "The TTS notification channel" //getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(com.lagradost.quicknovel.TTS_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notify(title : String, chapter : String, icon: Bitmap?, status : TTSHelper.TTSStatus, context: Context ) {
        if(!hasCreateedNotificationChannel) {
            hasCreateedNotificationChannel = true
            createNotificationChannel(context)
        }
        val builder = NotificationCompat.Builder(context, TTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_baseline_volume_up_24)
            .setContentTitle(title)
            .setContentText(chapter)

            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)

        if (icon != null) builder.setLargeIcon(icon)

        builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle())
        // .setMediaSession(mediaSession?.sessionToken))

        val actionTypes: MutableList<TTSActionType> = ArrayList()

        if (status == TTSHelper.TTSStatus.IsPaused) {
            actionTypes.add(TTSActionType.Resume)
        } else if (status == TTSHelper.TTSStatus.IsRunning) {
            actionTypes.add(TTSActionType.Pause)
        }
        actionTypes.add(TTSActionType.Stop)

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
                        TTSActionType.Resume -> R.drawable.ic_baseline_play_arrow_24
                        TTSActionType.Pause -> R.drawable.ic_baseline_pause_24
                        TTSActionType.Stop -> R.drawable.ic_baseline_stop_24
                    }, when (i) {
                        TTSActionType.Resume -> "Resume"
                        TTSActionType.Pause -> "Pause"
                        TTSActionType.Stop -> "Stop"
                    }, pending
                )
            )
        }

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(TTS_NOTIFICATION_ID, builder.build())
        }
    }
}