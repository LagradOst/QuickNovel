package com.lagradost.quicknovel.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.BuildConfig
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.logError
import java.io.*
import java.net.URL
import java.net.URLConnection
import kotlin.concurrent.thread

const val UPDATE_TIME = 1000

class InAppUpdater {
    companion object {
        // === IN APP UPDATER ===
        data class GithubAsset(
            @JsonProperty("name") val name: String,
            @JsonProperty("size") val size: Int, // Size bytes
            @JsonProperty("browser_download_url") val browser_download_url: String, // download link
            @JsonProperty("content_type") val content_type: String, // application/vnd.android.package-archive
        )

        data class GithubRelease(
            @JsonProperty("tag_name") val tag_name: String, // Version code
            @JsonProperty("body") val body: String, // Desc
            @JsonProperty("assets") val assets: List<GithubAsset>,
            @JsonProperty("target_commitish") val target_commitish: String, // branch
        )

        data class Update(
            @JsonProperty("shouldUpdate") val shouldUpdate: Boolean,
            @JsonProperty("updateURL") val updateURL: String?,
            @JsonProperty("updateVersion") val updateVersion: String?,
            @JsonProperty("changelog") val changelog: String?,
        )

        private val mapper = JsonMapper.builder().addModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, false)
                .configure(KotlinFeature.NullToEmptyMap, false)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

        private suspend fun Activity.getAppUpdate(): Update {
            try {
                val url = "https://api.github.com/repos/LagradOst/QuickNovel/releases/latest"
                val headers = mapOf("Accept" to "application/vnd.github.v3+json")
                val response =
                    mapper.readValue<GithubRelease>(app.get(url, headers = headers).text)

                val versionRegex = Regex("""(.*?((\d)\.(\d)\.(\d)).*\.apk)""")

                /*
                val releases = response.map { it.assets }.flatten()
                    .filter { it.content_type == "application/vnd.android.package-archive" }
                val found =
                    releases.sortedWith(compareBy {
                        versionRegex.find(it.name)?.groupValues?.get(2)
                    }).toList().lastOrNull()*/
//                val found =
//                    response.sortedWith(compareBy { release ->
//                        release.assets.filter { it.content_type == "application/vnd.android.package-archive" }
//                            .getOrNull(0)?.name?.let { it1 ->
//                                versionRegex.find(
//                                    it1
//                                )?.groupValues?.get(2)
//                            }
//                    }).toList().lastOrNull()
                val foundAsset = response.assets.getOrNull(0)
                val currentVersion = packageName?.let {
                    packageManager.getPackageInfo(
                        it,
                        0
                    )
                }

                val foundVersion = foundAsset?.name?.let { versionRegex.find(it) }
                val shouldUpdate =
                    if (foundAsset?.browser_download_url != "" && foundVersion != null) currentVersion?.versionName?.compareTo(
                        foundVersion.groupValues[2]
                    )!! < 0 else false
                return if (foundVersion != null) {
                    Update(
                        shouldUpdate,
                        foundAsset.browser_download_url,
                        foundVersion.groupValues[2],
                        response.body
                    )
                } else {
                    Update(false, null, null, null)
                }

            } catch (e: Exception) {
                println(e)
                return Update(false, null, null, null)
            }
        }

