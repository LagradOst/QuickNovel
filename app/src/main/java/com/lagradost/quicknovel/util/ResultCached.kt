package com.lagradost.quicknovel.util

import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.EPUB_CURRENT_POSITION
import com.lagradost.quicknovel.RESULT_BOOKMARK_READINGPROGRESS
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
    var totalChapters : Int,
    val cachedTime : Long, // milliseconds
    val synopsis : String? = null
) {
    val image : UiImage? get() = img(poster)

    override fun hashCode(): Int {
        return id
    }
}
data class ReadingProgressCached(val novel: ResultCached,
                               val lastUpdated : Long = getKey<Long>(RESULT_BOOKMARK_READINGPROGRESS, novel.name)?:0,
                               val lastChapterRead: Int = getKey<Int>(EPUB_CURRENT_POSITION, novel.name)?.let{it+1}?:0,
                               val totalChapters: Int = novel.totalChapters){
    val limitCached = 15//minutes
    fun isUpToDate(): Boolean = lastUpdated < (System.currentTimeMillis() - (limitCached * 60 * 1000))
}