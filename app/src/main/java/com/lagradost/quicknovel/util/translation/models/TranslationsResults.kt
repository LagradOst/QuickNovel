package com.lagradost.quicknovel.util.translation.models

data class TranslationResult(
    val translatedLines: List<String>,
    val failedChunks: List<FailedContext>
)

data class FailedContext(
    val originalIndex: Int,
    val text: String
)