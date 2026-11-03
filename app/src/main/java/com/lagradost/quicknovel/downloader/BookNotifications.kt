package com.lagradost.quicknovel.downloader

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter.formatFileSize
import androidx.annotation.WorkerThread
import androidx.compose.runtime.Immutable
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.lagradost.quicknovel.DownloadNotificationService
import com.lagradost.quicknovel.ImageDownloader.getImageBitmapFromUrl
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.NotificationHelper.CHANNEL_ID
import com.lagradost.quicknovel.NotificationHelper.createNotificationChannel
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.downloader.BookState.currentStatusMap
import com.lagradost.quicknovel.mvvm.safeAsync
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.util.PriorityQueue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Immutable
private data class BookNotificationSchedule(
    /** null = clear */
    val response: ImmutableSearchResponse?,
    val id: Int,
    val postTime: MonotonicInstant = monotonicNow(),
) : Comparable<BookNotificationSchedule> {
    override fun compareTo(other: BookNotificationSchedule): Int {
        return postTime.compareTo(other.postTime)
    }
}

/**
 * Helper to solve some problems with the android notifications.
 *
 * Android notifications have a wonky property where we can not DDOS the OS, as it discards
 * newer notifications. So we have a timeout to always make sure notifications are up to date,
 * without being lost.
 * */
object BookNotifications {
    /**
     *  Posts the notification to the notification worker, where null = clear it
     * */
    fun postNotification(response: ImmutableSearchResponse?, id: Int) {
        notificationChannel.trySend(BookNotificationSchedule(response = response, id = id))
    }

    fun notify(id: Int, action: BookAction) {
        val status = currentStatusMap[id] ?: return
        status.handle.notification.trySend(action)
    }

