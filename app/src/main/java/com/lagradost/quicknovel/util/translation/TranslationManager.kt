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
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.util.translation.models.TranslatorAgents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException

fun Translator?.closeQuietly() {
    try {
        this?.close()
    } catch (e: Exception) {
        logError(e)
    }
}

class TranslationManager {
    private var translator: Translator? = null // MLKit Offline
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

    suspend fun prepareModel(from: String, to: String, downloadIfNeeded: Boolean = true): Translator? {
        try {
            if (translator != null && currentFrom == from && currentTo == to) {
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
        agent: TranslatorAgents,
        progress: suspend (Int, Int) -> Unit = { _, _ -> },
    ): List<String> {
        if (textList.isEmpty()) return emptyList()

        return when (agent) {
            TranslatorAgents.ONLINE -> {
                GoogleTranslateOnline.onlineTranslate(textList, from, to, progress)
            }

            TranslatorAgents.OFFLINE -> {
                offlineTranslate(textList, from, to, progress)
            }
        }
    }

    private suspend fun offlineTranslate(
        textList: List<String>,
        from: String,
        to: String,
        progress: suspend (Int, Int) -> Unit
    ): List<String> {
        val client = prepareModel(from, to) ?: throw Exception("Offline model not available")
        return textList.mapIndexed { index, text ->
            if (!TranslationsUtils.isTranslatable(text, false)) return@mapIndexed text
            
            progress(index + 1, textList.size)
            try {
                Tasks.await(client.translate(TranslationsUtils.sanitize(text)))
            } catch (t: ExecutionException) {
                throw t.cause ?: t
            }
        }
    }

    fun releaseOffline() {
        translator?.closeQuietly()
        translator = null
        currentFrom = null
        currentTo = null
    }

    fun release() {
        releaseOffline()
    }
}
