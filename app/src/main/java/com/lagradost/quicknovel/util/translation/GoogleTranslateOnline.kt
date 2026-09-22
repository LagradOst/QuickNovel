package com.lagradost.quicknovel.util.translation

import android.net.Uri
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.translation.models.FailedContext
import com.lagradost.quicknovel.util.translation.models.GoogleTranslationResponse
import com.lagradost.quicknovel.util.translation.models.TranslationResult
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

class GoogleTranslateOnline {
    data class FragmentMeta(val shell: String, val content: String, val originalIndex: Int, val tags: List<String>)

    companion object {
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:129.0) Gecko/20100101 Firefox/129.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 OPR/113.0.0.0"
        )
        private var userAgentIndex = 0

        var app2: Requests = initApp2(USER_AGENTS[0])
            private set

        private fun initApp2(userAgent: String): Requests {
            return Requests(
                OkHttpClient()
                    .newBuilder()
                    .ignoreAllSSLErrors()
                    .readTimeout(30L, TimeUnit.SECONDS)
                    .build(),
                responseParser = object : ResponseParser {
                    val mapper: ObjectMapper = jacksonObjectMapper().configure(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false
                    )

                    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
                        return mapper.readValue(text, kClass.java)
                    }

                    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
                        return try {
                            mapper.readValue(text, kClass.java)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    override fun writeValueAsString(obj: Any): String {
                        return mapper.writeValueAsString(obj)
                    }
                }
            ).apply {
                defaultHeaders = mapOf("user-agent" to userAgent)
            }
        }

        fun rotateUserAgent() {
            userAgentIndex = (userAgentIndex + 1) % USER_AGENTS.size
            app2 = initApp2(USER_AGENTS[userAgentIndex])
        }

        private const val BASEURL = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
        private const val PARAGRAPH_DELIMITER = "\n\n\n\nFDHJEJHGYRSTJFDGLKDFGJREWY\n\n\n\n"
        private val paragraphsSeparatorRegex = Regex("\\n?FDHJEJHGYRSTJFDGLKDFGJREWY\\n?")
        private const val MAX_CHARS_PER_CHUNK: Int = 2500
    }

    /**
     * Recursively attempts to fix paragraphs that failed to translate (where trans == orig).
     *
     * @param translationResult The result from the previous translation attempt.
     * @param depth Current recursion level to prevent infinite loops.
     * @return A list of strings where failed paragraphs have been replaced by successful retries.
     */
    suspend fun fixFailures(
        translationResult: TranslationResult,
        from: String,
        to: String,
        depth: Int = 0,
        isHtml: Boolean = false
    ): List<String> {
        // If there are no failed chunks to fix, or we reached the maximum retry limit (3)
        // we stop recursion and return the lines as they are
        if (translationResult.failedChunks.isEmpty()  || depth >= 3) return translationResult.translatedLines

        // Create a simple list of strings containing only the text of the paragraphs that failed
        val textsToFix = translationResult.failedChunks.map { it.text }
        // Call the main translate function again, but ONLY for the failed texts
        // This returns a new TranslationResult which might still contain some failures
        val retryResult = translate(textsToFix, from, to, isHtml, { _, _ -> })
        // Create a mutable copy of the current translated lines to update them
        val finalLines = translationResult.translatedLines.toMutableList()

        retryResult.translatedLines.forEachIndexed { index, fixedText ->
            // We use 'index' to find the corresponding metadata from the previous attempt
            // 'index' in retryResult refers to the position in 'textsToFix'
            val originalMeta = translationResult.failedChunks.getOrNull(index)
            if (originalMeta != null) {
                // Use the 'originalIndex' (the global position in the book)
                // to place the newly translated text in the correct spot
                finalLines[originalMeta.originalIndex] = fixedText
            }
        }


        // If the retry attempt also produced failures, we need to handle them.
        if (retryResult.failedChunks.isNotEmpty()) {
            // Map the new failures back to their global indices
            // retryFailed.originalIndex points to the index in 'textsToFix'
            // We need to look up what that index was in the original book
            val deeperFailedContexts = retryResult.failedChunks.mapNotNull { retryFailed ->
                translationResult.failedChunks.getOrNull(retryFailed.originalIndex)
            }

            // Recursive call: try to fix the remaining failures, incrementing depth
            return fixFailures(
                TranslationResult(finalLines, deeperFailedContexts),
                from,
                to,
                depth + 1,
                isHtml = isHtml
            )
        }
        return finalLines
    }

    suspend fun translate(
        textList: List<String>,
        from: String,
        to: String,
        isHtml: Boolean,
        progress: suspend (Int, Int) -> Unit
    ): TranslationResult {
        if (textList.isEmpty()) return TranslationResult(emptyList(), emptyList())

        val allTranslatedLines = Array(textList.size) { "" }
        val failedParagraphs = mutableListOf<FailedContext>()

        // Separate and extract shell (tags) from content
        val contentFragments = mutableListOf<FragmentMeta>()

        textList.forEachIndexed { index, text ->
            if (!TranslationsUtils.isTranslatable(text, isHtml)) {
                allTranslatedLines[index] = text
            } else {
                if (isHtml) {
                    val (shell, content, tags) = TranslationsUtils.extractDeepShell(text)
                    val sanitizedContent = TranslationsUtils.sanitize(content)
                    contentFragments.add(FragmentMeta(shell, sanitizedContent, index, tags))
                } else {
                    val sanitizedText = TranslationsUtils.sanitize(text)
                    contentFragments.add(FragmentMeta("%s", sanitizedText, index, emptyList()))
                }
            }
        }

        if (contentFragments.isNotEmpty()) {
            val chunks = contentFragments.chunkByLimit()
            chunks.forEachIndexed { i, chunk ->
                if (i > 0) delay(1.seconds) // Throttle requests within the same chapter
                progress.invoke(i, chunks.size)

                val combinedText = chunk.joinToString(PARAGRAPH_DELIMITER) { it.content }
                val translatedBatch = translateChunk(combinedText, from, to)

                val splitParts = translatedBatch.split(paragraphsSeparatorRegex)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val expectedSize = chunk.size
                if (splitParts.size == expectedSize) {
                    chunk.forEachIndexed { localIndex, meta ->
                        var translatedText = splitParts[localIndex]

                        if (isHtml) {
                            translatedText = translatedText.replace(Regex("\\n+"), " ")

                            // Escape < and > to ensure literal terms are displayed as text
                            translatedText = translatedText.replace("<", "&lt;").replace(">", "&gt;")

                            // Restore tags sequentially
                            val tagPattern = TranslationsUtils.TAG_DELIMITER.trim()
                            for (tag in meta.tags) {
                                translatedText = translatedText.replaceFirst(Regex("\\s?" + Regex.escape(tagPattern) + "\\s?"), tag)
                            }
                            translatedText = translatedText.trim()
                        }

                        val finalResult = try { meta.shell.replaceFirst("%s", translatedText) } catch (e: Exception) { translatedText }
                        val originalGlobalIndex = meta.originalIndex
                        allTranslatedLines[originalGlobalIndex] = finalResult

                        if (translatedText == meta.content && meta.content.any { it.isLetter() } && meta.content.split(" ").size >= 3) {
                            failedParagraphs.add(FailedContext(originalGlobalIndex, meta.content))
                        }
                    }
                } else {
                    // Mismatch: Mark all as failed to trigger fixFailures (individual translation)
                    chunk.forEach { meta ->
                        val originalGlobalIndex = meta.originalIndex
                        failedParagraphs.add(FailedContext(originalGlobalIndex, meta.content))
                        allTranslatedLines[originalGlobalIndex] = try { meta.shell.replaceFirst("%s", meta.content) } catch (e: Exception) { meta.content }
                    }
                }
            }
        }

        return TranslationResult(allTranslatedLines.toList(), failedParagraphs)
    }

    private suspend fun callGoogleTranslateApi(text: String, from: String, to: String): GoogleTranslationResponse {
        val res = app2.get(url = "$BASEURL$from&tl=$to&dt=t&q=${Uri.encode(text)}")
        return res.parsed()
    }

    private suspend fun translateChunk(
        text: String,
        from: String,
        to: String
    ): String {
        var retryNumber = 0
        val maxRetry = 5
        while (retryNumber < maxRetry) {
            try {
                val response = callGoogleTranslateApi(text, from, to)
                val sentences = response.sentences
                if (sentences.isEmpty()) return text

                return sentences.joinToString("") { it.trans }
            } catch (t: Throwable) {
                logError(t)
                if (t is UnknownHostException) throw t
                rotateUserAgent()
                retryNumber++
                if (retryNumber >= maxRetry) throw t
            }
        }
        return text
    }

    private fun List<FragmentMeta>.chunkByLimit(): List<List<FragmentMeta>> {
        if (this.isEmpty()) return emptyList()
        val chunks = mutableListOf<List<FragmentMeta>>()
        var currentChunk = mutableListOf<FragmentMeta>()
        var currentLength = 0

        for (item in this) {
            val itemLength = Uri.encode(item.content + PARAGRAPH_DELIMITER).length
            if (currentChunk.isNotEmpty() && currentLength + itemLength > MAX_CHARS_PER_CHUNK) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                currentLength = 0
            }
            currentChunk.add(item)
            currentLength += itemLength
        }
        if (currentChunk.isNotEmpty()) chunks.add(currentChunk)
        return chunks
    }
}