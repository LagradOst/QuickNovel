package com.lagradost.quicknovel


import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

const val PREFERENCES_NAME: String = "rebuild_preference"
const val DOWNLOAD_FOLDER: String = "downloads_data"
const val DOWNLOAD_SIZE: String = "downloads_size"
const val DOWNLOAD_TOTAL: String = "downloads_total"
const val DOWNLOAD_EPUB_SIZE: String = "downloads_epub_size"

@SuppressLint("StaticFieldLeak")
object DataStore {

    val mapper = JsonMapper.builder().addModule(KotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    private var localContext: Context? = null

    fun getSharedPrefs(): SharedPreferences {
        return getPreferences(localContext!!)
    }

    fun init(_context: Context) {
        localContext = _context
    }

    fun getFolderName(folder: String, path: String): String {
        return "${folder}/${path}"
    }

    fun getKeys(folder: String): List<String> {
        return getSharedPrefs().all.keys.filter { it.startsWith(folder) }
    }

    fun removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun containsKey(folder: String, path: String): Boolean {
        return containsKey(getFolderName(folder, path))
    }

    fun containsKey(path: String): Boolean {
        val prefs = getSharedPrefs()
        return prefs.contains(path)
    }

    fun removeKey(path: String) {
        val prefs = getSharedPrefs()
        if (prefs.contains(path)) {
            val editor: SharedPreferences.Editor = prefs.edit()
            editor.remove(path)
            editor.apply()
        }
    }

    fun removeKeys(folder: String): Int {
        val keys = getKeys(folder)
        keys.forEach { value ->
            removeKey(value)
        }
        return keys.size
    }

    fun <T> setKey(path: String, value: T) {
        val editor: SharedPreferences.Editor = getSharedPrefs().edit()
        editor.putString(path, mapper.writeValueAsString(value))
        editor.apply()
    }

    fun <T> setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> String.toKotlinObject(): T {
        return mapper.readValue(this, T::class.java)
    }

    // GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
    inline fun <reified T : Any> getKey(path: String, defVal: T?): T? {
        try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            return json.toKotlinObject()
        } catch (e: Exception) {
            return null
        }
    }

    inline fun <reified T : Any> getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> getKey(folder: String, path: String): T? {
        return getKey(getFolderName(folder, path), null)
    }

    inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(getFolderName(folder, path), defVal)
    }
}