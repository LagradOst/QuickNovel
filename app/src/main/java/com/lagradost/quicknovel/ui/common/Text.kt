package com.lagradost.quicknovel.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml

@Composable
fun String.html(): AnnotatedString {
    val cache = remember {
        val string = AnnotatedString.fromHtml(this.replace("</p>", "<br/><br/>"))

        var startIndex = 0
        var endIndex = string.length - 1
        var startFound = false

        while (startIndex <= endIndex) {
            val index = if (!startFound) startIndex else endIndex
            val match = string[index].isWhitespace()

            if (!startFound) {
                if (!match)
                    startFound = true
                else
                    startIndex += 1
            } else {
                if (!match)
                    break
                else
                    endIndex -= 1
            }
        }

        string.subSequence(startIndex, endIndex + 1)
    }
    return cache
}