    private fun hasNotificationAccess(context: Context): Boolean {
        return !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED)
    }

    private val notificationChannel = Channel<BookNotificationSchedule>(
        /** We want to avoid dropped messages if we e.g. download an image */
        capacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val notificationMutex = Mutex()

    /** An always active worker to listen to notifications */
    @WorkerThread
    @Throws
    suspend fun notificationThread(context: Context) {
        if (!hasNotificationAccess(context)) return
        if (!notificationMutex.tryLock()) return
        context.createNotificationChannel()
        try {
            val pendingQueue = PriorityQueue<BookNotificationSchedule>()
            val lastPostedNotificationTime = HashMap<Int, MonotonicInstant>()
            val lastState = HashMap<Int, ImmutableSearchResponse?>()
            val timeout = 300.milliseconds

            while (true) {
                val topItem = pendingQueue.poll()
                if (topItem == null) {
                    val next = notificationChannel.receive()
                    lastState[next.id] = next.response
                    pendingQueue += next
                    continue
                }

                /** Not up-to-date information, remove it */
                if (topItem.response != lastState[topItem.id]) {
                    continue
                }

                val timeSinceLastPost =
                    lastPostedNotificationTime[topItem.id]?.let { oldTime ->
                        monotonicNow() - oldTime
                    } ?: Duration.INFINITE

                /** Are we too early? */
                if (timeSinceLastPost < timeout) {
                    val added = timeout - timeSinceLastPost

                    /** Move it backward to make room for others */
                    val nextTry = monotonicNow() + added
                    pendingQueue += topItem.copy(postTime = nextTry)

                    /** If we have pending ones, then skip waiting */
                    pendingQueue.peek()?.postTime?.let { peekPostTime ->
                        if (peekPostTime < nextTry) {
                            continue
                        }
                    }

                    /** Wait for any other notifications */
                    val next = withTimeoutOrNull(added) {
                        notificationChannel.receive()
                    }
                    if (next != null) {
                        lastState[next.id] = next.response
                        pendingQueue += next
                    }
                    continue
                }

                /** We have up-to-date information that we can post */
                lastPostedNotificationTime[topItem.id] = monotonicNow()
                safeAsync {
                    postAndroidNotification(
                        context = context,
                        id = topItem.id,
                        response = topItem.response
                    )
                }
            }
        } finally {
            notificationMutex.unlock()
        }
    }

    private suspend fun postAndroidNotification(
        context: Context,
        id: Int,
        response: ImmutableSearchResponse?
    ) {
        if (ActivityCompat.checkSelfPermission(
                context,
                POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (response == null) {
            NotificationManagerCompat.from(context).cancel(id)
            return
        }

        val downloadState = response.downloadState ?: return

        val pendingIntent: PendingIntent? = if (response.url.isBlank()) {
            null
        } else {
            val intent = Intent(context, MainActivity::class.java).apply {
                data = response.url.toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            PendingIntent.getActivity(
                context, 0, intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setAutoCancel(true)
            .setColorized(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(context.colorFromAttribute(R.attr.colorPrimary))
            .setContentTitle(response.name)
            .setContentIntent(pendingIntent)

        downloadState.estimateDownloadTimeFormat?.let { duration ->
            builder.setSubText("$duration remaining")
        }

        val extraText =
            when (downloadState.downloadStatus) {
                BookDownloadStatus.Pending, BookDownloadStatus.Downloading -> {
                    if (downloadState.downloadedBytes > 0) {
                        val div = 1024
                        builder.setProgress(
                            maxOf((downloadState.totalBytes / div).toInt(), 1),
                            (downloadState.downloadedBytes / div).toInt(),
                            false
                        )

                        "${
                            formatFileSize(
                                context,
                                downloadState.downloadedBytes
                            )
                        } / ${formatFileSize(context, downloadState.totalBytes)}"
                    } else if (downloadState.chapterProgress != null) {
                        builder.setProgress(
                            downloadState.chapterProgress.total,
                            downloadState.chapterProgress.progress,
                            false
                        )

                        "${downloadState.chapterProgress.progress} / ${downloadState.chapterProgress.total}"
                    } else {
                        builder.setProgress(
                            1, 0,
                            true
                        )
                        ""
                    }
                }

                else -> {
                    ""
                }
            }

        val statusText = when (downloadState.downloadStatus) {
            BookDownloadStatus.Done -> "Download Done"
            BookDownloadStatus.Downloading -> "Downloading $extraText"
            BookDownloadStatus.Paused -> "Paused $extraText"
            BookDownloadStatus.Failed -> "Error ${downloadState.error?.message ?: extraText}"
            BookDownloadStatus.Stopped -> "Stopped $extraText"
            else -> ""
        }
        builder.setContentText(statusText)
        builder.setSmallIcon(
            when (downloadState.downloadStatus) {
                BookDownloadStatus.Done -> R.drawable.rddone
                BookDownloadStatus.Downloading, BookDownloadStatus.Idle, BookDownloadStatus.Pending -> R.drawable.rdload
                BookDownloadStatus.Paused -> R.drawable.rdpause
                else -> R.drawable.rderror
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && response.posterUrl != null) {
            context.getImageBitmapFromUrl(
                response.posterUrl,
                response.posterHeaders
            )
                ?.let { builder.setLargeIcon(it) }
        }

        if (downloadState.downloadStatus == BookDownloadStatus.Downloading || downloadState.downloadStatus == BookDownloadStatus.Paused) {
            val actionTypes = if (downloadState.downloadStatus == BookDownloadStatus.Downloading)
                listOf(BookAction.Pause, BookAction.Stop)
            else
                listOf(BookAction.Resume, BookAction.Stop)

            actionTypes.forEachIndexed { index, action ->
                val resultIntent = Intent(context, DownloadNotificationService::class.java).apply {
                    putExtra("type", action.ordinal)
                    putExtra("id", id)
                }

                val pending: PendingIntent = PendingIntent.getService(
                    context, 4337 + index + id, resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                )

                builder.addAction(
                    NotificationCompat.Action(
                        when (action) {
                            BookAction.Resume -> R.drawable.rdload
                            BookAction.Pause -> R.drawable.rdpause
                            BookAction.Stop -> R.drawable.rderror
                        },
                        action.name, pending
                    )
                )
            }
        }

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}