package com.lagradost.quicknovel.ui.result

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.quicknovel.BookDownloader
import com.lagradost.quicknovel.LoadResponse
import kotlin.concurrent.thread

class ResultViewModel(val repo: ResultRepository) : ViewModel() {
    var id: MutableLiveData<Int> = MutableLiveData<Int>(-1)

    val resultUrl: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }
    val apiName: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    val isLoaded: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    val loadResponse: MutableLiveData<LoadResponse?> by lazy {
        MutableLiveData<LoadResponse?>()
    }
    val downloadNotification: MutableLiveData<BookDownloader.DownloadNotification?> by lazy {
        MutableLiveData<BookDownloader.DownloadNotification?>()
    }

    fun initState(url: String, apiName: String) {
        this.resultUrl.value = url
        this.apiName.value = apiName

        BookDownloader.downloadNotification += {
            downloadNotification.postValue(it)
        }

        thread {
            repo.load(this.apiName.value!!, this.resultUrl.value!!
            ) { res ->
                isLoaded.postValue(true)
                loadResponse.postValue(res)
                if (res != null) {

                    val tid = BookDownloader.generateId(this.apiName.value!!,
                        res.author,
                        res.name)
                    id.postValue(tid)

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