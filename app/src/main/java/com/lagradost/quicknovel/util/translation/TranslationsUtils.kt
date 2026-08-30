package com.lagradost.quicknovel.util.translation

import org.jsoup.Jsoup

object TranslationsUtils {
    fun sanitize(text: String): String {
        // Remove zero-width spaces, marks and other non-printable control characters
        return text.replace(Regex("[\u200B-\u200F\uFEFF]"), "")
    }

    fun isTranslatable(text: String, isHtml: Boolean): Boolean {
        if (text.isBlank()) return false
        val sanitized = sanitize(text)
        return if (isHtml) {
            val plainText = Jsoup.parse(sanitized).text()
            plainText.isNotBlank() && plainText.any { it.isLetter() }
        } else {
            sanitized.trim().any { it.isLetter() }
        }
    }
}
