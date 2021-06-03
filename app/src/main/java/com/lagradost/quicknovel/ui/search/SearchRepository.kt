package com.lagradost.quicknovel.ui.search

import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.util.Apis

class SearchRepository {
    suspend fun search(query : String) : Resource<ArrayList<SearchResponse>> {
        return safeApiCall {
            Apis.allApi.search(query)
        }
    }

    companion object {
        // Singleton instantiation you already know and love
        @Volatile
        private var instance: SearchRepository? = null

        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: SearchRepository().also { instance = it }
            }
    }
}