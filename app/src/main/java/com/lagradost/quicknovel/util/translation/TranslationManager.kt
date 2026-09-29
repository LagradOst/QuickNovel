package com.lagradost.quicknovel.util.translation

import androidx.core.text.HtmlCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.translation.models.TranslatorAgents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

fun Translator?.closeQuietly() {
    try {
        this?.close()
    } catch (e: Exception) {
        logError(e)
    }
}

class TranslationManager {
    private val onlineTranslator by lazy { GoogleTranslateOnline() }
    private var translator: Translator? = null // MLKit Offline
    private var cachedFrom: String? = null
    private var cachedTo: String? = null

    suspend fun isModelDownloaded(source: String, target: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelManager = RemoteModelManager.getInstance()
            val sDownloaded = if (source == "en") true
            else Tasks.await(modelManager.isModelDownloaded(
                TranslateRemoteModel.Builder(source).build()
            ))
            val tDownloaded = if (target == "en") true
            else Tasks.await(modelManager.isModelDownloaded(
                TranslateRemoteModel.Builder(target).build()
            ))
            return@withContext sDownloaded && tDownloaded
        } catch (e: Exception) {
            logError(e)
            return@withContext false
        }
    }

    suspend fun prepareModel(from: String, to: String, downloadIfNeeded: Boolean = true): Translator? {
        try {
            if (translator != null && cachedFrom == from && cachedTo == to) {
                return translator
            }

            releaseOffline()

            val sourceTag = TranslateLanguage.fromLanguageTag(from) ?: throw ErrorLoadingException("Language $from doesn't exist")
            val targetTag = TranslateLanguage.fromLanguageTag(to) ?: throw ErrorLoadingException("Language $to doesn't exist")

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceTag)
                .setTargetLanguage(targetTag)
                .build()

            val client = Translation.getClient(options)

            if (downloadIfNeeded && !isModelDownloaded(from, to)) {
                Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            }

            translator = client
            cachedFrom = from
            cachedTo = to
            return translator
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    /**
     * Translates a single string. If isHtml is true, it will split, translate fragments, and join.
     */
    suspend fun translate(
        text: String,
        from: String,
        to: String,
        agent: TranslatorAgents,
        isHtml: Boolean = false,
        progress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): String {
        if (text.isBlank()) return text

        return if (isHtml) {
            val doc = Jsoup.parse(text)
            val fragments = mutableListOf<String>()
            TranslationsUtils.htmlToTranslatableList(doc.body(), fragments)

            if (fragments.isEmpty()) return text

            val translatedList = translate(
                textList = fragments,
                from = from,
                to = to,
                agent = agent,
                isHtml = true,
                progress = progress
            )
            translatedList.joinToString("<br>\n")
        } else {
            val result = translate(
                textList = listOf(text),
                from = from,
                to = to,
                agent = agent,
                isHtml = false,
                progress = progress
            )
            result.firstOrNull() ?: text
        }
    }

    /**
     * Translates a list of strings.
     */
    suspend fun translate(
        textList: List<String>,
        from: String,
        to: String,
        agent: TranslatorAgents,
        isHtml: Boolean = false,
        progress: suspend (Int, Int) -> Unit = { _, _ -> },
    ): List<String> {
        if (textList.isEmpty()) return emptyList()

        return when (agent) {
            TranslatorAgents.ONLINE -> {
                val result = onlineTranslator.translate(textList, from, to, isHtml, progress)
                onlineTranslator.fixFailures(result, from, to, isHtml = isHtml)
            }

            TranslatorAgents.OFFLINE -> {
                offlineTranslate(textList, from, to, isHtml, progress)
            }
        }
    }

    private suspend fun offlineTranslate(
        textList: List<String>,
        from: String,
        to: String,
        isHtml: Boolean = false,
        progress: suspend (Int, Int) -> Unit
    ): List<String> {
        val client = prepareModel(from, to) ?: throw Exception("Offline model not available")
        return textList.mapIndexed { index, text ->
            val textToTranslate = TranslationsUtils.isTranslatable(text, isHtml) ?: return@mapIndexed text
            val decodedText = HtmlCompat.fromHtml(textToTranslate, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            val translatedText = Tasks.await(client.translate(decodedText))
            progress(index + 1, textList.size)
            translatedText.replace("<", "&lt;").replace(">", "&gt;")
        }
    }

    private fun releaseOffline() {
        translator?.closeQuietly()
        translator = null
        cachedFrom = null
        cachedTo = null
    }

    fun release() {
        releaseOffline()
    }
}
