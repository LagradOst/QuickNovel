package com.lagradost.quicknovel

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.quicknovel.mvvm.logError
import androidx.core.content.edit
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.util.AppUtils.parseJson

const val PREFERENCES_NAME: String = "rebuild_preference"
const val DOWNLOAD_FOLDER: String = "downloads_data"
const val DOWNLOAD_SIZE: String = "downloads_size"
const val DOWNLOAD_TOTAL: String = "downloads_total"
const val DOWNLOAD_OFFSET: String = "downloads_offset"
const val DOWNLOAD_EPUB_SIZE: String = "downloads_epub_size"
const val DOWNLOAD_EPUB_LAST_ACCESS: String = "downloads_epub_last_access"
const val DOWNLOAD_SORTING_METHOD: String = "download_sorting"
const val DOWNLOAD_NORMAL_SORTING_METHOD: String = "download_normal_sorting"
const val DOWNLOAD_SETTINGS: String = "download_settings"
const val EPUB_LOCK_ROTATION: String = "reader_epub_rotation"
const val EPUB_TEXT_SIZE: String = "reader_epub_text_size"
const val EPUB_TEXT_BIONIC: String = "reader_epub_bionic_reading"
const val EPUB_TEXT_SELECTABLE: String = "reader_epub_text_selectable"
const val EPUB_SCROLL_VOL: String = "reader_epub_scroll_volume"
const val EPUB_AUTHOR_NOTES: String = "reader_epub_author_notes"
const val EPUB_TTS_LOCK: String = "reader_epub_scroll_lock"
const val EPUB_TTS_SET_SPEED: String = "reader_epub_tts_speed"
const val RESULT_CHAPTER_SORT: String = "result_chapter_sort"
const val RESULT_CHAPTER_FILTER_DOWNLOADED: String = "result_chapter_filter_download"
const val RESULT_CHAPTER_FILTER_BOOKMARKED: String = "result_chapter_filter_bookmarked"
const val RESULT_CHAPTER_FILTER_READ: String = "result_chapter_filter_read"
const val RESULT_CHAPTER_FILTER_UNREAD: String = "result_chapter_filter_unread"
const val EPUB_TTS_SET_PITCH: String = "reader_epub_tts_pitch"
const val EPUB_BG_COLOR: String = "reader_epub_bg_color"
const val EPUB_TEXT_COLOR: String = "reader_epub_text_color"
const val EPUB_TEXT_VERTICAL_PADDING: String = "reader_epub_vertical_padding"
const val EPUB_TEXT_PADDING: String = "reader_epub_text_padding"
const val EPUB_TEXT_PADDING_TOP: String = "reader_epub_text_padding_top"
const val EPUB_HAS_BATTERY: String = "reader_epub_has_battery"
const val EPUB_KEEP_SCREEN_ACTIVE: String = "reader_epub_keep_screen_active"
const val EPUB_SLEEP_TIMER: String = "reader_epub_tts_timer"
const val EPUB_ML_FROM_LANGUAGE: String = "reader_epub_ml_from"
const val EPUB_ML_TO_LANGUAGE: String = "reader_epub_ml_to"
const val EPUB_ML_USEONLINETRANSLATION: String = "reader_epub_ml_useOnlineTranslation"
const val EPUB_HAS_TIME: String = "reader_epub_has_time"
const val EPUB_TWELVE_HOUR_TIME: String = "reader_epub_twelve_hour_time"
const val EPUB_FONT: String = "reader_epub_font"
const val EPUB_LANG: String = "reader_epub_lang"
const val EPUB_VOICE: String = "reader_epub_voice"
const val EPUB_READER_TYPE: String = "reader_reader_type"
const val EPUB_CURRENT_POSITION: String = "reader_epub_position"
const val EPUB_CURRENT_POSITION_SCROLL: String = "reader_epub_position_scroll"
const val EPUB_CURRENT_POSITION_SCROLL_CHAR: String = "reader_epub_position_scroll_char"
const val EPUB_CURRENT_ML: String = "reader_epub_ml"
const val EPUB_CURRENT_POSITION_READ_AT: String = "reader_epub_position_read"
const val EPUB_CURRENT_POSITION_CHAPTER: String = "reader_epub_position_chapter"

