package com.lagradost.quicknovel.ui.result

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.*
import com.lagradost.quicknovel.BookDownloader.downloadInfo
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.util.Apis
import kotlinx.coroutines.launch

class ResultViewModel : ViewModel() {
    lateinit var repo: APIRepository

    var id: MutableLiveData<Int> = MutableLiveData<Int>(-1)
    var readState: MutableLiveData<ReadType> = MutableLiveData<ReadType>(ReadType.NONE)

    val api get() = repo
    val apiName get() = api.name

    val currentTabIndex: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    val loadResponse: MutableLiveData<Resource<LoadResponse>> by lazy {
        MutableLiveData<Resource<LoadResponse>>()
    }

    val downloadNotification: MutableLiveData<BookDownloader.DownloadNotification?> by lazy {
        MutableLiveData<BookDownloader.DownloadNotification?>()
    }

    val reviews: MutableLiveData<ArrayList<UserReview>> by lazy {
        MutableLiveData<ArrayList<UserReview>>()
    }
    private val reviewPage: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    fun loadMoreReviews(url: String) {
        viewModelScope.launch {
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

    fun initState(context: Context, apiName: String, url: String) {
        repo = Apis.getApiFromName(apiName)

        BookDownloader.downloadNotification += {
            if (it.id == id.value)
                downloadNotification.postValue(it)
        }

        viewModelScope.launch {
            loadResponse.postValue(Resource.Loading(url))
            val data = repo.load(url)
            loadResponse.postValue(data)
            when (data) {
                is Resource.Success -> {
                    val res = data.value
                    val tid = BookDownloader.generateId(res, apiName)
                    readState.postValue(
                        ReadType.fromSpinner(
                            context.getKey(
                                RESULT_BOOKMARK_STATE,
                                tid.toString()
                            )
                        )
                    )

                    id.postValue(tid)

                    val start = context.downloadInfo(res.author, res.name, res.data.size, apiName)
                    if (start != null) {
                        downloadNotification.postValue(
                            BookDownloader.DownloadNotification(
                                start.progress,
                                start.total,
                                tid,
                                "",
                                (if (BookDownloader.isRunning.containsKey(tid)) BookDownloader.isRunning[tid] else BookDownloader.DownloadType.IsStopped)
                                    ?: BookDownloader.DownloadType.IsStopped
                            )
                        )
                    }
                }
                is Resource.Failure -> {

                }
                is Resource.Loading -> {

                }
            }
        }
    }
}