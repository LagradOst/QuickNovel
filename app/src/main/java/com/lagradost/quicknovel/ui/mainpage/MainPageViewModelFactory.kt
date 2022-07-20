package com.lagradost.quicknovel.ui.mainpage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName

class MainPageViewModelFactory(private val mainPageRepository: MainPageRepository) :
    ViewModelProvider.NewInstanceFactory() {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainPageViewModel(mainPageRepository) as T
    }
}

fun provideMainPageViewModelFactory(apiName: String): MainPageViewModelFactory {
    // ViewModelFactory needs a repository, which in turn needs a DAO from a database
    // The whole dependency tree is constructed right here, in one place
    val mainPageRepository = MainPageRepository(getApiFromName(apiName))
    return MainPageViewModelFactory(mainPageRepository)
}
