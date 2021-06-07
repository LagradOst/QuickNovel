package com.lagradost.quicknovel.ui.download

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BookDownloader.downloadInfo
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.getKeys

class DownloadViewModel : ViewModel() {
    val cards: MutableLiveData<ArrayList<DownloadFragment.DownloadDataLoaded>> by lazy {
        MutableLiveData<ArrayList<DownloadFragment.DownloadDataLoaded>>()
    }

    var currentSortingMethod: MutableLiveData<Int> =
        MutableLiveData<Int>()

    private fun Context.sortArray(
        currentArray: ArrayList<DownloadFragment.DownloadDataLoaded>,
        sortMethod: Int? = null,
    ): ArrayList<DownloadFragment.DownloadDataLoaded> {

        if (sortMethod != null) {
            currentSortingMethod.postValue(sortMethod)
        }

        return when (sortMethod ?: currentSortingMethod.value) {
            DEFAULT_SORT -> currentArray
            ALPHA_SORT -> {
                currentArray.sortBy { t -> t.name }
                currentArray
            }
            REVERSE_ALPHA_SORT -> {
                currentArray.sortBy { t -> t.name }
                currentArray.reverse()
                currentArray
            }
            DOWNLOADSIZE_SORT -> {
                currentArray.sortBy { t -> -t.downloadedCount }
                currentArray
            }
            REVERSE_DOWNLOADSIZE_SORT -> {
                currentArray.sortBy { t -> t.downloadedCount }
                currentArray
            }
            DOWNLOADPRECENTAGE_SORT -> {
                currentArray.sortBy { t -> -t.downloadedCount.toFloat() / t.downloadedTotal }
                currentArray
            }
            REVERSE_DOWNLOADPRECENTAGE_SORT -> {
                currentArray.sortBy { t -> t.downloadedCount.toFloat() / t.downloadedTotal }
                currentArray
            }
            LAST_ACCES_SORT -> {
                currentArray.sortBy { t -> -(getKey<Long>(DOWNLOAD_EPUB_LAST_ACCESS, t.id.toString(), 0)!!) }
                currentArray
            }
            else -> currentArray
        }
    }

    fun loadData(context: Context) {
        currentSortingMethod.postValue(context.getKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, LAST_ACCES_SORT)
            ?: LAST_ACCES_SORT)

        val newArray = ArrayList<DownloadFragment.DownloadDataLoaded>()
        val keys = context.getKeys(DOWNLOAD_FOLDER)
        val added = HashMap<String, Boolean>()

        for (k in keys) {
            val res =
                context.getKey<DownloadFragment.DownloadData>(k) // THIS SHIT LAGS THE APPLICATION IF ON MAIN THREAD (IF NOT WARMED UP BEFOREHAND, SEE @WARMUP)

            if (res != null) {
                val localId = BookDownloader.generateId(res.apiName, res.author, res.name)
                val info = context.downloadInfo(res.author, res.name, 100000, res.apiName)

                if (info != null && info.progress > 0) {
                    if (added.containsKey(res.source)) continue // PREVENTS DUPLICATES
                    added[res.source] = true

                    val state =
                        (if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped)!!
                    newArray.add(DownloadFragment.DownloadDataLoaded(
                        res.source,
                        res.name,
                        res.author,
                        res.posterUrl,
                        res.rating,
                        res.peopleVoted,
                        res.views,
                        res.Synopsis,
                        res.tags,
                        res.apiName,
                        info.progress,
                        maxOf(info.progress,
                            context.getKey(DOWNLOAD_TOTAL, localId.toString(), info.progress)!!), //IDK Bug fix ?
                        false,
                        "",
                        state,
                        info.id,
                    ))
                }
            }
        }
        cards.postValue(context.sortArray(newArray))
    }

    fun sortData(context: Context, sortMethod: Int? = null) {
        cards.postValue(context.sortArray(cards.value ?: return, sortMethod))
    }

    fun removeActon(id: Int) {
        val copy = cards.value ?: return
        var index = 0
        for (res in copy) {
            if (res.id == id) {
                copy.removeAt(index)
                cards.postValue(copy)
                break
            }
            index++
        }
    }

    fun updateDownloadInfo(info: BookDownloader.DownloadNotification) {
        val copy = cards.value ?: return
        var index = 0
        for (res in copy) {
            if (res.id == info.id) {
                copy[index] =
                    DownloadFragment.DownloadDataLoaded(
                        res.source,
                        res.name,
                        res.author,
                        res.posterUrl,
                        res.rating,
                        res.peopleVoted,
                        res.views,
                        res.Synopsis,
                        res.tags,
                        res.apiName,
                        info.progress,
                        maxOf(info.progress, info.total), //IDK Bug fix ?
                        true,
                        info.ETA,
                        info.state,
                        res.id,
                    )
                cards.postValue(copy)
                break
            }
            index++
        }
    }
}