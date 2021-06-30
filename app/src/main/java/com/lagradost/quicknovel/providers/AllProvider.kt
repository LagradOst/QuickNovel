package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.mvvm.normalSafeApiCall
import com.lagradost.quicknovel.pmap
import com.lagradost.quicknovel.util.Apis.Companion.apis

class AllProvider : MainAPI() {
    override val name: String
        get() = "All Sources"

    var providersActive = HashSet<String>()

    override fun search(query: String): ArrayList<SearchResponse> {
        val list = apis.filter { a ->
            a.name != this.name && (providersActive.size == 0 || providersActive.contains(a.name))
        }.pmap { a ->
            normalSafeApiCall {
                a.search(query)
            }
        }

        var maxCount = 0
        var providerCount = 0
        for (res in list) {
            if (res != null && res.size > maxCount) {
                maxCount = res.size
            }
            providerCount++
        }

        if (providerCount == 0) throw Exception()
        if (maxCount == 0) return ArrayList()

        val result = ArrayList<SearchResponse>()
        for (i in 0..maxCount) {
            for (res in list) {
                if (res != null && i < res.size) {
                    result.add(res[i])
                }
            }
        }

        return result
    }
}