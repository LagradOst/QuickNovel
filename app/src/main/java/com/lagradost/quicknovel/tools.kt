package com.lagradost.quicknovel

import android.net.Uri
import com.lagradost.quicknovel.MainActivity.Companion.app
import java.util.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

suspend fun jConnect(
    url: String,
    params: Map<String, String> = mapOf(),
    method: String = "GET"
): Document? {
    val head = mapOf(
        "Accept" to "*/*",
        "Accept-Encoding" to "gzip, deflate",
        "User-Agent" to USER_AGENT
    )
    return try {
        val res = app.custom(method, url = url, headers = head, params = params)
        if (res.code == 200) Jsoup.parse(res.text) else null
    } catch (e: Exception) {
        null
    }
}

fun String.toRate(maxRate: Int = 10): Int {
    return this
        .replace(Regex("[^.0-9]"), "")
        .toFloatOrNull()
        ?.times(1000 / maxRate)
        ?.toInt() ?: 0
}

fun String.toVote(): Int {
    val k = this.contains("K", true)
    return this
        .replace(Regex("[^.0-9]"), "")
        .toFloatOrNull()
        ?.times(if (k) 1000 else 1)
        ?.toInt() ?: 0
}

fun String.toChapters(): String = this.replace(Regex("[^0-9]"), "")

fun String.toStatus(): Int {
    return this
        .clean()
        .lowercase(Locale.getDefault())
        .let {
            when (it) {
                "completed" -> STATUS_COMPLETE
                "ongoing" -> STATUS_ONGOING
                else -> STATUS_NULL
            }
        }
}

fun String.clean(): String {
    return this
        .replace(Regex("[\\n\\t\\r]"), "")
        .replace(Regex("[ ]{2,}"), " ")
}

fun String.synopsis(): String {
    return this
        .replace(Regex("(\\. )"), ".\n\n")
        .replace(Regex("[\\t\\r]"), "")
        .replace(Regex("\\n{1}"), "\n\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .replace(Regex("[ ]{2,}"), " ")
}

fun String.toUrl(): Uri? = runCatching { Uri.parse(this) }.getOrNull()
fun String.toUrlBuilder(): Uri.Builder? = toUrl()?.buildUpon()
fun String.toUrlBuilderSafe(): Uri.Builder = toUrl()?.buildUpon()!!
fun Uri.Builder.ifCase(case: Boolean, action: Uri.Builder.() -> Uri.Builder) = when {
    case -> action(this)
    else -> this
}

fun Uri.Builder.addPath(vararg path: String) =
    path.fold(this) { builder, s ->
        builder.appendPath(s)
    }

fun Uri.Builder.add(vararg query: Pair<String, Any>) =
    query.fold(this) { builder, s ->
        builder.appendQueryParameter(s.first, s.second.toString())
    }

fun Uri.Builder.add(key: String, value: Any): Uri.Builder =
    appendQueryParameter(key, value.toString())
