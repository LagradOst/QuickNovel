package com.lagradost.quicknovel.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml

private val nonWhitespace = Regex("\\S")

@Composable
fun String.html(): AnnotatedString {
    val cache = remember {
        val string = AnnotatedString.fromHtml(this.replace("</p>", "<br/><br/>"))

        /** Avoids too large newlines, and force each paragraph to use 2 "par skip" style */
        var startIndex =
            nonWhitespace.find(string)?.range?.start ?: return@remember AnnotatedString("")
        val builder = AnnotatedString.Builder()
        while (startIndex < string.length) {
            val endIndex = string.indexOf(char = '\n', startIndex = startIndex)
            if (endIndex == -1) {
                builder.append(string.subSequence(startIndex, string.length))
                break
            }
            builder.append(string.subSequence(startIndex = startIndex, endIndex = endIndex + 1))
            /** Split by paragraph */
            builder.append('\n')
            startIndex = nonWhitespace.find(string, startIndex = endIndex)?.range?.start ?: break
        }
        val annotated = builder.toAnnotatedString()

        /** Trim end without removing styling */
        for (index in annotated.indices.reversed()) {
            if (!annotated[index].isWhitespace()) {
                return@remember annotated.subSequence(0, index + 1)
            }
        }
        return@remember AnnotatedString("")
    }
    return cache
}