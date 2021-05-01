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

    fun load(
        page: Int,
        mainCategory: Int?,
        orderBy: Int?,
        tag: Int?,
    ) {
        if (page == 0)
            cards.postValue(ArrayList())

        val api = api.value
        if (api != null) {
            val list = api.loadMainPage(page,
                if (mainCategory != null) api.mainCategories[mainCategory].second else null,
                if (orderBy != null) api.orderBys[orderBy].second else null,
                if (tag != null) api.tags[tag].second else null)

            val copy = if(page == 0) ArrayList() else cards.value

            if (list != null && copy != null) {
                for (i in list) {
                    copy.add(i)
                }
            }
            cards.postValue(copy)
        }
    }
}