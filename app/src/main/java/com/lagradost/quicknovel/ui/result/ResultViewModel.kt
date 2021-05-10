package com.lagradost.quicknovel.ui.result

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.quicknovel.*
import kotlin.concurrent.thread

class ResultViewModel(val repo: ResultRepository) : ViewModel() {
    var id: MutableLiveData<Int> = MutableLiveData<Int>(-1)

    val resultUrl: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
    val apiName: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
    val currentTabIndex: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    val isFailedConnection: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    val isLoaded: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    val loadResponse: MutableLiveData<LoadResponse?> by lazy {
        MutableLiveData<LoadResponse?>()
    }
    val downloadNotification: MutableLiveData<BookDownloader.DownloadNotification?> by lazy {
        MutableLiveData<BookDownloader.DownloadNotification?>()
    }

    val reviews: MutableLiveData<ArrayList<UserReview>> by lazy {
        MutableLiveData<ArrayList<UserReview>>()
    }
    val reviewPage: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }

    fun loadMoreReviews() {
        thread {
            val loadPage = (reviewPage.value ?: 0) + 1
            val api = MainActivity.getApiFromName(apiName.value!!)
            val moreReviews = api.loadReviews(resultUrl.value!!,
                loadPage,
                false) // API STARTS AT 0, BUT REQUEST STARTS AT 1
            if (moreReviews != null) {
                val merged = ArrayList<UserReview>()
                merged.addAll(reviews.value ?: ArrayList())
                merged.addAll(moreReviews)
                reviews.postValue(merged)
                reviewPage.postValue(loadPage + 1)
            }
        }
    }

    fun initState(url: String, apiName: String) {
        this.resultUrl.value = url
        this.apiName.value = apiName
        isFailedConnection.postValue(false)

        BookDownloader.downloadNotification += {
            if (it.id == id.value)
                downloadNotification.postValue(it)
        }

        thread {
            repo.load(this.apiName.value!!, this.resultUrl.value!!
            ) { res ->
                isLoaded.postValue(true)
                loadResponse.postValue(res)
                if (res == null) {
                    isFailedConnection.postValue(true)
                } else {
                    isFailedConnection.postValue(false)
                    val tid = BookDownloader.generateId(this.apiName.value!!,
                        res.author,
                        res.name)
                    id.postValue(tid)
                    DataStore.setKey(DOWNLOAD_EPUB_LAST_ACCESS, tid.toString(), System.currentTimeMillis())

                    val start = BookDownloader.downloadInfo(res.author, res.name, res.data.size, apiName)
                    if (start != null) {
                        downloadNotification.postValue(
                            BookDownloader.DownloadNotification(start.progress,
                                start.total,
                                tid,
                                "",
                                (if (BookDownloader.isRunning.containsKey(tid)) BookDownloader.isRunning[tid] else BookDownloader.DownloadType.IsStopped)
                                    ?: BookDownloader.DownloadType.IsStopped
                            ))
                    }
                }
            }
        }
    }
}