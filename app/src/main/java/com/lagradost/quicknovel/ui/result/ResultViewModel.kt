package com.lagradost.quicknovel.ui.result

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BaseApplication.Companion.setKey
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.BookDownloader2Helper.generateId
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DOWNLOAD_EPUB_LAST_ACCESS
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DownloadActionType
import com.lagradost.quicknovel.DownloadProgressState
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.RESULT_BOOKMARK
import com.lagradost.quicknovel.RESULT_BOOKMARK_STATE
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ResultViewModel : ViewModel() {
    lateinit var repo: APIRepository

    var id: MutableLiveData<Int> = MutableLiveData<Int>(-1)
    var readState: MutableLiveData<ReadType> = MutableLiveData<ReadType>(ReadType.NONE)

    val api get() = repo
    val apiName get() = api.name

    val currentTabIndex: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    private val loadMutex = Mutex()
    private lateinit var load: LoadResponse
    private var loadId: Int = 0
    private var loadUrl: String = ""
    private var hasLoaded: Boolean = false

    val loadResponse: MutableLiveData<Resource<LoadResponse>> = MutableLiveData<Resource<LoadResponse>>()


    val reviews: MutableLiveData<ArrayList<UserReview>> by lazy {
        MutableLiveData<ArrayList<UserReview>>()
    }

    private val reviewPage: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    private val loadMoreReviewsMutex = Mutex()
    private fun loadMoreReviews(url: String) {
        viewModelScope.launch {
            if (loadMoreReviewsMutex.isLocked) return@launch
            loadMoreReviewsMutex.withLock {
                val loadPage = (reviewPage.value ?: 0) + 1
                when (val data = repo.loadReviews(url, loadPage, false)) {
                    is Resource.Success -> {
                        val moreReviews = data.value
                        val merged = ArrayList<UserReview>()
                        merged.addAll(reviews.value ?: ArrayList())
                        merged.addAll(moreReviews)
                        reviews.postValue(merged)
                        reviewPage.postValue(loadPage)
                    }

                    else -> {}
                }
            }
        }
    }

    fun openInBrowser() = viewModelScope.launch {
        loadMutex.withLock {
            if (loadUrl.isNotBlank()) return@launch
            val i = Intent(Intent.ACTION_VIEW)
            i.data = Uri.parse(loadUrl)
            activity?.startActivity(i)
        }
    }

    fun switchTab(pos: Int?) {
        val newPos = pos ?: return
        currentTabIndex.postValue(newPos)
        if (newPos == 1 && reviews.value.isNullOrEmpty()) {
            loadMoreReviews(verify = false)
        }
    }

    fun readEpub() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            addToHistory()
            BookDownloader2.readEpub(
                loadId,
                downloadState.value?.progress ?: return@launch,
                load.author,
                load.name,
                apiName
            )
        }
    }

    fun streamRead() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            addToHistory()
            BookDownloader2.stream(load, apiName)
        }
    }

    /** paused => resume,
     *  downloading => pause,
     *  done / pending => nothing,
     *  else => download
     * */
    fun downloadOrPause() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch

            BookDownloader2.downloadInfoMutex.withLock {
                BookDownloader2.downloadProgress[loadId]?.let { downloadState ->
                    when (downloadState.state) {
                        DownloadState.IsPaused -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Resume
                        )

                        DownloadState.IsDownloading -> BookDownloader2.addPendingAction(
                            loadId,
                            DownloadActionType.Pause
                        )

                        DownloadState.IsDone, DownloadState.IsPending -> {

                        }

                        else -> BookDownloader2.download(load, api)
                    }
                } ?: run {
                    BookDownloader2.download(load, api)
                }
            }
        }
    }


    private fun addToHistory() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            setKey(
                HISTORY_FOLDER, loadId.toString(), ResultCached(
                    loadUrl,
                    load.name,
                    apiName,
                    loadId,
                    load.author,
                    load.posterUrl,
                    load.tags,
                    load.rating,
                    load.data.size,
                    System.currentTimeMillis()
                )
            )
        }
    }

    // requireContext().setKey(DOWNLOAD_TOTAL, localId.toString(), res.data .size)
    // loadReviews()

    fun isInReviews(): Boolean {
        return currentTabIndex.value == 1
    }

    fun deleteAlert() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            val dialogClickListener = DialogInterface.OnClickListener { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        delete()
                    }

                    DialogInterface.BUTTON_NEGATIVE -> {
                    }
                }
            }
            val builder: AlertDialog.Builder = AlertDialog.Builder(activity ?: return@launch)
            builder.setMessage("This will permanently delete ${load.name}.\nAre you sure?")
                .setTitle("Delete").setPositiveButton("Delete", dialogClickListener)
                .setNegativeButton("Cancel", dialogClickListener).show()
        }
    }

    fun delete() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            BookDownloader2.deleteNovel(load.author, load.name, apiName)
        }
    }

    fun bookmark(state: Int) = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            setKey(
                RESULT_BOOKMARK_STATE, loadId.toString(), state
            )

            setKey(
                RESULT_BOOKMARK, loadId.toString(), ResultCached(
                    loadUrl,
                    load.name,
                    apiName,
                    loadId,
                    load.author,
                    load.posterUrl,
                    load.tags,
                    load.rating,
                    load.data.size,
                    System.currentTimeMillis()
                )
            )
        }

        readState.postValue(ReadType.fromSpinner(state))
    }

    fun share() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch

            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_SUBJECT, load.name)
            i.putExtra(Intent.EXTRA_TEXT, loadUrl)
            activity?.startActivity(Intent.createChooser(i, load.name))
        }
    }

    fun loadMoreReviews(verify : Boolean = true) = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch
            if(verify && currentTabIndex.value == 0) return@launch
            loadMoreReviews(loadUrl)
        }
    }

    init {
        // BookDownloader2.downloadDataChanged += ::progressDataChanged
        BookDownloader2.downloadProgressChanged += ::progressChanged
        BookDownloader2.downloadRemoved += ::downloadRemoved
    }

    override fun onCleared() {
        super.onCleared()
        BookDownloader2.downloadProgressChanged -= ::progressChanged
        //BookDownloader2.downloadDataChanged -= ::progressDataChanged
        BookDownloader2.downloadRemoved -= ::downloadRemoved
    }

    val downloadState: MutableLiveData<DownloadProgressState> by lazy {
        MutableLiveData<DownloadProgressState>(null)
    }

    private fun progressChanged(data: Pair<Int, DownloadProgressState>) =
        viewModelScope.launch {
            val (id, state) = data
            loadMutex.withLock {
                if (!hasLoaded || id != loadId) return@launch
                downloadState.postValue(state)
            }
        }

    /*fun progressDataChanged(data: Pair<Int, DownloadFragment.DownloadData>) =
        viewModelScope.launch {
            val (id, downloadData) = data
            loadMutex.withLock {
                if (!hasLoaded || id != loadId) return@launch

            }
        }*/

    private fun downloadRemoved(id: Int) = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded || id != loadId) return@launch
            insertZeroData()
        }
    }

    private fun insertZeroData() = viewModelScope.launch {
        loadMutex.withLock {
            if (!hasLoaded) return@launch

            BookDownloader2.downloadInfoMutex.withLock {
                val current = BookDownloader2.downloadProgress[loadId]
                if (current == null) {
                    val new = DownloadProgressState(
                        DownloadState.Nothing,
                        0,
                        load.data.size,
                        System.currentTimeMillis(),
                        null
                    )
                    BookDownloader2.downloadProgress[loadId] = new
                    downloadState.postValue(new)
                } else {
                    downloadState.postValue(current)
                }
            }
        }
    }

    fun initState(context: Context, apiName: String, url: String) = viewModelScope.launch {
        loadMutex.withLock {
            repo = Apis.getApiFromName(apiName)
            loadUrl = url
        }

        loadResponse.postValue(Resource.Loading(url))
        val data = repo.load(url)
        loadMutex.withLock {
            when (data) {
                is Resource.Success -> {
                    val res = data.value

                    load = res
                    loadUrl = res.url

                    val tid = generateId(res, apiName)
                    loadId = tid

                    readState.postValue(
                        ReadType.fromSpinner(
                            context.getKey(
                                RESULT_BOOKMARK_STATE, tid.toString()
                            )
                        )
                    )

                    setKey(
                        DOWNLOAD_EPUB_LAST_ACCESS, tid.toString(), System.currentTimeMillis()
                    )
                    hasLoaded = true

                    // insert a download progress if not found
                    insertZeroData()
                }

                else -> {}
            }
            loadResponse.postValue(data)
        }
    }
}