//all novel data like name, url, etc.
const val RESULT_BOOKMARK: String = "result_bookmarked"

//all novels's id saved in libraries
const val RESULT_BOOKMARK_STATE: String = "result_bookmarked_state"
const val HISTORY_FOLDER: String = "result_history"
const val CURRENT_TAB : String = "current_tab"
/** When inserting many keys use this function, this is because apply for every key is very expensive on memory */
data class Editor(
    val editor : SharedPreferences.Editor
) {
    /** Always remember to call apply after */
    fun<T> setKeyRaw(path: String, value: T) {
        @Suppress("UNCHECKED_CAST")
        if (isStringSet(value)) {
            editor.putStringSet(path, value as Set<String>)
        } else {
            when (value) {
                is Boolean -> editor.putBoolean(path, value)
                is Int -> editor.putInt(path, value)
                is String -> editor.putString(path, value)
                is Float -> editor.putFloat(path, value)
                is Long -> editor.putLong(path, value)
            }
        }
    }

    private fun isStringSet(value: Any?) : Boolean {
        if (value is Set<*>) {
            return value.filterIsInstance<String>().size == value.size
        }
        return false
    }

    fun apply() {
        editor.apply()
        System.gc()
    }
}

object DataStore {

    fun editor(context : Context, isEditingAppSettings: Boolean = false) : Editor {
        val editor: SharedPreferences.Editor =
            if (isEditingAppSettings) context.getDefaultSharedPrefs().edit() else context.getSharedPrefs().edit()
        return Editor(editor)
    }

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
                prefs.edit {
                    remove(path)
                }
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
            getSharedPrefs().edit {
                putString(path, mapper.writeValueAsString(value))
            }
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

