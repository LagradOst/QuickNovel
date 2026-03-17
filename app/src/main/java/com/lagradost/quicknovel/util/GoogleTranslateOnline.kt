package com.lagradost.quicknovel.util

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.lagradost.quicknovel.MainActivity
import kotlin.math.pow
import android.net.Uri
import com.lagradost.quicknovel.mvvm.logError
import kotlinx.coroutines.delay


class GoogleTranslateOnline(val loading: suspend (Int, Int) -> Unit = {_,_->})
{
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

    private val baseUrl = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
    val paragraphsSeparator ="\nXQZX\n"
    val paragraphsSeparatorRegex = Regex("\\n?XQZX\\n?")
    val charsLimit = 2000

    private suspend fun callGoogleTranslateApi(text: String, from: String, to: String) =
        MainActivity
            .app
            .get("$baseUrl$from&tl=$to&dt=t&q=${Uri.encode(text)}")
            .parsed<GoogleTranslationResponse>()

    suspend fun onlineTranslate(paragraphs: List<String>, from: String, to: String): List<String> {
        if (paragraphs.isEmpty()) return emptyList()
        val chunks = paragraphs.chunkByLimit()

        val translatedChunks = chunks.mapIndexed { i, chunk ->
            loading.invoke(i, chunks.size)
            translateChunk(chunk, from, to)
        }

        return translatedChunks.flatMap { it.split(paragraphsSeparatorRegex) }
    }


    private suspend fun translateChunk(text: String, from: String, to: String): String {
        try {
            val response = callGoogleTranslateApi(text, from, to)
            val translatedSentences = response.sentences.map { sentence ->
                val trans = sentence.trans
                val orig = sentence.orig
                val failed = trans == orig &&
                             orig.any { it.isLetter() } &&
                             orig.split(" ").size >= 3

                if (failed) retrySpecificSentence(orig, from, to)
                else trans
            }
            return translatedSentences.joinToString("")
        } catch (t: Throwable) {
            if (t is java.net.UnknownHostException) throw t
            return retrySpecificSentence(text, from, to)
        }
    }

    private suspend fun retrySpecificSentence(text: String, from: String, to: String): String {
        var retryNumber = 0
        val maxRetry = 3

        while (retryNumber < maxRetry) {
            try {
                val response = callGoogleTranslateApi(text, from, to)
                return response.sentences.joinToString("") { it.trans }
            } catch (t: Throwable) {
                logError(t)
                if (t is java.net.UnknownHostException)
                    throw t

                retryNumber++
                if (retryNumber >= maxRetry){
                    return text
                }
                delay(500L * (2.0.pow(retryNumber).toLong()))
            }
        }
        return text
    }

    fun List<String>.chunkByLimit(): List<String> {
        if (this.isEmpty()) return emptyList()
        val combinedChunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        for (t in this) {
            val text = t.trim().ifBlank { " " }.let {
                it + paragraphsSeparator
            }

            if (text.length > charsLimit) {
                if (currentChunk.isNotEmpty()) {
                    combinedChunks.add(currentChunk.toString().removeSuffix(paragraphsSeparator))
                    currentChunk = StringBuilder()
                }
                combinedChunks.addAll(text.chunkByLimit())
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
    fun String.chunkByLimit(): List<String>  {
        if (this.isBlank()) return emptyList()
        val initialParts = this.split("\\n")
        val refinedParts = mutableListOf<String>()
        for (part in initialParts)
            if (part.length <= charsLimit) refinedParts.add(part)
            else refinedParts.addAll(part.split(" "))
        return refinedParts
    }
}
