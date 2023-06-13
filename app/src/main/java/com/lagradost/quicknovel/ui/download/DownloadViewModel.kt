package com.lagradost.quicknovel.ui.download

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BookDownloader.downloadInfo
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.getKeys
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.toImmutableList

class DownloadViewModel : ViewModel() {
    val cards: MutableLiveData<ArrayList<DownloadFragment.DownloadDataLoaded>> by lazy {
        MutableLiveData<ArrayList<DownloadFragment.DownloadDataLoaded>>()
    }

    val normalCards: MutableLiveData<ArrayList<ResultCached>> by lazy {
        MutableLiveData<ArrayList<ResultCached>>()
    }

    val isOnDownloads: MutableLiveData<Boolean> = MutableLiveData(true)
    val currentReadType: MutableLiveData<ReadType?> = MutableLiveData(null)

    var currentSortingMethod: MutableLiveData<Int> =
        MutableLiveData<Int>()

    var currentNormalSortingMethod: MutableLiveData<Int> =
        MutableLiveData<Int>()

    fun refresh() {
      //  val copy = arrayListOf( *(cards.value ?: emptyList()).toImmutableList().map { it.copy(state = BookDownloader.DownloadType.IsPending) }.toTypedArray())
       // cards.postValue(copy)
        for (card in cards.value ?: emptyList()) {
            if ((card.downloadedCount * 100 / card.downloadedTotal) > 90) {
                DownloadHelper.updateDownloadFromCard(context ?: return, card)
            }
        }
    }

    private fun sortArray(
        currentArray: ArrayList<DownloadFragment.DownloadDataLoaded>,
        sortMethod: Int? = null,
    ): ArrayList<DownloadFragment.DownloadDataLoaded> {

        if (sortMethod != null) {
            currentSortingMethod.postValue(sortMethod!!)
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

    private fun sortNormalArray(
        currentArray: ArrayList<ResultCached>,
        sortMethod: Int? = null,
    ): ArrayList<ResultCached> {

        if (sortMethod != null) {
            currentNormalSortingMethod.postValue(sortMethod!!)
        }

        return when (sortMethod ?: currentNormalSortingMethod.value) {
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
            LAST_ACCES_SORT -> {
                currentArray.sortBy { t -> -(getKey<Long>(DOWNLOAD_EPUB_LAST_ACCESS, t.id.toString(), 0)!!) }
                currentArray
            }
            else -> currentArray
        }
    }

    fun loadNormalData(state: ReadType) = viewModelScope.launch {
        currentNormalSortingMethod.postValue(
            getKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD, LAST_ACCES_SORT)
                ?: LAST_ACCES_SORT
        )

        normalCards.postValue(ArrayList())
        isOnDownloads.postValue(false)
        currentReadType.postValue(state)

        val cards = withContext(Dispatchers.IO) {
            val ids = ArrayList<String>()

            val keys = getKeys(RESULT_BOOKMARK_STATE)
            for (key in keys ?: emptyList()) {
                if (getKey<Int>(key) == state.prefValue) {
                    ids.add(key.replaceFirst(RESULT_BOOKMARK_STATE, RESULT_BOOKMARK)) // I know kinda spaghetti
                }
            }
            ids.mapNotNull { id -> getKey<ResultCached>(id) }
        }
        normalCards.postValue(sortNormalArray(ArrayList(cards)))
    }

    fun loadData() = viewModelScope.launch {
        currentSortingMethod.postValue(
            getKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, LAST_ACCES_SORT)
                ?: LAST_ACCES_SORT
        )

        currentReadType.postValue(null)
        isOnDownloads.postValue(true)

        val newArray = ArrayList<DownloadFragment.DownloadDataLoaded>()
        val added = HashMap<String, Boolean>()

        withContext(Dispatchers.IO) {
            val keys = getKeys(DOWNLOAD_FOLDER)
            for (k in keys ?: emptyList()) {
                val res =
                    getKey<DownloadFragment.DownloadData>(k) // THIS SHIT LAGS THE APPLICATION IF ON MAIN THREAD (IF NOT WARMED UP BEFOREHAND, SEE @WARMUP)

                if (res != null) {
                    val localId = BookDownloader.generateId(res.apiName, res.author, res.name)
                    val info = activity?.downloadInfo(res.author, res.name, 100000, res.apiName)

                    if (info != null && info.progress > 0) {
                        if (added.containsKey(res.source)) continue // PREVENTS DUPLICATES
                        added[res.source] = true

                        val state =
                            (if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped)!!
                        newArray.add(
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
                                maxOf(
                                    info.progress,
                                    getKey(DOWNLOAD_TOTAL, localId.toString(), info.progress)!!
                                ), //IDK Bug fix ?
                                false,
                                "",
                                state,
                                info.id,
                            )
                        )
                    }
                }
            }
        }

        cards.postValue(sortArray(newArray))
    }

    fun sortData(sortMethod: Int? = null) {
        cards.postValue(sortArray(cards.value ?: return, sortMethod))
    }

    fun sortNormalData(sortMethod: Int? = null) {
        normalCards.postValue(sortNormalArray(normalCards.value ?: return, sortMethod))
    }

    private fun removeActon(id: Int) {
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

    init {
        BookDownloader.downloadNotification += ::updateDownloadInfo
        BookDownloader.downloadRemove += ::removeActon
    }

    override fun onCleared() {
        super.onCleared()
        BookDownloader.downloadNotification -= ::updateDownloadInfo
        BookDownloader.downloadRemove -= ::removeActon
    }

    private fun updateDownloadInfo(info: BookDownloader.DownloadNotification) {
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