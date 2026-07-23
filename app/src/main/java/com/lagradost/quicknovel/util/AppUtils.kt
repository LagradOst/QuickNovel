package com.lagradost.quicknovel.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.mvvm.logError
import java.io.Reader

object AppUtils {
    val mapper: ObjectMapper = jacksonObjectMapper().configure(
        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
        false
    )

    /** Any object as json string */
    fun Any.toJson(): String {
        if (this is String) return this
        return mapper.writeValueAsString(this)
    }

    inline fun <reified T> parseJson(value: String): T {
        return mapper.readValue(value)
    }

    inline fun <reified T> parseJson(reader: Reader, valueType: Class<T>): T {
        return mapper.readValue(reader, valueType)
    }

    inline fun <reified T> tryParseJson(value: String?): T? {
        return try {
            parseJson(value ?: return null)
        } catch (_: Exception) {
            null
        }
    }

    fun isServiceRunning(ctx: Context, service: Class<*>): Boolean =
        try {
            (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(
                Integer.MAX_VALUE
            ).any { cmp -> service.name == cmp.service.className }
        } catch (t: Throwable) {
            false
        }
    fun String.textToHtmlChapter(): String {
        return this
            .replace(Regex("((?<=\\p{Ll}(\\.{2,5})?),?) \\n(?=\\p{Ll})"), " ")
            .split(Regex("\\n"))
            .joinToString("") { paragraph ->
                if (paragraph.trim().isNotBlank()) {
                    paragraph.split(Regex("(?<=(?<!\\.)\\.)(?=\\s+)"))
                        .joinToString("") { "<p>${it}</p>" } + "</br>"
                } else {
                    "</br>"
                }
            }
    }

    fun String.toLibraryKey(): String {
        val sanitized = this.uppercase()
            .replace(" ", "_")
            .replace(Regex("[^A-Z0-9_]"), "")
            .trim('_')
        return if (sanitized.isEmpty()) "" else "CUSTOM_$sanitized"
    }

    fun openInBrowser(url : String) {
        try {
            if (url.isBlank()) return
            val i = Intent(Intent.ACTION_VIEW)
            i.data = url.toUri()
            activity?.startActivity(i)
        } catch (t : Throwable) {
            logError(t)
        }
    }
}