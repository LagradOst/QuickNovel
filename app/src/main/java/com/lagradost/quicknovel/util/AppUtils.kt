package com.lagradost.quicknovel.util

import android.R.attr.text
import android.app.ActivityManager
import android.content.Context
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
            .replace(Regex("([a-záéíóú](\\.{2,})?) \\n([a-z])"), "$1 $3")
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
}