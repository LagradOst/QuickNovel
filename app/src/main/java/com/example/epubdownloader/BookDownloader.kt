package com.example.epubdownloader

import android.app.IntentService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import com.example.epubdownloader.BookDownloader.cachedNotifications
import com.example.epubdownloader.BookDownloader.createNotification
import com.example.epubdownloader.BookDownloader.updateDownload
import java.io.File
import java.lang.Exception
import java.lang.Thread.sleep

const val UPDATE_TIME = 1000
const val CHANNEL_ID = "epubdownloader.general"
const val CHANNEL_NAME = "Downloads"
const val CHANNEL_DESCRIPT = "The download notification channel"

// USED TO STOP, CANCEL AND RESUME FROM ACTION IN NOTIFICATION
class DownloadService : IntentService("DownloadService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val id = intent.getIntExtra("id", -1)
            val type = intent.getStringExtra("type")
            if (id != -1 && type != null) {
                val state = when (type) {
                    "resume" -> BookDownloader.DownloadType.IsDownloading
                    "pause" -> BookDownloader.DownloadType.IsPaused
                    "stop" -> BookDownloader.DownloadType.IsStopped
                    else -> BookDownloader.DownloadType.IsDownloading
                }
                updateDownload(id, state)
            }
        }
    }
}

object BookDownloader {
    data class DownloadResponse(
        val progress: Int,
        val total: Int,
        val id: Int,
    )

    data class DownloadNotification(
        val progress: Int,
        val total: Int,
        val id: Int,
        val ETA: String,
        val state: DownloadType,
    )

    enum class DownloadActionType {
        Pause,
        Resume,
        Stop,
    }

    enum class DownloadType {
        IsPaused,
        IsDownloading,
        IsDone,
        IsFailed,
        IsStopped,
    }

    private const val reservedChars = "|\\?*<\":>+[]/'"
    private fun sanitizeFilename(name: String): String {
        for (c in reservedChars) {
            name.replace(c, ' ')
        }
        return name.replace("  ", " ")
    }

    val fileSeperator = File.separatorChar

    fun getFilename(apiName: String, author: String, name: String, index: Int): String {
        return "$fileSeperator$apiName$fileSeperator$author$fileSeperator$name$fileSeperator$index.txt"
    }

    val cachedBitmaps = hashMapOf<String, Bitmap>()

    fun updateDownload(id: Int, state: DownloadType) {
        if (state == DownloadType.IsStopped || state == DownloadType.IsFailed || state == DownloadType.IsDone) {
            if (isRunning.containsKey(id)) {
                isRunning.remove(id)
            }
        } else {
            isRunning[id] = state
        }

        val not = cachedNotifications[id]
        if (not != null) {
            createNotification(not.id, not.load, not.progress, not.total, not.eta, state)
        }
    }

    fun getImageBitmapFromUrl(url: String): Bitmap? {
        if (cachedBitmaps.containsKey(url)) {
            return cachedBitmaps[url]
        }

        val bitmap = Glide.with(MainActivity.activity)
            .asBitmap()
            .load(url).into(720, 720)
            .get()
        if (bitmap != null) {
            cachedBitmaps[url] = bitmap
        }
        return null
    }

    val isRunning = hashMapOf<Int, DownloadType>()
    val downloadNotification = Event<DownloadNotification>()

    fun generateId(load: LoadResponse, api: MainAPI): Int {
        val sApiname = sanitizeFilename(api.name)
        val sAuthor = if (load.author == null) "" else sanitizeFilename(load.author)
        val sName = sanitizeFilename(load.name)
        return "$sApiname$sAuthor$sName".hashCode()
    }

    fun downloadInfo(load: LoadResponse, api: MainAPI): DownloadResponse? {
        try {
            val sApiname = sanitizeFilename(api.name)
            val sAuthor = if (load.author == null) "" else sanitizeFilename(load.author)
            val sName = sanitizeFilename(load.name)
            val id = "$sApiname$sAuthor$sName".hashCode()

            var count = 0
            for ((index, d) in load.data.withIndex()) {
                val filepath =
                    MainActivity.activity.filesDir.toString() + getFilename(sApiname, sAuthor, sName, index)
                val rFile: File = File(filepath)
                if (rFile.exists()) {
                    count++
                }
            }
            return DownloadResponse(count, load.data.size, id)
        } catch (e: Exception) {
            return null
        }
    }

    fun download(load: LoadResponse, api: MainAPI) {
        try {
            val sApiName = sanitizeFilename(api.name)
            val sAuthor = if (load.author == null) "" else sanitizeFilename(load.author)
            val sName = sanitizeFilename(load.name)

            val id = generateId(load, api)
            if (isRunning.containsKey(id)) return // prevent multidownload of same files

            isRunning[id] = DownloadType.IsDownloading

            var timePerLoad = 1.0

            val total = load.data.size
            for ((index, d) in load.data.withIndex()) {
                if (!isRunning.containsKey(id)) return
                while (isRunning[id] == DownloadType.IsPaused) {
                    sleep(100)
                }
                val lastTime = System.currentTimeMillis() / 1000.0

                val filepath =
                    MainActivity.activity.filesDir.toString() + getFilename(sApiName, sAuthor, sName, index)
                val rFile: File = File(filepath)
                if (rFile.exists()) {
                    continue
                }
                rFile.parentFile.mkdirs()
                //if(rFile.isDirectory) rFile.delete()
                rFile.createNewFile()
                var page: String? = null
                while (page == null) {
                    page = api.loadPage(d.url)
                    if (!isRunning.containsKey(id)) return

                    if (page != null) {
                        rFile.writeText(page)
                    } else {
                        sleep(5000) // ERROR
                    }
                }

                val dloadTime = System.currentTimeMillis() / 1000.0
                timePerLoad = (dloadTime - lastTime) * 0.05 + timePerLoad * 0.95 // rolling avrage
                createAndStoreNotification(NotificationData(id,
                    load,
                    index + 1,
                    total,
                    timePerLoad * (total - index),
                    isRunning[id] ?: DownloadType.IsDownloading))
            }
        } catch (e: Exception) {
            println(e)
        }
    }

