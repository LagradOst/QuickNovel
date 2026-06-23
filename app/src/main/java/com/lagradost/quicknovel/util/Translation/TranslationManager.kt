package com.lagradost.quicknovel.util.Translation

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.lagradost.quicknovel.ErrorLoadingException
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

            val sourceTag = TranslateLanguage.fromLanguageTag(from) ?: throw ErrorLoadingException("Language doesn't exist")
            val targetTag = TranslateLanguage.fromLanguageTag(to) ?: throw ErrorLoadingException("Language doesn't exist")

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

        return if (useOnline)  GoogleTranslateOnline.translate(textList, from, to, progress)
        else offlineTranslate(textList, from, to, progress)
    }
    private suspend fun offlineTranslate(
        textList: List<String>,
        from: String,
        to: String,
        progress: suspend (Int, Int) -> Unit = { _, _ -> }): List<String>
    {
        val client = translator ?: prepareModel(from, to) ?: throw Exception("Offline model not available")
        return textList.mapIndexed { index, text ->
            //Translation is resource-intensive, so only translate when the text is not just a set of non-letter characters
            if (!text.trim().any { it.isLetter() }) return@mapIndexed text
            progress(index, textList.size)
            Tasks.await(client.translate(text))
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