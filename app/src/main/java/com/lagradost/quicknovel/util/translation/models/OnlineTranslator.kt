package com.lagradost.quicknovel.util.translation.models

interface OnlineTranslator {
    suspend fun translate(
        textList: List<String>,
        from: String,
        to: String,
        isHtml: Boolean,
        progress: suspend (Int, Int) -> Unit
    ): TranslationResult
}
