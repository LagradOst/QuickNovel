package com.example.epubdownloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import khttp.post
import java.io.File
import java.lang.Exception
import java.lang.Thread.sleep

const val UPDATE_TIME = 1000
const val CHANNEL_ID = "epubdownloader.general"
const val CHANNEL_NAME = "Downloads"
const val CHANNEL_DESCRIPT = "The download notification channel"

const val IS_RUNNING = 1
const val IS_PAUSED = 2

class BookDownloader {
    companion object {
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

        val isRunning = hashMapOf<Int, Int>()

        fun download(load: LoadResponse, api: MainAPI) {
            try {
                val s_apiName = sanitizeFilename(api.name)
                val s_author = if (load.author == null) "" else sanitizeFilename(load.author)
                val s_name = sanitizeFilename(load.name)

                val id = "$s_apiName$s_author$s_name".hashCode()
                if (isRunning.containsKey(id)) return // prevent multidownload of same files

                isRunning[id] = IS_RUNNING

                var timePerLoad = 1.0


                val total = load.data.size
                for ((index, d) in load.data.withIndex()) {
                    if (!isRunning.containsKey(id)) return
                    while (isRunning[id] == IS_PAUSED) {
                        sleep(100)
                    }
                    val lastTime = System.currentTimeMillis() / 1000.0

                    val filepath =
                        MainActivity.activity.filesDir.toString() + getFilename(s_apiName, s_author, s_name, index)
                    val rFile: File = File(filepath)
                    if (rFile.exists()) {
                        continue
                    }

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
                    timePerLoad = (dloadTime - lastTime) * 0.1 + timePerLoad * 0.9 // rolling avrage
                    createNotification(id, load, index + 1, total, timePerLoad * (total - index))
                }
            } catch (e: Exception) {

            }
        }

        fun createNotification(id: Int, load: LoadResponse, progress: Int, total: Int, eta: Double) {
            val intent = Intent(MainActivity.activity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(MainActivity.activity, 0, intent, 0)

            val hours = eta / 3600;
            val minutes = (eta % 3600) / 60;
            val seconds = eta % 60;
            var timeformat = String.format("%02d h %02d min %02d s", hours, minutes, seconds);
            if (minutes <= 0 && hours <= 0) {
                timeformat = String.format("%02d s", seconds);
            } else if (hours <= 0) {
                timeformat = String.format("%02d min %02d s", minutes, seconds);
            }

            val builder = NotificationCompat.Builder(MainActivity.activity, CHANNEL_ID)
                .setProgress(total, progress, false)
                .setAutoCancel(true)
                .setColorized(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setColor(MainActivity.activity.getColor(R.attr.colorPrimary))
                .setContentText(load.name)
                .setSubText("$timeformat remaining")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (load.posterUrl != null) {
                    val poster = getImageBitmapFromUrl(load.posterUrl)
                    if (poster != null)
                        builder.setLargeIcon(poster)
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
}