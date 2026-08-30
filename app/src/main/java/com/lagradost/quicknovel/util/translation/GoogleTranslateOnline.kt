package com.lagradost.quicknovel.util.translation

import android.net.Uri
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.translation.models.GoogleTranslationResponse
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

class GoogleTranslateOnline {
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

        suspend fun onlineTranslate(
            textList: List<String>,
            from: String,
            to: String,
            progress: suspend (Int, Int) -> Unit
        ): List<String> {
            if (textList.isEmpty()) return emptyList()

            val allTranslatedLines = Array(textList.size) { "" }
            val contentFragments = mutableListOf<Pair<String, Int>>()

            textList.forEachIndexed { index, text ->
                if (!TranslationsUtils.isTranslatable(text, false)) {
                    allTranslatedLines[index] = text
                } else {
                    contentFragments.add(TranslationsUtils.sanitize(text) to index)
                }
            }

            if (contentFragments.isNotEmpty()) {
                val chunks = chunkByLimit(contentFragments)
                chunks.forEachIndexed { i, chunk ->
                    if (i > 0) delay(1.seconds)
                    progress.invoke(i, chunks.size)

                    val combinedText = chunk.joinToString(PARAGRAPH_DELIMITER) { it.first }
                    val translatedBatch = translateChunk(combinedText, from, to)

                    val splitParts = translatedBatch.split(paragraphsSeparatorRegex)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    if (splitParts.size == chunk.size) {
                        chunk.forEachIndexed { localIndex, pair ->
                            allTranslatedLines[pair.second] = splitParts[localIndex]
                        }
                    } else {
                        // Fallback: translate one by one if batch fails
                        chunk.forEach { pair ->
                            allTranslatedLines[pair.second] = translateChunk(pair.first, from, to)
                        }
                    }
                }
            }

            return allTranslatedLines.toList()
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
                    val res = app2.get(url = "$BASEURL$from&tl=$to&dt=t&q=${Uri.encode(text)}")
                    val response: GoogleTranslationResponse = res.parsed()
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

        private fun chunkByLimit(fragments: List<Pair<String, Int>>): List<List<Pair<String, Int>>> {
            if (fragments.isEmpty()) return emptyList()
            val chunks = mutableListOf<List<Pair<String, Int>>>()
            var currentChunk = mutableListOf<Pair<String, Int>>()
            var currentLength = 0

            for (item in fragments) {
                val itemLength = Uri.encode(item.first + PARAGRAPH_DELIMITER).length
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
}
