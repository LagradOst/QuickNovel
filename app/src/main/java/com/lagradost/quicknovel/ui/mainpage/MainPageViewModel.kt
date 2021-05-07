package com.lagradost.quicknovel.ui.mainpage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainPageResponse
import com.lagradost.quicknovel.ui.download.DownloadFragment

class MainPageViewModel : ViewModel() {
    val cards: MutableLiveData<ArrayList<MainPageResponse>> by lazy {
        MutableLiveData<ArrayList<MainPageResponse>>()
    }

    val api: MutableLiveData<MainAPI> by lazy {
        for (api in MainActivity.apis) {
            if (api.hasMainPage) {
                return@lazy MutableLiveData<MainAPI>(api)
            }
        }
        return@lazy MutableLiveData<MainAPI>()
    }

    val currentPage: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    val currentMainCategory: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(null)
    }
    val currentOrderBy: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(null)
    }
    val currentTag: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(null)
    }

    val currentUrl: MutableLiveData<String> by lazy {
        MutableLiveData<String>(null)
    }

    val isInSearch: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    val isSearchResults: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    fun search(query: String) {
        cards.postValue(ArrayList())
        currentPage.postValue(0)
        val api = api.value
        if (api != null) {
            isInSearch.postValue(true)
            cards.postValue(
                ArrayList(api.search(query)?.map { t ->
                    MainPageResponse(t.name,
                        t.url,
                        t.posterUrl,
                        t.rating,
                        t.latestChapter,
                        t.apiName,
                        ArrayList())
                } ?: ArrayList())
            )
            isSearchResults.postValue(true)
        }
    }

    fun load(
        page: Int?,
        mainCategory: Int?,
        orderBy: Int?,
        tag: Int?,
    ) {
        val cPage = page ?: ((currentPage.value ?: 0) + 1)
        if (cPage == 0)
            cards.postValue(ArrayList())

        val api = api.value
        if (api != null) {
            isInSearch.postValue(false)
            val load = api.loadMainPage(cPage + 1, // cPage starts at 0, load starts at 1
                if (mainCategory != null) api.mainCategories[mainCategory].second else null,
                if (orderBy != null) api.orderBys[orderBy].second else null,
                if (tag != null) api.tags[tag].second else null)
            val list = load?.response

            val copy = if (cPage == 0) ArrayList() else cards.value

            if (list != null && copy != null) {
                for (i in list) {
                    copy.add(i)
                }
            }

            if (load != null && list != null && list.size > 0) {
                currentUrl.postValue(load.url)
            }
            cards.postValue(copy)
        }

        isSearchResults.postValue(false)
        currentPage.postValue(cPage)
        currentTag.postValue(tag)
        currentOrderBy.postValue(orderBy)
        currentMainCategory.postValue(mainCategory)
    }
}