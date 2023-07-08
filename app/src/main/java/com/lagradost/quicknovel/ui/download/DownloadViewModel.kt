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
    private val cards: MutableLiveData<ArrayList<DownloadFragment.DownloadDataLoaded>> by lazy {
        MutableLiveData<ArrayList<DownloadFragment.DownloadDataLoaded>>()
    }

    val downloadCards: LiveData<ArrayList<DownloadFragment.DownloadDataLoaded>> = cards

    val normalCards: MutableLiveData<ArrayList<ResultCached>> by lazy {
        MutableLiveData<ArrayList<ResultCached>>()
    }

    val isOnDownloads: MutableLiveData<Boolean> = MutableLiveData(true)
    val currentReadType: MutableLiveData<ReadType?> = MutableLiveData(null)

    var currentSortingMethod: MutableLiveData<Int> =
        MutableLiveData<Int>()

    var currentNormalSortingMethod: MutableLiveData<Int> =
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
                cardsData[card.id]?.generating = true
                postCards()
            }
            BookDownloader2.readEpub(
                card.id,
                card.downloadedCount,
                card.author,
                card.name,
                card.apiName
            )
        } finally {
            setKey(DOWNLOAD_EPUB_LAST_ACCESS, card.id.toString(), System.currentTimeMillis())
            cardsDataMutex.withLock {
                cardsData[card.id]?.generating = false
                postCards()
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        val values = cardsDataMutex.withLock {
            cardsData.values
        }
        for (card in values) {
            if ((card.downloadedCount * 100 / card.downloadedTotal) > 90) {
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
        loadNormalData(currentReadType.value!!)
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
        currentSortingMethod.postValue(newSortingMethod)
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
        currentNormalSortingMethod.postValue(newSortingMethod)
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

    fun loadData() = viewModelScope.launch {
        currentReadType.postValue(null)
        isOnDownloads.postValue(true)
        cardsDataMutex.withLock {
            postCards()
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
                    ids.add(
                        key.replaceFirst(
                            RESULT_BOOKMARK_STATE,
                            RESULT_BOOKMARK
                        )
                    ) // I know kinda spaghetti
                }
            }
            ids.mapNotNull { id -> getKey<ResultCached>(id) }
        }
        normalCards.postValue(sortNormalArray(ArrayList(cards)))
    }

    private fun postCards(sortMethod: Int? = null) {
        cards.postValue(sortArray(ArrayList(cardsData.values), sortMethod))
    }

    fun sortData(sortMethod: Int? = null) = ioSafe {
        cardsDataMutex.withLock {
            postCards(sortMethod)
        }
    }

    fun sortNormalData(sortMethod: Int? = null) {
        normalCards.postValue(sortNormalArray(normalCards.value ?: return, sortMethod))
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
                cardsData[id]?.apply {
                    downloadedCount = state.progress
                    downloadedTotal = state.total
                    this.state = state.state
                    context?.let { ctx ->
                        this.ETA = state.eta(ctx)
                    }
                }
                postCards()
            }
        }

    private fun downloadRemoved(id: Int) = ioSafe {
        cardsDataMutex.withLock {
            cardsData -= id
            postCards()
        }
    }

    private fun progressDataChanged(data: Pair<Int, DownloadFragment.DownloadData>) = ioSafe {
        cardsDataMutex.withLock {
            val (id, value) = data
            cardsData[id]?.apply {
                source = value.source
                name = value.name
                author = value.author
                posterUrl = value.posterUrl
                rating = value.rating
                peopleVoted = value.peopleVoted
                views = value.views
                synopsis = value.synopsis
                tags = value.tags
                apiName = value.apiName
            } ?: run {
                cardsData[id] = DownloadFragment.DownloadDataLoaded(
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
            postCards()
        }
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
                postCards()
            }
        }
    }
}