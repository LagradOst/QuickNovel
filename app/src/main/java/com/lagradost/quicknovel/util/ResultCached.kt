package com.lagradost.quicknovel.util

import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.img

data class ResultCached(
    val source : String,
    val name: String,
    val apiName : String,
    val id : Int,
    val author : String?,
    val poster: String?,
    val tags : List<String>?,
    val rating : Int?,
    val totalChapters : Int,
    val cachedTime : Long, // milliseconds
    val synopsis : String? = null
) {
    val image : UiImage? get() = img(poster)
}