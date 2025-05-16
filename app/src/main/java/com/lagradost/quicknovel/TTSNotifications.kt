package com.lagradost.quicknovel

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.session.MediaButtonReceiver
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.ui.UiText

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

    var mediaSession: MediaSessionCompat? = null

    fun setMediaSession(viewModel: ReadActivityViewModel, book: AbstractBook) {
        val context = viewModel.context ?: return

        val mbrIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_PLAY_PAUSE
        )

        mediaSession = MediaSessionCompat(
            context,
            "TTS",
            ComponentName(context, MediaButtonReceiver::class.java),
            mbrIntent
        ).apply {
            setCallback(
                object : MediaSessionCompat.Callback() {
                    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                        val keyEvent =
                            mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent?
                        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) { // NO DOUBLE SKIP
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    viewModel.pausePlayTTS()
                                }

                                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    viewModel.pauseTTS()
                                }

                                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    viewModel.playTTS()
                                }

                                KeyEvent.KEYCODE_MEDIA_STOP -> {
                                    viewModel.stopTTS()
                                }

                                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                                    viewModel.forwardsTTS()
                                }

                                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                    viewModel.backwardsTTS()
                                }

                                else -> return super.onMediaButtonEvent(mediaButtonEvent)
                            }
                            return true
                        }

                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }
                }
            )

            val mediaMetadata = MediaMetadataCompat.Builder()
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1L).apply {
                    // https://stackoverflow.com/questions/72750099/android-mediastyle-notification-image-largeicon-is-pixilated
                    book.poster()?.let { icon ->
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, icon)
                    }
                    book.author()?.let { author ->
                        putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, author)
                    }
                }
                .build()
            setMetadata(mediaMetadata)
            //setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            //isActive = true
        }
    }

    fun releaseMediaSession() {
        mediaSession?.release()
        mediaSession = null
    }

    fun createNotification(
        title: String,
        chapter: UiText,
        icon: Bitmap?,
        status: TTSHelper.TTSStatus,
        context: Context?
    ): Notification? {
        if (context == null) return null

        if (status == TTSHelper.TTSStatus.IsStopped) {
            NotificationManagerCompat.from(context).cancel(TTS_NOTIFICATION_ID)
            return null
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

        val cancelButton = MediaButtonReceiver.buildMediaButtonPendingIntent(
            context,
            PlaybackStateCompat.ACTION_STOP
        )
        val style = androidx.media.app.NotificationCompat.MediaStyle()
        mediaSession?.sessionToken?.let { token ->
            style.setShowCancelButton(true).setShowActionsInCompactView(1, 2)
                .setCancelButtonIntent(cancelButton)
                .setMediaSession(token)
        }

        builder.setStyle(style)

        val actionPlay = NotificationCompat.Action(
            R.drawable.ic_baseline_play_arrow_24,
            "Resume",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_PLAY
            )
        )

        val actionStop = NotificationCompat.Action(
            R.drawable.ic_baseline_stop_24,
            "Stop",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_STOP
            )
        )

        val actionPause = NotificationCompat.Action(
            R.drawable.ic_baseline_pause_24,
            "Pause",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_PAUSE
            )
        )

        val actionRewind = NotificationCompat.Action(
            R.drawable.ic_baseline_fast_rewind_24,
            "Rewind",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_REWIND
            )
        )

        val actionFastForward = NotificationCompat.Action(
            R.drawable.ic_baseline_fast_forward_24,
            "Fast Forward",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context,
                PlaybackStateCompat.ACTION_FAST_FORWARD
            )
        )

        when (status) {
            TTSHelper.TTSStatus.IsRunning -> {
                builder.addAction(actionRewind)
                builder.addAction(actionStop)
                builder.addAction(actionPause)
                builder.addAction(actionFastForward)
            }

            TTSHelper.TTSStatus.IsPaused -> {
                builder.addAction(actionRewind)
                builder.addAction(actionStop)
                builder.addAction(actionPlay)
                builder.addAction(actionFastForward)
            }

            else -> {
                // unreachable
            }
        }

        /*val actionTypes: MutableList<TTSHelper.TTSActionType> = ArrayList()

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
                        else -> return null
                    }, when (i) {
                        TTSHelper.TTSActionType.Resume -> "Resume"
                        TTSHelper.TTSActionType.Pause -> "Pause"
                        TTSHelper.TTSActionType.Stop -> "Stop"
                        else -> return null
                    }, pending
                )
            )
        }*/
        return builder.build()
    }

    fun notify(
        title: String,
        chapter: UiText,
        icon: Bitmap?,
        status: TTSHelper.TTSStatus,
        context: Context?
    ) {
        if (context == null) return
        val notification = createNotification(title, chapter, icon, status, context) ?: return

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

                notify(TTS_NOTIFICATION_ID, notification)
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }
}