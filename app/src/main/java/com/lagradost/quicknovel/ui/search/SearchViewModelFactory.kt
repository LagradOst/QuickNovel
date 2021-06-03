package com.lagradost.quicknovel.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class SearchViewModelFactory(private val searchRepository: SearchRepository) : ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return SearchViewModel(searchRepository) as T
    }
}

fun provideSearchViewModelFactory(): SearchViewModelFactory {
    // ViewModelFactory needs a repository, which in turn needs a DAO from a database
    // The whole dependency tree is constructed right here, in one place
    val searchRepository = SearchRepository.getInstance()
    return SearchViewModelFactory(searchRepository)
}
