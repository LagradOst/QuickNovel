package com.lagradost.quicknovel.providers

import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.pmap
import java.util.concurrent.locks.ReentrantLock

class AllProvider : MainAPI() {
    override val name: String
        get() = "All Sources"
    private val mutex = ReentrantLock()
    override fun search(query: String): ArrayList<SearchResponse>? {
        val list = MainActivity.apis.filter { a -> a.name != this.name }.pmap { a ->
            a.search(query)
        }

        var maxCount = 0
        for (res in list) {
            if (res != null) {
                if (res.size > maxCount) {
                    maxCount = res.size
                }
            }
        }
        if (maxCount == 0) return null

        val result = ArrayList<SearchResponse>()
        for (i in 0..maxCount) {
            for (res in list) {
                if (res != null) {
                    if (i < res.size) {
                        result.add(res[i])
                    }
                }
            }
        }

        return result
    }
}