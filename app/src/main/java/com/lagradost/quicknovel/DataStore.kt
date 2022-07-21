package com.lagradost.quicknovel

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.quicknovel.mvvm.logError

const val PREFERENCES_NAME: String = "rebuild_preference"
const val DOWNLOAD_FOLDER: String = "downloads_data"
const val DOWNLOAD_SIZE: String = "downloads_size"
const val DOWNLOAD_TOTAL: String = "downloads_total"
const val DOWNLOAD_EPUB_SIZE: String = "downloads_epub_size"
const val DOWNLOAD_EPUB_LAST_ACCESS: String = "downloads_epub_last_access"
const val DOWNLOAD_SORTING_METHOD: String = "download_sorting"
const val DOWNLOAD_NORMAL_SORTING_METHOD: String = "download_normal_sorting"
const val DOWNLOAD_SETTINGS: String = "download_settings"
const val EPUB_LOCK_ROTATION: String = "reader_epub_rotation"
const val EPUB_TEXT_SIZE: String = "reader_epub_text_size"
const val EPUB_SCROLL_VOL: String = "reader_epub_scroll_volume"
const val EPUB_BG_COLOR: String = "reader_epub_bg_color"
const val EPUB_TEXT_COLOR: String = "reader_epub_text_color"
const val EPUB_TEXT_PADDING: String = "reader_epub_text_padding"
const val EPUB_TEXT_PADDING_TOP: String = "reader_epub_text_padding_top"
const val EPUB_HAS_BATTERY: String = "reader_epub_has_battery"
const val EPUB_KEEP_SCREEN_ACTIVE: String = "reader_epub_keep_screen_active"
const val EPUB_HAS_TIME: String = "reader_epub_has_time"
const val EPUB_TWELVE_HOUR_TIME: String = "reader_epub_twelve_hour_time"
const val EPUB_FONT: String = "reader_epub_font"
const val EPUB_CURRENT_POSITION: String = "reader_epub_position"
const val EPUB_CURRENT_POSITION_SCROLL: String = "reader_epub_position_scroll"
const val RESULT_BOOKMARK : String = "result_bookmarked"
const val RESULT_BOOKMARK_STATE : String = "result_bookmarked_state"
const val HISTORY_FOLDER : String = "result_history"

object DataStore {
    val mapper: JsonMapper = JsonMapper.builder().addModule(
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

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun Context.getSharedPrefs(): SharedPreferences {
        return getPreferences(this)
    }

    fun getFolderName(folder: String, path: String): String {
        return "${folder}/${path}"
    }

    fun <T> Context.setKeyRaw(path: String, value: T, isEditingAppSettings: Boolean = false) {
        try {
            val editor: SharedPreferences.Editor =
                if (isEditingAppSettings) getDefaultSharedPrefs().edit() else getSharedPrefs().edit()
            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is String -> editor.putString(path, value)
                is Float -> editor.putFloat(path, value)
                is Long -> editor.putLong(path, value)
                (value as? Set<String> != null) -> editor.putStringSet(path, value as Set<String>)
            }
            editor.apply()
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.getDefaultSharedPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(this)
    }

    fun Context.getKeys(folder: String): List<String> {
        return this.getSharedPrefs().all.keys.filter { it.startsWith(folder) }
    }

    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun Context.containsKey(folder: String, path: String): Boolean {
        return containsKey(getFolderName(folder, path))
    }

    fun Context.containsKey(path: String): Boolean {
        val prefs = getSharedPrefs()
        return prefs.contains(path)
    }

    fun Context.removeKey(path: String) {
        try {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) {
                val editor: SharedPreferences.Editor = prefs.edit()
                editor.remove(path)
                editor.apply()
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys(folder)
        keys.forEach { value ->
            removeKey(value)
        }
        return keys.size
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            val editor: SharedPreferences.Editor = getSharedPrefs().edit()
            editor.putString(path, mapper.writeValueAsString(value))
            editor.apply()
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> String.toKotlinObject(): T {
        return mapper.readValue(this, T::class.java)
    }

    // GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            return json.toKotlinObject()
        } catch (e: Exception) {
            return null
        }
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? {
        return getKey(getFolderName(folder, path), null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(getFolderName(folder, path), defVal) ?: defVal
    }
}