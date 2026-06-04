package com.lagradost.quicknovel.util.Translation

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.safefile.closeQuietly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TranslationManager {
    private var translator: Translator? = null
    private var currentFrom: String? = null
    private var currentTo: String? = null
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
            // Close previous translator if language pair changed
            if (currentFrom != from || currentTo != to) {
                translator?.close()
            }

            val sourceTag = TranslateLanguage.fromLanguageTag(from) ?: TranslateLanguage.ENGLISH
            val targetTag = TranslateLanguage.fromLanguageTag(to) ?: TranslateLanguage.SPANISH

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceTag)
                .setTargetLanguage(targetTag)
                .build()

            val client = Translation.getClient(options)

            if (!isModelDownloaded(from, to)) {
                Tasks.await(client.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            }

            translator = client
            currentFrom = from
            currentTo = to
            return translator
        } catch (e: Exception) {
            logError(e)
            return null
        }
    }

    suspend fun translate(
        textList: List<String>,
        from: String,
        to: String,
        useOnline: Boolean = false,
        progress: suspend (Int, Int) -> Unit = { _, _ -> },
    ): List<String> {
        if (textList.isEmpty()) return emptyList()

        if (useOnline) return GoogleTranslateOnline.translate(textList, from, to, progress)

        val client = translator ?: prepareModel(from, to) ?: throw Exception("Model not available")

        // Safe paragraph processing loop to prevent single failures from crashing whole chapters
        return textList.mapIndexed { index, text ->
            if (!text.trim().any{it.isLetter()}) return@mapIndexed text
            try {
                progress(index, textList.size)
                Tasks.await(client.translate(text))
            } catch (e: Exception) {
                // Return original untranslated paragraph as fallback item on error
                text
            }
        }
    }
    /**
     * Reset translator
     * */
    fun release() {
        translator?.closeQuietly()
        translator = null
        currentFrom = null
        currentTo = null
    }
}