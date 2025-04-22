package com.lagradost.quicknovel.ui.download

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BaseApplication.Companion.context
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2Helper
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DOWNLOAD_NORMAL_SORTING_METHOD
import com.lagradost.quicknovel.DOWNLOAD_SETTINGS
import com.lagradost.quicknovel.DOWNLOAD_SORTING_METHOD
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

const val DEFAULT_SORT = 0
const val ALPHA_SORT = 1
const val REVERSE_ALPHA_SORT = 2
const val DOWNLOADSIZE_SORT = 3
const val REVERSE_DOWNLOADSIZE_SORT = 4
const val DOWNLOADPRECENTAGE_SORT = 5
const val REVERSE_DOWNLOADPRECENTAGE_SORT = 6
const val LAST_ACCES_SORT = 7
const val REVERSE_LAST_ACCES_SORT = 8

class DownloadViewModel : ViewModel() {

    val readList = arrayListOf(
        ReadType.READING,
        ReadType.ON_HOLD,
        ReadType.PLAN_TO_READ,
        ReadType.COMPLETED,
        ReadType.DROPPED,
    )

    val _pages: MutableLiveData<List<Page>> = MutableLiveData(null)
    val pages: LiveData<List<Page>> = _pages

    var currentTab: MutableLiveData<Int> =
        MutableLiveData<Int>()

    fun refreshCard(card: DownloadFragment.DownloadDataLoaded) = viewModelScope.launch {
        BookDownloader2.downloadFromCard(card)
    }

    fun pause(card: DownloadFragment.DownloadDataLoaded) {
        BookDownloader2.addPendingAction(card.id, DownloadActionType.Pause)
    }

    fun resume(card: DownloadFragment.DownloadDataLoaded) {
        BookDownloader2.addPendingAction(card.id, DownloadActionType.Resume)
    }

    fun load(card: ResultCached) {
        loadResult(card.source, card.apiName)
    }

    fun stream(card: ResultCached) {
        BookDownloader2.stream(card)
    }

    fun readEpub(card: DownloadFragment.DownloadDataLoaded) = ioSafe {
        try {
            cardsDataMutex.withLock {
                cardsData[card.id] = cardsData[card.id]?.copy(generating = true) ?: return@withLock
            }
            postCards()
            BookDownloader2.readEpub(
                card.id,
                card.downloadedCount,
                card.author,
                card.name,
                card.apiName,
                card.synopsis
            )
        } finally {
            setKey(DOWNLOAD_EPUB_LAST_ACCESS, card.id.toString(), System.currentTimeMillis())
            cardsDataMutex.withLock {
                cardsData[card.id] = cardsData[card.id]?.copy(generating = false) ?: return@withLock
            }
            postCards()
        }
    }

    fun refresh() = viewModelScope.launch {
        val values = cardsDataMutex.withLock {
            cardsData.values
        }
        for (card in values) {
            // avoid div by zero
            if (card.downloadedTotal <= 0 || (card.downloadedCount * 100 / card.downloadedTotal) > 90) {
                BookDownloader2.downloadFromCard(card)
            }
        }
    }

    fun showMetadata(card: DownloadFragment.DownloadDataLoaded) {
        MainActivity.loadPreviewPage(card)
    }

    fun showMetadata(card: ResultCached) {
        MainActivity.loadPreviewPage(card)
    }

    fun load(card: DownloadFragment.DownloadDataLoaded) {
        loadResult(card.source, card.apiName)
    }

    fun deleteAlert(card: ResultCached) {
        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        delete(card)
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
        val act = activity ?: return
        val builder: AlertDialog.Builder = AlertDialog.Builder(act)
        builder.setMessage(act.getString(R.string.permanently_delete_format).format(card.name))
            .setTitle(R.string.delete)
            .setPositiveButton(R.string.delete, dialogClickListener)
            .setNegativeButton(R.string.cancel, dialogClickListener)
            .show()
    }

