package com.lagradost.quicknovel

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.StorageFile
import com.anggrayudi.storage.extension.isRawFile
import com.anggrayudi.storage.file.CreateMode
import com.anggrayudi.storage.file.MimeType
import com.anggrayudi.storage.media.FileDescription
import com.anggrayudi.storage.media.MediaStoreCompat
import com.anggrayudi.storage.media.MediaType
import com.anggrayudi.storage.toStorageFile
import com.lagradost.quicknovel.BookDownloader2Helper.sanitizeFilename
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.tachiyomi.AndroidPreferenceStore
import com.lagradost.quicknovel.tachiyomi.PreferenceData

data class FileStorage(
    val mimeType: String,
    val defaultSubFolder: String,
    val key: Int,
) {
    val defaultLocation = "${Environment.DIRECTORY_DOWNLOADS}/$defaultSubFolder/"

    fun setLocation(context: Context, uri: Uri?) {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        settingsManager.edit { putString(context.getString(key), uri?.toString()) }
    }

    fun getLocation(context: Context): Uri? {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        return settingsManager.getString(context.getString(key), null)?.toUri()
    }

    fun getVisualLocation(context: Context): String {
        val uri = getLocation(context)
        return if (uri != null) {
            val absolutePath = uri.toStorageFile(context)?.absolutePath
            absolutePath ?: uri.toString()
        } else {
            defaultLocation
        }
    }

    fun openFile(context: Context, name: String, requiresWriteAccess: Boolean): StorageFile? {
        FileHelper.requestStorage(context)
        return FileHelper.openFile(
            context,
            getLocation(context),
            defaultSubFolder,
            sanitizeFilename(name),
            mimeType,
            requiresWriteAccess
        )
    }

    fun createFile(context: Context, name: String): StorageFile? {
        FileHelper.requestStorage(context)
        return FileHelper.createFile(context, getLocation(context), defaultSubFolder, sanitizeFilename(name), mimeType)
    }

    fun toPreference(context: Context, store : AndroidPreferenceStore) : PreferenceData<String> {
        return store.getString(
            context.getString(key),
            defaultLocation
        )
    }
}


object FileHelper {
    const val EPUB_MIME = "application/epub+zip"
    const val TEXT_MIME = "text/plain"
    val epub = FileStorage(EPUB_MIME, "Epub", R.string.epub_path_key)
    val backup = FileStorage(TEXT_MIME, "Backup", R.string.backup_path_key)
    val logcat = FileStorage(TEXT_MIME, "Logcat", R.string.logcat_path_key)

    fun requestStorage(context: Context) {
        try {
            ((context as? Activity) ?: CommonActivity.activity)?.let {
                requestStorage(activity = it)
            }
        } catch (t : Throwable) {
            logError(t)
        }
    }

    fun requestStorage(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return
        }
        if (ContextCompat.checkSelfPermission(
            activity,
            WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    WRITE_EXTERNAL_STORAGE,
                    READ_EXTERNAL_STORAGE
                ),
                1337
            )
        }
    }

    fun exportUri(context: Context, uri: Uri) : Uri {
        return if(uri.isRawFile) {
            FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".provider",
                uri.toFile()
            )
        } else {
            uri
        }
    }

    fun createFile(
        context: Context,
        storageLocation: Uri?,
        defaultSubFolder: String,
        name: String,
        mimeType: String,
    ): StorageFile? {
        return if (storageLocation != null) {
            StorageFile.from(context, storageLocation)
                ?.createFile(name = name, mimeType = mimeType, CreateMode.REPLACE)
        } else {
            MediaStoreCompat.createDownload(
                context = context,
                file = FileDescription(
                    name = name,
                    defaultSubFolder,
                    mimeType = mimeType
                ),
                mode = CreateMode.REPLACE
            )?.toStorageFile(context)
        }
    }

    fun openFile(
        context: Context,
        storageLocation: Uri?,
        defaultSubFolder: String,
        name: String,
        mimeType: String,
        requiresWriteAccess: Boolean
    ): StorageFile? {
        val mimeExt = MimeType.getExtensionFromMimeType(mimeType)
        return if (storageLocation != null) {
            StorageFile.from(context, storageLocation)
                ?.child(path = "$name.$mimeExt", requiresWriteAccess = requiresWriteAccess)
        } else {
            MediaStoreCompat.fromBasePath(
                context = context,
                mediaType = MediaType.DOWNLOADS,
                basePath = "${Environment.DIRECTORY_DOWNLOADS}/$defaultSubFolder/$name.$mimeExt"
            )?.toStorageFile(context)
        }
    }
}