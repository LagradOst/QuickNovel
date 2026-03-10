package com.lagradost.quicknovel

import android.content.Context
import android.util.Log
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.util.ResultCached

object LibraryHelper {

    var libraryData: Map<String, List<ResultCached>> = emptyMap()

    fun Context.setLibraryBooks() {
        val result = mutableMapOf<String, List<ResultCached>>()

        Log.d("LIBRARY_DEBUG", "Fetching library books...")



        // 3. Bookmarks grouped by ReadType
        val bookmarkStateKeys = getKeys(RESULT_BOOKMARK_STATE)
        val mapping = mutableMapOf<ReadType, MutableList<ResultCached>>()
        for (read in ReadType.values()) {
            if (read != ReadType.NONE) {
                mapping[read] = mutableListOf()
            }
        }

        for (key in bookmarkStateKeys) {
            val typeValue = getKey<Int>(key) ?: continue
            val type = ReadType.values().find { it.prefValue == typeValue } ?: continue
            val bookKey = key.replaceFirst(RESULT_BOOKMARK_STATE, RESULT_BOOKMARK)
            val book = getKey<ResultCached>(bookKey) ?: continue
            mapping[type]?.add(book)
        }

        mapping.forEach { (readType, books) ->
            Log.d("LIBRARY_DEBUG", "${readType.name}: ${books.size} books")
            result[readType.name] = books
        }


        // 1. Downloads
        val downloadKeys = getKeys(DOWNLOAD_FOLDER)
        val downloads = downloadKeys.mapNotNull { getKey<ResultCached>(it) }
        Log.d("LIBRARY_DEBUG", "Downloads: ${downloads.size} books")
        result["Downloads"] = downloads

        // 2. History
        val historyKeys = getKeys(HISTORY_FOLDER)
        val history = historyKeys.mapNotNull { getKey<ResultCached>(it) }
        Log.d("LIBRARY_DEBUG", "History: ${history.size} books")
        result["History"] = history

        Log.d("LIBRARY_DEBUG", "Finished fetching library. Sections: ${result.keys}")

        libraryData = result
    }

    fun getBookmarkForBook(title: String): String? {
        libraryData.forEach { (section, books) ->
            if(section!="History" && section!="Downloads")
            {
                books.forEach { book ->
                    if (book.name.equals(title, ignoreCase = true)) {
                        Log.d("LIBRARY_DEBUG", "Comparing: ${book.name} with $title")
                        // If the section maps to a ReadType, return it; otherwise null
                        return friendlyStatus(section)
                    }
                }
            }
            else if(section!="History"){
                books.forEach { book ->
                    if (book.name.equals(title, ignoreCase = true)) {
                        Log.d("LIBRARY_DEBUG", "Comparing: ${book.name} with $title")
                        // If the section maps to a ReadType, return it; otherwise null
                        return friendlyStatus(section)
                    }
                }
            }
        }
        return null // Not found in library
    }


    fun friendlyStatus(status: String): String {
        return when (status.uppercase()) {
            "PLAN_TO_READ" -> "Library"
            "DROPPED" -> "Dropped"
            "COMPLETED" -> "Completed"
            "ON_HOLD" -> "On Hold"
            "READING" -> "Library"
            "DOWNLOADS" -> "Library"
            "HISTORY" -> "History"
            else -> status // fallback to original if unknown
        }
    }


    enum class ChapterCountFilter(val displayName: String) {
        ALL("all"),
        LESS_THAN_100("<100"),
        GREATER_EQUAL_100(">100"),
        GREATER_EQUAL_200(">200"),
        GREATER_EQUAL_300(">300"),
        GREATER_EQUAL_400(">400"),
        GREATER_EQUAL_500(">500")
    }
    fun getChapterFiltersList(): List<Pair<String, String>> {
        return ChapterCountFilter.values().map { it.displayName to it.name }
    }
    fun isChapterCountInRange(filter: ChapterCountFilter, countString: String): Boolean {
        val count = countString.toIntOrNull() ?: 0
        return when (filter) {
            ChapterCountFilter.ALL -> true
            ChapterCountFilter.LESS_THAN_100 -> count < 100
            ChapterCountFilter.GREATER_EQUAL_100 -> count >= 100
            ChapterCountFilter.GREATER_EQUAL_200 -> count >= 200
            ChapterCountFilter.GREATER_EQUAL_300 -> count >= 300
            ChapterCountFilter.GREATER_EQUAL_400 -> count >= 400
            ChapterCountFilter.GREATER_EQUAL_500 -> count >= 500
        }
    }

    fun getLastReadChapterIndex(bookName: String): Int {
        val k=getKey<Int>(EPUB_CURRENT_POSITION, bookName) ?:0
        return k+1
    }
}