    fun <T> String.toKotlinObject(valueType: Class<T>): T {
        return mapper.readValue(this, valueType)
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

    fun <T> Context.getKey(path: String, valueType: Class<T>): T? {
        try {
            val json: String = getSharedPrefs().getString(path, null) ?: return null
            return json.toKotlinObject(valueType)
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

const val LIBRARIES_KEY: String = "default_libraries"
const val NOVEL_WATCH_FOLDER: String = "novel_watch_entries"
data class DefaultLibrary(
    val id: Int,
    val key: String,
    val title: String,
    val editable: Boolean = true,
    val position: Int = 0
)

val DEFAULT_LIBRARIES: List<DefaultLibrary> = listOf(
    DefaultLibrary(1, "READING",       R.string.type_reading.toString(),      editable = false, position = 1),
    DefaultLibrary(2, "PLAN_TO_READ",  R.string.type_plan_to_read.toString(), editable = false, position = 2),
    DefaultLibrary(3, "ON_HOLD",       R.string.type_on_hold.toString(),      editable = false, position = 3),
    DefaultLibrary(4, "COMPLETED",     R.string.type_completed.toString(),    editable = false, position = 4),
    DefaultLibrary(5, "DROPPED",       R.string.type_dropped.toString(),      editable = false, position = 5),
)
/**
 * Returns the list of persisted libraries, sorted by [DefaultLibrary.position].
 * If no list is saved, returns [DEFAULT_LIBRARIES].
 */
fun Context.getLibraries(): List<DefaultLibrary> {
    val stored = with(DataStore) { this@getLibraries.getKey<Array<DefaultLibrary>>(LIBRARIES_KEY) }

    if (stored != null) {
        return stored.map { it }.sortedBy { it.position }
    }

    val defaultLibs = DEFAULT_LIBRARIES.map { translateLibrary(it) }
    saveLibraries(defaultLibs)

    return defaultLibs
}

private fun Context.translateLibrary(lib: DefaultLibrary): DefaultLibrary {
    val resId = lib.title.toIntOrNull() ?: return lib
    return try {
        lib.copy(title = this.getString(resId))
    } catch (e: Exception) {
        lib
    }
}

/**
 * Overwrites the persisted library list with [libs] (sorted by position).
 * Throws an exception if there are duplicate IDs.
 */
fun Context.saveLibraries(libs: List<DefaultLibrary>) {
    require(libs.map { it.id }.distinct().size == libs.size) { R.string.library_error_duplicate_ids }
    val sorted = libs.sortedBy { it.position }
    with(DataStore) { this@saveLibraries.setKey(LIBRARIES_KEY, sorted.toTypedArray()) }
}

/**
 * Adds [newLib] to the persisted list.
 * Throws an exception if a library with the same ID already exists.
 */
fun Context.addLibrary(newLib: DefaultLibrary) {
    val current = getLibraries().toMutableList()
    require(current.none { it.id == newLib.id || it.title == newLib.title }) {
        R.string.library_error_exists
    }
    current.add(newLib)
    saveLibraries(current)
}

/**
 * Replaces the library whose ID matches [updated].
 * Respects [DefaultLibrary.editable]: throws an exception if the library is not editable.
 */
//rename
fun Context.updateLibrary(updated: DefaultLibrary) {
    val current = getLibraries().toMutableList()
    val index = current.indexOfFirst { it.id == updated.id }
    require(index >= 0) { R.string.library_error_not_found }
    current[index] = updated
    saveLibraries(current)
}

/**
 * Deletes the library with the given [id].
 * Throws an exception if it is not editable (e.g., "Plan to read").
 */
fun Context.deleteLibrary(id: Int) {
    val current = getLibraries().toMutableList()
    val target = current.find { it.id == id }
    require(target != null) { R.string.library_error_not_found }
    require(target.editable) { R.string.library_error_not_editable }
    current.removeAll { it.id == id }
    saveLibraries(current)
}

/**
 * Returns the number of bookmarks associated with a specific library [id].
 */
fun Context.getLibraryBookmarkCount(id: Int): Int {
    return with(DataStore) {
        this@getLibraryBookmarkCount.getKeys(RESULT_BOOKMARK_STATE)
            .count { key -> getKey<Int>(key) == id }
    }
}

/**
 * Reassigns all bookmarks from [sourceId] to [targetId].
 * Useful when moving books before deleting a category.
 */
fun Context.reassignLibraryBookmarks(sourceId: Int, targetId: Int = 0) {
    require(sourceId != targetId) { R.string.library_error_same_ids }
    if (targetId != 0) {
        require(getLibraries().any { it.id == targetId }) { R.string.library_error_target_not_found }
    }

    val stateKeys = with(DataStore) { this@reassignLibraryBookmarks.getKeys(RESULT_BOOKMARK_STATE) }
    stateKeys.forEach { key ->
        val current = with(DataStore) { this@reassignLibraryBookmarks.getKey<Int>(key) } ?: return@forEach
        if (current == sourceId) {
            with(DataStore) { this@reassignLibraryBookmarks.setKey(key, targetId) }
        }
    }
}

fun Context.mergeLibraries(backupJson: String) {
    try {
        val currentLibs = getLibraries().toMutableList()
        val currentKeys = currentLibs.map { it.key }.toSet()

        val backupLibs = parseJson<List<DefaultLibrary>>(backupJson)

        val newLibs = backupLibs.filter { it.key !in currentKeys }

        if (newLibs.isNotEmpty()) {
            var lastId = currentLibs.maxOfOrNull { it.id } ?: 0
            var lastPos = currentLibs.maxOfOrNull { it.position } ?: 0

            newLibs.forEach { lib ->
                lastId++
                lastPos++
                currentLibs.add(lib.copy(id = lastId, position = lastPos))
            }
            saveLibraries(currentLibs)
        }
    } catch (e: Exception) {
        logError(e)
    }
}

/**
 * Merges [sourceId] library into [targetId].
 * All books are moved to the target library and the source library is deleted.
 */
fun Context.mergeLibraries(sourceId: Int, targetId: Int) {
    require(sourceId != targetId) { R.string.library_error_same_ids }
    val source = getLibraries().firstOrNull { it.id == sourceId }
    require(source != null) { R.string.library_error_not_found }
    require(source.editable) { R.string.library_error_not_editable }

    reassignLibraryBookmarks(sourceId, targetId)
    deleteLibrary(sourceId)
}