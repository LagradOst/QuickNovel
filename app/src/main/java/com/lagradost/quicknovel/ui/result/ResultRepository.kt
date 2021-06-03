package com.lagradost.quicknovel.ui.result

import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.safeApiCall

class ResultRepository(val api: MainAPI) {
    suspend fun load(url: String): Resource<LoadResponse> {
        return safeApiCall {
            api.load(url)
        }
    }

    suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean = false,
    ): Resource<ArrayList<UserReview>> {
        return safeApiCall {
            api.loadReviews(url, page, showSpoilers)
        }
    }
}