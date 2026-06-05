package com.lagradost.quicknovel.util.Translation.models

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

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
