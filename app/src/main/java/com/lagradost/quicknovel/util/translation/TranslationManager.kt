package com.lagradost.quicknovel.util.translation

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
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

fun Translator?.closeQuietly() = try { this?.close() }
    catch (e: Exception) {
        logError(e)
    }
class TranslationManager {
    private val onlineTranslator = GoogleTranslateOnline(MainActivity.app)
    private var translator: Translator? = null // MLKit Offline
    private var currentFrom: String? = null
    private var currentTo: String? = null
    private var currentAgent: TranslatorAgents = TranslatorAgents.OFFLINE

    /**
     * Config languages and current agent
     */
    fun setSettings(from: String, to: String, agent: TranslatorAgents) {
        if (currentFrom == from && currentTo == to && currentAgent == agent) return

        currentFrom = from
        currentTo = to
        currentAgent = agent
    }

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

    suspend fun prepareModel(from: String, to: String): Translator? {
        try {
            if (translator != null && currentFrom == from && currentTo == to)
                return translator

            translator?.closeQuietly()

            val sourceTag = TranslateLanguage.fromLanguageTag(from) ?: throw ErrorLoadingException("Language $from doesn't exist")
            val targetTag = TranslateLanguage.fromLanguageTag(to) ?: throw ErrorLoadingException("Language $to doesn't exist")
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceTag)
                .setTargetLanguage(targetTag)
                .build()
            val client = Translation.getClient(options)

            if (!isModelDownloaded(from, to))
                Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()))

            translator = client
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
        isHtml: Boolean = false,
        progress: suspend (Int, Int) -> Unit = { _, _ -> }
    ): String {
        if (text.isBlank()) return text
        
        return if (isHtml) {
            val doc = Jsoup.parse(text)
            val fragments = mutableListOf<String>()
            TranslationsUtils.htmlToTranslatableList(doc.body(), fragments)
            
            if (fragments.isEmpty()) return text
            
            val translatedList = translate(textList = fragments, isHtml = true, progress = progress)
            translatedList.joinToString("<br>\n")
        } else {
            val result = translate(listOf(text), false, progress)
            result.firstOrNull() ?: text
        }
    }

    /**
     * Translates a list of strings.
     */
    suspend fun translate(
        textList: List<String>,
        isHtml: Boolean = false,
        progress: suspend (Int, Int) -> Unit = { _, _ -> },
    ): List<String> {
        if (textList.isEmpty()) return emptyList()
        val from = currentFrom ?: throw Exception("Source language not set")
        val to = currentTo ?: throw Exception("Target language not set")

        return when (currentAgent) {
            TranslatorAgents.ONLINE -> {
                val result = onlineTranslator.translate(textList, from, to, isHtml, progress)
                onlineTranslator.fixFailures(result, from, to, isHtml = isHtml)
            }
            TranslatorAgents.OFFLINE -> { offlineTranslate(textList, from, to, isHtml, progress) }
        }
    }

    private suspend fun offlineTranslate(
        textList: List<String>,
        from: String,
        to: String,
        isHtml: Boolean = false,
        progress: suspend (Int, Int) -> Unit
    ): List<String> {
        val client = translator ?: prepareModel(from, to) ?: throw Exception("Offline model not available")
        return textList.mapIndexed { index, text ->
            if (!TranslationsUtils.isTranslatable(text, isHtml)) return@mapIndexed text
            progress(index + 1, textList.size)
            Tasks.await(client.translate(TranslationsUtils.sanitize(text)))
        }
    }
    private fun releaseOffline() {
        translator?.closeQuietly()
        translator = null
    }
    fun release() {
        releaseOffline()
        currentFrom = null
        currentTo = null
    }
}
