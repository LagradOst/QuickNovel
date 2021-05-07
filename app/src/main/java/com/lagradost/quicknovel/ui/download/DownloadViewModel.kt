package com.lagradost.quicknovel.ui.download

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.quicknovel.*
import kotlinx.android.synthetic.main.fragment_downloads.*

class DownloadViewModel : ViewModel() {
    val cards: MutableLiveData<ArrayList<DownloadFragment.DownloadDataLoaded>> by lazy {
        MutableLiveData<ArrayList<DownloadFragment.DownloadDataLoaded>>()
    }
    val standardSotringMethod = LAST_ACCES_SORT
    var currentSortingMethod: MutableLiveData<Int> =
        MutableLiveData<Int>(DataStore.getKey(DOWNLOAD_SETTINGS,DOWNLOAD_SORTING_METHOD, standardSotringMethod)
            ?: standardSotringMethod)

    fun sortArray(arry: ArrayList<DownloadFragment.DownloadDataLoaded>, sortMethod: Int? = null): ArrayList<DownloadFragment.DownloadDataLoaded> {

        if(sortMethod != null) {
            currentSortingMethod.postValue(sortMethod)
        }

        return when (sortMethod ?: currentSortingMethod.value) {
            DEFAULT_SORT -> arry
            ALPHA_SORT -> {
                arry.sortBy { t -> t.name }
                arry
            }
            REVERSE_ALPHA_SORT -> {
                arry.sortBy { t -> t.name }
                arry.reverse()
                arry
            }
            DOWNLOADSIZE_SORT -> {
                arry.sortBy { t -> -t.downloadedCount }
                arry
            }
            REVERSE_DOWNLOADSIZE_SORT -> {
                arry.sortBy { t -> t.downloadedCount }
                arry
            }
            DOWNLOADPRECENTAGE_SORT -> {
                arry.sortBy { t -> -t.downloadedCount.toFloat() / t.downloadedTotal }
                arry
            }
            REVERSE_DOWNLOADPRECENTAGE_SORT -> {
                arry.sortBy { t -> t.downloadedCount.toFloat() / t.downloadedTotal }
                arry
            }
            LAST_ACCES_SORT -> {
                arry.sortBy { t -> -(DataStore.getKey<Long>(DOWNLOAD_EPUB_LAST_ACCESS, t.id.toString(), 0)!!) }
                arry
            }
            else -> arry
        }
    }

    fun loadData() {
        val arry = ArrayList<DownloadFragment.DownloadDataLoaded>()
        val keys = DataStore.getKeys(DOWNLOAD_FOLDER)
        for (k in keys) {
            val res =
                DataStore.getKey<DownloadFragment.DownloadData>(k) // THIS SHIT LAGS THE APPLICATION IF ON MAIN THREAD (IF NOT WARMED UP BEFOREHAND, SEE @WARMUP)
            if (res != null) {
                val localId = BookDownloader.generateId(res.apiName, res.author, res.name)
                val info = BookDownloader.downloadInfo(res.author, res.name, 100000, res.apiName)
                if (info != null && info.progress > 0) {
                    val state =
                        (if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped)!!
                    arry.add(DownloadFragment.DownloadDataLoaded(
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
                            DataStore.getKey(DOWNLOAD_TOTAL, localId.toString(), info.progress)!!), //IDK Bug fix ?
                        false,
                        "",
                        state,
                        info.id,
                    ))
                }
            }
        }
        cards.postValue(sortArray(arry))
    }

    fun sortData(sortMethod : Int? = null) {
        if (cards.value != null) {
            cards.postValue(sortArray(cards.value!!,sortMethod))
        }
    }

    fun removeActon(id : Int) {
        if(cards.value == null) return
        val arry = cards.value!!
        var index = 0
        for (res in arry) {
            if (res.id == id) {
                arry.removeAt(index)
                cards.postValue(arry)
                break
            }
            index++
        }
    }

    fun updateDownloadInfo(info: BookDownloader.DownloadNotification) {
        if(cards.value == null) return
        val arry = cards.value!!
        var index = 0
        for (res in arry) {
            if (res.id == info.id) {
                arry[index] =
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
                cards.postValue(arry)
                break
            }
            index++
        }
    }
}