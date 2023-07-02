package com.lagradost.quicknovel.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.Apis.Companion.getApiProviderLangSettings

class HomeViewModel : ViewModel() {
    val homeApis: LiveData<List<MainAPI>> by lazy {
        MutableLiveData(
            let {
                val langs = getApiProviderLangSettings()
                Apis.apis.filter { api -> api.hasMainPage && (langs.contains(api.lang)) }
            }
        )
    }
}