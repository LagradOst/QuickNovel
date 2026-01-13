package com.lagradost.quicknovel.ui.mainpage

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.map
import com.lagradost.quicknovel.util.Apis
import kotlinx.coroutines.launch

class MainPageViewModel : ViewModel() {
    lateinit var repo: MainPageRepository
    val api: APIRepository get() = repo.api
    private var hasInit = false

    /*private val searchCards: MutableLiveData<ArrayList<SearchResponse>> by lazy {
        MutableLiveData<ArrayList<SearchResponse>>()
    }*/

    private val infCards: ArrayList<SearchResponse> = arrayListOf()
    private var oldResponse: Resource<SearchResponseList>? = null
    private var searchResponseListQueries : Int = 0

    data class SearchResponseList(
        val items: List<SearchResponse>,
        val pages: Int,
        val id : Int,
    )

    val currentCards: MutableLiveData<Resource<SearchResponseList>> by lazy {
        MutableLiveData<Resource<SearchResponseList>>()
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

    val loadingMoreItems: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    val currentUrl: MutableLiveData<String> by lazy {
        MutableLiveData<String>(null)
    }

    val isInSearch: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>(false)
    }

    fun openInBrowser() {
        try {
            val url = currentUrl.value
            if (url != null) {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(url)
                activity?.startActivity(i)
            }
        } catch (_: Throwable) {

        }
    }

    fun search(query: String) {
        if (isInSearch.value == false) {
            oldResponse = currentCards.value
        }

        // searchCards.postValue(ArrayList())
        currentCards.postValue(Resource.Loading())
        currentPage.postValue(0)
        isInSearch.postValue(true)
        viewModelScope.launch {
            val res = repo.search(query)
            currentCards.postValue(res.map { x -> SearchResponseList(items = x, pages = 1, id = ++searchResponseListQueries) })
        }
    }

    fun switchToMain() {
        if (isInSearch.value == false) return

        currentCards.postValue(
            oldResponse ?: Resource.Success(
                // this still gives a bug, works works 90% of the time so idc
                SearchResponseList(
                    infCards,
                    ((currentPage.value ?: 0) + 1) + 1,
                    ++searchResponseListQueries,
                )
            )
        )
        oldResponse = null
        isInSearch.postValue(false)
    }

    fun init(
        apiName: String, mainCategory: Int?,
        orderBy: Int?,
        tag: Int?
    ) {
        if (hasInit) return
        hasInit = true
        repo = MainPageRepository(Apis.getApiFromName(apiName))
        load(
            0,
            mainCategory,
            orderBy,
            tag
        )
    }

    fun load(
        page: Int?,
        mainCategory: Int?,
        orderBy: Int?,
        tag: Int?,
    ) {
        val cPage = page ?: ((currentPage.value ?: 0) + 1)
        if (cPage == 0) {
            infCards.clear()
            currentCards.postValue(Resource.Loading())
        }

        isInSearch.postValue(false)
        if (page != 0) {
            loadingMoreItems.postValue(true)
        }
        viewModelScope.launch {
            //val copy = if (cPage == 0) ArrayList() else cards.value
            when (val res = repo.loadMainPage(cPage + 1, mainCategory, orderBy, tag)) {
                is Resource.Success -> {
                    val response = res.value
                    currentUrl.postValue(response.url)
                    infCards.addAll(response.list)

                    currentCards.postValue(
                        Resource.Success(
                            SearchResponseList(
                                infCards,
                                cPage + 1,
                                ++searchResponseListQueries
                            )
                        )
                    )
                }

                is Resource.Failure -> {
                    val result: Resource<SearchResponseList> = Resource.Failure(
                        res.cause,
                        res.errorString
                    )
                    currentCards.postValue(result)
                }

                is Resource.Loading -> {
                    //NOTHING
                }
            }

            loadingMoreItems.postValue(false)

            currentPage.postValue(cPage)
            currentTag.postValue(tag)
            currentOrderBy.postValue(orderBy)
            currentMainCategory.postValue(mainCategory)
        }
    }
}