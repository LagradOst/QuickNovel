package com.lagradost.quicknovel.ui.mainpage

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import kotlinx.coroutines.launch

class MainPageViewModel(private val repo: MainPageRepository) : ViewModel() {
    val api: APIRepository get() = repo.api

    /*private val searchCards: MutableLiveData<ArrayList<SearchResponse>> by lazy {
        MutableLiveData<ArrayList<SearchResponse>>()
    }*/

    private val infCards: MutableLiveData<List<SearchResponse>> by lazy {
        MutableLiveData<List<SearchResponse>>()
    }

    val currentCards: MutableLiveData<Resource<List<SearchResponse>>> by lazy {
        MutableLiveData<Resource<List<SearchResponse>>>()
    }

    private val currentPage: MutableLiveData<Int> by lazy {
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

    fun search(query: String) {
       // searchCards.postValue(ArrayList())
        currentCards.postValue(Resource.Loading())
        currentPage.postValue(0)
        isInSearch.postValue(true)
        viewModelScope.launch {
            val res = repo.search(query)
            currentCards.postValue(res)
        }
    }

    fun switchToMain() {
        infCards.value?.let {
            currentCards.postValue(Resource.Success(it))
        }
        isInSearch.postValue(false)
    }

    fun load(
        page: Int?,
        mainCategory: Int?,
        orderBy: Int?,
        tag: Int?,
    ) {
        val cPage = page ?: ((currentPage.value ?: 0) + 1)
        if (cPage == 0) {
            infCards.postValue(ArrayList())
            currentCards.postValue(Resource.Loading())
        }

        isInSearch.postValue(false)

        viewModelScope.launch {
            //val copy = if (cPage == 0) ArrayList() else cards.value
            val res = repo.loadMainPage(cPage + 1, mainCategory, orderBy, tag)
            val copy = ArrayList(infCards.value ?: listOf())

            when (res) {
                is Resource.Success -> {
                    val response = res.value
                    currentUrl.postValue(response.url)
                    val list = response.list
                    for (i in list) {
                        copy.add(i)
                    }
                    infCards.postValue(copy)
                    currentCards.postValue(Resource.Success(copy))
                }
                is Resource.Failure -> {
                    infCards.postValue(copy)
                    val result : Resource<List<SearchResponse>> = Resource.Failure(res.isNetworkError, res.errorCode,res.errorResponse,res.errorString)
                    currentCards.postValue(result)
                    // TODO SHOW UI
                }
                is Resource.Loading -> {
                    //NOTHING
                }
            }

            currentPage.postValue(cPage)
            currentTag.postValue(tag)
            currentOrderBy.postValue(orderBy)
            currentMainCategory.postValue(mainCategory)
        }
    }
}