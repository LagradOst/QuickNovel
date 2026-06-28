package com.lagradost.quicknovel.util.Translation

import com.lagradost.quicknovel.MainActivity
import kotlin.math.pow
import android.net.Uri
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.Translation.models.GoogleTranslationResponse
import kotlinx.coroutines.delay
import java.net.UnknownHostException
object GoogleTranslateOnline {

    private const val BASEURL = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
    private const val PARAGRAPH_DELIMITER = "\nXQZX\n"
    var charsLimit = 2000
    private val paragraphsSeparatorRegex = Regex("\\n?XQZX\\n?")

    suspend fun translate(
        paragraphs: List<String>,
        from: String,
        to: String,
        loading: suspend (Int, Int) -> Unit = { _, _ -> }
    ): List<String> {
        if (paragraphs.isEmpty()) return emptyList()

        val chunks = paragraphs.chunkByLimit()
        val translatedChunks = chunks.mapIndexed { i, chunk ->
            loading.invoke(i, chunks.size)
            translateChunk(chunk, from, to)
        }

        return translatedChunks.flatMap { it.split(paragraphsSeparatorRegex) }
    }

    private suspend fun callGoogleTranslateApi(text: String, from: String, to: String) =
        MainActivity
            .app
            .get("$BASEURL$from&tl=$to&dt=t&q=${Uri.encode(text)}")
            .parsed<GoogleTranslationResponse>()



    private suspend fun translateChunk(
        text: String,
        from: String,
        to: String,
        retry: Boolean = false
    ): String {

        var retryNumber = 0
        val maxRetry = 3

        while (retryNumber < maxRetry) {
            try {
                val response = callGoogleTranslateApi(text, from, to)
                val translatedSentences = response.sentences.map { sentence ->

                    val trans = sentence.trans
                    val orig = sentence.orig

                    val failed = trans == orig &&
                            orig.any { it.isLetter() } &&
                            orig.split(" ").size >= 3

                    if (failed && !retry) {
                        translateChunk(orig, from, to, true)
                    } else {
                        trans
                    }
                }

                return translatedSentences.joinToString("")

            } catch (t: Throwable) {
                logError(t)

                if (t is UnknownHostException) throw t

                retryNumber++

                if (retryNumber >= maxRetry) throw t

                delay(500L * (2.0.pow(retryNumber).toLong()))
            }
        }

        return "" //this never happens
    }

    private fun List<String>.chunkByLimit(): List<String> {
        if (this.isEmpty()) return emptyList()
        val combinedChunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (t in this) {
            val text = t.trim().ifBlank { " " }.let {
                it + PARAGRAPH_DELIMITER
            }

            if (text.length > charsLimit) {
                if (currentChunk.isNotEmpty()) {
                    combinedChunks.add(currentChunk.toString().removeSuffix(PARAGRAPH_DELIMITER))
                    currentChunk = StringBuilder()
                }
                combinedChunks.add(text.removeSuffix(PARAGRAPH_DELIMITER))
                continue
            }

            if (currentChunk.length + text.length > charsLimit) {
                combinedChunks.add(currentChunk.toString().removeSuffix(PARAGRAPH_DELIMITER))
                currentChunk = StringBuilder()
            }
            currentChunk.append(text)
        }

        if (currentChunk.isNotEmpty()) {
            combinedChunks.add(currentChunk.toString().removeSuffix(PARAGRAPH_DELIMITER))
        }
        return combinedChunks
    }
}