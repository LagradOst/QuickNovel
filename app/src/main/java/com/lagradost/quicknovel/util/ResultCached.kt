package com.lagradost.quicknovel.util

import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.RESULT_BOOKMARK
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

    override fun hashCode(): Int {
        return id
    }
    val currentTotalChapters:Int get() = (
            getKey(RESULT_BOOKMARK, this.id.toString()) as? ResultCached
            )?.totalChapters ?: totalChapters
    val lastChapterRead:Int get() = getKey<Int>(EPUB_CURRENT_POSITION, this.name)?.let{it+1}?:0
}