package com.lagradost.quicknovel.util

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.quicknovel.MainActivity
import kotlin.math.pow
import android.net.Uri
import com.lagradost.quicknovel.mvvm.logError
import kotlinx.coroutines.delay

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
data class GoogleTranslationResponse(
    val sentences: List<GoogleSentence>,
    val extra: Any? = null,
    val language: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
data class GoogleSentence(
    val trans: String,
    val orig: String,
    val translit: String? = null,
    val srcTranslit: String? = null
)

object GoogleTranslateOnline {

    private const val baseUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
    private const val paragraphsSeparator = "\nXQZX\n"
    private const val charsLimit = 2000
    private val paragraphsSeparatorRegex = Regex("\\n?XQZX\\n?")

    suspend fun onlineTranslate(
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
            .get("$baseUrl$from&tl=$to&dt=t&q=${Uri.encode(text)}")
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

                if (t is java.net.UnknownHostException) throw t

                retryNumber++

                if (retryNumber >= maxRetry) return text

                delay(500L * (2.0.pow(retryNumber).toLong()))
            }
        }

        return text
    }

    private fun List<String>.chunkByLimit(): List<String> {
        if (this.isEmpty()) return emptyList()
        val combinedChunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (t in this) {
            var text = t.trim().ifBlank { " " }.let {
                it + paragraphsSeparator
            }

            if (text.length > charsLimit) {
                if (currentChunk.isNotEmpty()) {
                    combinedChunks.add(currentChunk.toString().removeSuffix(paragraphsSeparator))
                    currentChunk = StringBuilder()
                }
                combinedChunks.add(text.removeSuffix(paragraphsSeparator))
                continue
            }

            if (currentChunk.length + text.length > charsLimit) {
                combinedChunks.add(currentChunk.toString().removeSuffix(paragraphsSeparator))
                currentChunk = StringBuilder()
            }
            currentChunk.append(text)
        }

        if (currentChunk.isNotEmpty()) {
            combinedChunks.add(currentChunk.toString().removeSuffix(paragraphsSeparator))
        }
        return combinedChunks
    }
}