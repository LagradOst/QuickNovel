package com.lagradost.quicknovel

import android.net.Uri
import androidx.core.net.toUri

fun String.clean(): String {
    return this
        .replace(Regex("[\\n\\t\\r]"), "")
        .replace(Regex(" {2,}"), " ")
}

fun String.synopsis(): String {
    return this
        .replace(Regex("(\\. )"), ".\n\n")
        .replace(Regex("[\\t\\r]"), "")
        .replace(Regex("\\n{1}"), "\n\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .replace(Regex(" {2,}"), " ")
}

fun String.toUrl(): Uri? = runCatching { this.toUri() }.getOrNull()
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
