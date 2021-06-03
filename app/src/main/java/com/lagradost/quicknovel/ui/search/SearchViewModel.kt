package com.lagradost.quicknovel.ui.search

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import kotlinx.coroutines.launch

class SearchViewModel(private val repo: SearchRepository) : ViewModel() {
    val searchResults: MutableLiveData<Resource<ArrayList<SearchResponse>>> by lazy {
        MutableLiveData<Resource<ArrayList<SearchResponse>>>()
    }

    fun search(query: String) {
        viewModelScope.launch {
            searchResults.postValue(repo.search(query))
        }
    }
}