    data class NotificationData(
        val id: Int,
        val load: LoadResponse,
        val progress: Int,
        val total: Int,
        val eta: Double,
        val _state: DownloadType,
    )


    val cachedNotifications = hashMapOf<Int, NotificationData>()

    fun createAndStoreNotification(data: NotificationData) {
        cachedNotifications[data.id] = data
        createNotification(data.id, data.load, data.progress, data.total, data.eta, data._state)
    }

    fun createNotification(
        id: Int,
        load: LoadResponse,
        progress: Int,
        total: Int,
        eta: Double,
        _state: DownloadType,
    ) {
        var state = _state
        if (progress >= total) {
            state = DownloadType.IsDone
        }

        val intent = Intent(MainActivity.activity, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(MainActivity.activity, 0, intent, 0)

        val builder = NotificationCompat.Builder(MainActivity.activity, CHANNEL_ID)
            .setAutoCancel(true)
            .setColorized(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(MainActivity.activity.getColor(R.color.colorPrimary))
            .setContentText(
                when (state) {
                    DownloadType.IsDone -> "Download Done - ${load.name}"
                    DownloadType.IsDownloading -> "Downloading ${load.name} - $progress/$total"
                    DownloadType.IsPaused -> "Paused ${load.name} - $progress/$total"
                    DownloadType.IsFailed -> "Error ${load.name} - $progress/$total"
                    DownloadType.IsStopped -> "Stopped ${load.name} - $progress/$total"
                })
            .setSmallIcon(
                when (state) {
                    DownloadType.IsDone -> R.drawable.rddone
                    DownloadType.IsDownloading -> R.drawable.rdload
                    DownloadType.IsPaused -> R.drawable.rdpause
                    DownloadType.IsFailed -> R.drawable.rderror
                    DownloadType.IsStopped -> R.drawable.rderror
                }
            )
            .setContentIntent(pendingIntent)

        if (state == DownloadType.IsDownloading || state == DownloadType.IsPaused) {
            builder.setProgress(total, progress, false)
        }

        var timeformat = ""
        if (state == DownloadType.IsDownloading) { // ETA
            val eta_int = eta.toInt()
            val hours: Int = eta_int / 3600;
            val minutes: Int = (eta_int % 3600) / 60;
            val seconds: Int = eta_int % 60;
            timeformat = String.format("%02d h %02d min %02d s", hours, minutes, seconds);
            if (minutes <= 0 && hours <= 0) {
                timeformat = String.format("%02d s", seconds);
            } else if (hours <= 0) {
                timeformat = String.format("%02d min %02d s", minutes, seconds);
            }

            builder.setSubText("$timeformat remaining")
        }

        val ETA = when (state) {
            DownloadType.IsDone -> "Downloaded"
            DownloadType.IsDownloading -> timeformat
            DownloadType.IsPaused -> "Paused"
            DownloadType.IsFailed -> "Error"
            DownloadType.IsStopped -> "Stopped"
        }

        downloadNotification.invoke(DownloadNotification(progress, total, id, ETA, state))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (load.posterUrl != null) {
                val poster = getImageBitmapFromUrl(load.posterUrl)
                if (poster != null)
                    builder.setLargeIcon(poster)
            }
        }

        if ((state == DownloadType.IsDownloading || state == DownloadType.IsPaused) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actionTypes: MutableList<DownloadActionType> = ArrayList<DownloadActionType>()
            // INIT
            if (state == DownloadType.IsDownloading) {
                actionTypes.add(DownloadActionType.Pause)
                actionTypes.add(DownloadActionType.Stop)
            }

            if (state == DownloadType.IsPaused) {
                actionTypes.add(DownloadActionType.Resume)
                actionTypes.add(DownloadActionType.Stop)
            }

            // ADD ACTIONS
            for ((index, i) in actionTypes.withIndex()) {
                val _resultIntent = Intent(MainActivity.activity, DownloadService::class.java)

                _resultIntent.putExtra(
                    "type", when (i) {
                        DownloadActionType.Resume -> "resume"
                        DownloadActionType.Pause -> "pause"
                        DownloadActionType.Stop -> "stop"
                    }
                )

                _resultIntent.putExtra("id", id)

                val pending: PendingIntent = PendingIntent.getService(
                    MainActivity.activity, 4337 + index + id,
                    _resultIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )

                builder.addAction(
                    NotificationCompat.Action(
                        when (i) {
                            DownloadActionType.Resume -> R.drawable.rdload
                            DownloadActionType.Pause -> R.drawable.rdpause
                            DownloadActionType.Stop -> R.drawable.rderror
                        }, when (i) {
                            DownloadActionType.Resume -> "Resume"
                            DownloadActionType.Pause -> "Pause"
                            DownloadActionType.Stop -> "Stop"
                        }, pending
                    )
                )
            }
        }

        with(NotificationManagerCompat.from(MainActivity.activity)) {
            // notificationId is a unique int for each notification that you must define
            notify(id, builder.build())
        }
    }

    fun init() {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = CHANNEL_NAME //getString(R.string.channel_name)
            val descriptionText = CHANNEL_DESCRIPT//getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                MainActivity.activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

}