    fun delete(card: ResultCached) {
        removeKey(RESULT_BOOKMARK, card.id.toString())
        removeKey(RESULT_BOOKMARK_STATE, card.id.toString())
        loadAllData()
    }

    fun deleteAlert(card: DownloadFragment.DownloadDataLoaded) {
        val dialogClickListener =
            DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        delete(card)
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
        val act = activity ?: return
        val builder: AlertDialog.Builder = AlertDialog.Builder(act)
        builder.setMessage(act.getString(R.string.permanently_delete_format).format(card.name))
            .setTitle(R.string.delete)
            .setPositiveButton(R.string.delete, dialogClickListener)
            .setNegativeButton(R.string.cancel, dialogClickListener)
            .show()
    }

    fun delete(card: DownloadFragment.DownloadDataLoaded) {
        BookDownloader2.deleteNovel(card.author, card.name, card.apiName)
    }

    private fun sortArray(
        currentArray: ArrayList<DownloadFragment.DownloadDataLoaded>,
        sortMethod: Int? = null,
    ): ArrayList<DownloadFragment.DownloadDataLoaded> {
        val newSortingMethod =
            sortMethod ?: getKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD) ?: DEFAULT_SORT
        setKey(DOWNLOAD_SETTINGS, DOWNLOAD_SORTING_METHOD, newSortingMethod)

        return when (newSortingMethod) {
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

            //DEFAULT_SORT, LAST_ACCES_SORT
            else -> {
                currentArray.sortBy { t ->
                    -(getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }
        }
    }

    private fun sortNormalArray(
        currentArray: ArrayList<ResultCached>,
        sortMethod: Int? = null,
    ): ArrayList<ResultCached> {
        val newSortingMethod =
            sortMethod ?: getKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD) ?: DEFAULT_SORT
        setKey(DOWNLOAD_SETTINGS, DOWNLOAD_NORMAL_SORTING_METHOD, newSortingMethod)

        return when (newSortingMethod) {
            ALPHA_SORT -> {
                currentArray.sortBy { t -> t.name }
                currentArray
            }

            REVERSE_ALPHA_SORT -> {
                currentArray.sortBy { t -> t.name }
                currentArray.reverse()
                currentArray
            }

            // DEFAULT_SORT, LAST_ACCES_SORT
            else -> {
                currentArray.sortBy { t ->
                    -(getKey<Long>(
                        DOWNLOAD_EPUB_LAST_ACCESS,
                        t.id.toString(),
                        0
                    )!!)
                }
                currentArray
            }
        }
    }

    fun loadAllData() = viewModelScope.launch {

        val mapping: HashMap<Int, ArrayList<ResultCached>> = hashMapOf(
            ReadType.PLAN_TO_READ.prefValue to arrayListOf(),
            ReadType.DROPPED.prefValue to arrayListOf(),
            ReadType.COMPLETED.prefValue to arrayListOf(),
            ReadType.ON_HOLD.prefValue to arrayListOf(),
            ReadType.READING.prefValue to arrayListOf(),
        )

        withContext(Dispatchers.IO) {
            val keys = getKeys(RESULT_BOOKMARK_STATE)
            for (key in keys ?: emptyList()) {
                val type = getKey<Int>(key) ?: continue
                val id = key.replaceFirst(
                    RESULT_BOOKMARK_STATE,
                    RESULT_BOOKMARK
                )
                val cached = getKey<ResultCached>(id) ?: continue
                mapping[type]?.add(cached)
            }
        }

        val pages = mutableListOf(
            getDownloadedCards(),
        )
        for (read in readList) {
            pages.add(
                Page(read.name, sortNormalArray(mapping[read.prefValue]!!)),
            )
        }
        _pages.postValue(pages)
    }

    suspend fun getDownloadedCards(): Page = Page(
        ReadType.NONE.name, cardsDataMutex.withLock {
            sortArray(ArrayList(cardsData.values))
        }
    )