        private fun Activity.downloadUpdate(url: String): Boolean {
            var fullResume = false // IF FULL RESUME
            try {
                // =================== DOWNLOAD POSTERS AND SETUP PATH ===================
                val path = filesDir.toString() +
                        "/Download/apk/update.apk"

                // =================== MAKE DIRS ===================
                val rFile = File(path)
                try {
                    rFile.parentFile?.mkdirs()
                } catch (t: Throwable) {
                    logError(t)
                }

                val _url = URL(url.replace(" ", "%20"))

                val connection: URLConnection = _url.openConnection()

                var bytesRead = 0L

                // =================== STORAGE ===================
                try {
                    if (!rFile.exists()) {
                        rFile.createNewFile()
                    } else {
                        rFile.delete()
                        rFile.createNewFile()
                    }
                } catch (e: Exception) {
                    println(e)

                    showToast(R.string.permission_error, Toast.LENGTH_SHORT)
                    return false
                }

                // =================== CONNECTION ===================
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.connectTimeout = 10000
                var clen = 0
                try {
                    connection.connect()
                    clen = connection.contentLength
                } catch (t: Throwable) {
                    logError(t)
                }

                // =================== VALIDATE ===================
                if (clen < 5000000) { // min of 5 MB
                    clen = 0
                }
                if (clen <= 0) { // TO SMALL OR INVALID
                    //showNot(0, 0, 0, DownloadType.IsFailed, info)
                    return false
                }

                // =================== SETUP VARIABLES ===================
                //val bytesTotal: Long = (clen + bytesRead.toInt()).toLong()
                val input: InputStream = BufferedInputStream(connection.inputStream)
                val output: OutputStream = FileOutputStream(rFile, false)
                var bytesPerSec = 0L
                val buffer = ByteArray(1024)
                var count: Int
                //var lastUpdate = System.currentTimeMillis()

                while (true) {
                    try {
                        count = input.read(buffer)
                        if (count < 0) break

                        bytesRead += count
                        bytesPerSec += count
                        output.write(buffer, 0, count)
                    } catch (t: Throwable) {
                        logError(t)
                        fullResume = true
                        break
                    }
                }

                if (fullResume) { // IF FULL RESUME DELETE CURRENT AND DONT SHOW DONE
                    with(NotificationManagerCompat.from(this)) {
                        cancel(-1)
                    }
                }

                output.flush()
                output.close()
                input.close()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val contentUri = FileProvider.getUriForFile(
                        this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        rFile
                    )
                    val install = Intent(Intent.ACTION_VIEW)
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    install.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    install.data = contentUri
                    startActivity(install)
                    return true
                } else {
                    val apkUri = Uri.fromFile(rFile)
                    val install = Intent(Intent.ACTION_VIEW)
                    install.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    install.setDataAndType(
                        apkUri,
                        "application/vnd.android.package-archive"
                    )
                    startActivity(install)
                    return true
                }

            } catch (t: Throwable) {
                logError(t)
                return false
            }
        }

        suspend fun Activity.runAutoUpdate(checkAutoUpdate: Boolean = true): Boolean {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)

            if (!checkAutoUpdate || settingsManager.getBoolean(
                    getString(R.string.auto_update_key),
                    true
                )
            ) {
                val update = getAppUpdate()
                if (update.shouldUpdate && update.updateURL != null) {
                    runOnUiThread {
                        val currentVersion = packageName?.let {
                            packageManager.getPackageInfo(
                                it,
                                0
                            )
                        }

                        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                        builder.setTitle(
                            getString(R.string.new_update_found_format).format(
                                currentVersion?.versionName,
                                update.updateVersion
                            )
                        )
                        builder.setMessage(update.changelog)

                        val context = this
                        builder.apply {
                            setPositiveButton(R.string.update) { _, _ ->
                                showToast(context, R.string.download_started, Toast.LENGTH_LONG)
                                thread {
                                    val downloadStatus = context.downloadUpdate(update.updateURL)
                                    if (!downloadStatus) {
                                        showToast(
                                            R.string.download_failed,
                                            Toast.LENGTH_LONG
                                        )

                                    } /*else {
                                        activity.runOnUiThread {
                                            Toast.makeText(localContext,
                                                "Downloaded APK",
                                                Toast.LENGTH_LONG).show()
                                        }
                                    }*/
                                }
                            }

                            setNegativeButton(R.string.cancel) { _, _ -> }

                            if (checkAutoUpdate) {
                                setNeutralButton(R.string.dont_show_again) { _, _ ->
                                    settingsManager.edit().putBoolean("auto_update", false).apply()
                                }
                            }
                        }
                        builder.show()
                    }
                    return true
                }
                return false
            }
            return false
        }
    }
}