    suspend fun postCards() {
        _pages.value?.let { data ->
            val list = CopyOnWriteArrayList(data)
            if (list.isEmpty()) {
                list.add(getDownloadedCards())
            } else {
                list[0] = getDownloadedCards()
            }
            _pages.postValue(list)
        }
    }

    init {
        BookDownloader2.downloadDataChanged += ::progressDataChanged
        BookDownloader2.downloadProgressChanged += ::progressChanged
        BookDownloader2.downloadDataRefreshed += ::downloadDataRefreshed
        BookDownloader2.downloadRemoved += ::downloadRemoved

        // just in case this runs way after other init that we don't miss downloadDataRefreshed
        downloadDataRefreshed(0)
    }

    override fun onCleared() {
        super.onCleared()
        BookDownloader2.downloadProgressChanged -= ::progressChanged
        BookDownloader2.downloadDataChanged -= ::progressDataChanged
        BookDownloader2.downloadDataRefreshed -= ::downloadDataRefreshed
        BookDownloader2.downloadRemoved -= ::downloadRemoved
    }

    private val cardsDataMutex = Mutex()
    private val cardsData: HashMap<Int, DownloadFragment.DownloadDataLoaded> = hashMapOf()

    private fun progressChanged(data: Pair<Int, DownloadProgressState>) =
        ioSafe {
            cardsDataMutex.withLock {
                val (id, state) = data
                val newState = state.eta(context ?: return@ioSafe)
                cardsData[id] = cardsData[id]?.copy(
                    downloadedCount = state.progress,
                    downloadedTotal = state.total,
                    state = state.state,
                    ETA = newState,
                ) ?: return@ioSafe
            }
            postCards()
        }

    private fun downloadRemoved(id: Int) = ioSafe {
        cardsDataMutex.withLock {
            cardsData -= id
        }
        postCards()
    }

    private fun progressDataChanged(data: Pair<Int, DownloadFragment.DownloadData>) = ioSafe {
        cardsDataMutex.withLock {
            val (id, value) = data
            cardsData[id] = cardsData[id]?.copy(
                source = value.source,
                name = value.name,
                author = value.author,
                posterUrl = value.posterUrl,
                rating = value.rating,
                peopleVoted = value.peopleVoted,
                views = value.views,
                synopsis = value.synopsis,
                tags = value.tags,
                apiName = value.apiName
            ) ?: run {
                DownloadFragment.DownloadDataLoaded(
                    source = value.source,
                    name = value.name,
                    author = value.author,
                    posterUrl = value.posterUrl,
                    rating = value.rating,
                    peopleVoted = value.peopleVoted,
                    views = value.views,
                    synopsis = value.synopsis,
                    tags = value.tags,
                    apiName = value.apiName,
                    downloadedCount = 0,
                    downloadedTotal = 0,
                    ETA = "",
                    state = DownloadState.Nothing,
                    id = id,
                    generating = false,
                )
            }
        }
        postCards()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun downloadDataRefreshed(_id: Int) = ioSafe {
        BookDownloader2.downloadInfoMutex.withLock {
            cardsDataMutex.withLock {
                BookDownloader2.downloadData.map { (key, value) ->
                    val info = BookDownloader2.downloadProgress[key] ?: return@map
                    cardsData[key] = DownloadFragment.DownloadDataLoaded(
                        source = value.source,
                        name = value.name,
                        author = value.author,
                        posterUrl = value.posterUrl,
                        rating = value.rating,
                        peopleVoted = value.peopleVoted,
                        views = value.views,
                        synopsis = value.synopsis,
                        tags = value.tags,
                        apiName = value.apiName,
                        downloadedCount = info.progress,
                        downloadedTotal = info.total,
                        ETA = context?.let { ctx -> info.eta(ctx) } ?: "",
                        state = info.state,
                        id = key,
                        generating = false
                    )
                }
            }
            postCards()
        }
    }
}
