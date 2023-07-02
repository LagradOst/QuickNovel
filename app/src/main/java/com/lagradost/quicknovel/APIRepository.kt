package com.lagradost.quicknovel

import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.normalSafeApiCall
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.util.Coroutines.threadSafeListOf

data class OnGoingSearch(
    val apiName: String,
    val data: Resource<List<SearchResponse>>
)

private fun String?.removeAds(): String? {
    if (this.isNullOrBlank()) return null
    return this.replace("(adsbygoogle = window.adsbygoogle || []).push({});", "")
}

class APIRepository(val api: MainAPI) {
    val unixTime: Long
        get() = System.currentTimeMillis() / 1000L

    companion object {
        var providersActive = HashSet<String>()

        data class SavedLoadResponse(
            val unixTime: Long,
            val response: LoadResponse,
            val hash: Pair<String, String>
        )

        private val cache = threadSafeListOf<SavedLoadResponse>()
        private var cacheIndex: Int = 0
        const val cacheSize = 20

        // 10min cache time should be plenty per session without fucking up anything for the user with outdated data
        const val cacheTimeSec: Int = 60 * 10
    }

    val name: String get() = api.name
    val mainUrl: String get() = api.mainUrl
    val hasReviews: Boolean get() = api.hasReviews
    val rateLimitTime: Long get() = api.rateLimitTime
    val hasMainPage: Boolean get() = api.hasMainPage

    val iconId: Int? get() = api.iconId
    val iconBackgroundId: Int get() = api.iconBackgroundId
    val mainCategories: List<Pair<String, String>> get() = api.mainCategories
    val orderBys: List<Pair<String, String>> get() = api.orderBys
    val tags: List<Pair<String, String>> get() = api.tags


    suspend fun load(url: String, allowCache: Boolean = true): Resource<LoadResponse> {
        return safeApiCall {
            val fixedUrl = api.fixUrl(url)
            val lookingForHash = Pair(api.name, fixedUrl)

            if (allowCache) {
                synchronized(cache) {
                    for (item in cache) {
                        // 10 min save
                        if (item.hash == lookingForHash && (unixTime - item.unixTime) < cacheTimeSec) {
                            return@safeApiCall item.response
                        }
                    }
                }
            }

            api.load(fixedUrl)?.also { response ->
                // Remove all blank tags as early as possible
                val add = SavedLoadResponse(unixTime, response, lookingForHash)
                if (allowCache) {
                    synchronized(cache) {
                        if (cache.size > cacheSize) {
                            cache[cacheIndex] = add // rolling cache
                            cacheIndex = (cacheIndex + 1) % cacheSize
                        } else {
                            cache.add(add)
                        }
                    }
                }
            } ?: throw ErrorLoadingException("No data")
        }
    }

    suspend fun search(query: String): Resource<List<SearchResponse>> {
        return safeApiCall {
            api.search(query) ?: throw ErrorLoadingException("No data")
        }
    }

    /**
     * Automatically strips adsbygoogle
     * */
    suspend fun loadHtml(url: String): String? {
        return try {
            api.loadHtml(api.fixUrl(url))?.removeAds()
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    suspend fun loadReviews(
        url: String,
        page: Int,
        showSpoilers: Boolean = false
    ): Resource<List<UserReview>> {
        return safeApiCall {
            api.loadReviews(url, page, showSpoilers)
        }
    }

    suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): Resource<HeadMainPageResponse> {
        return safeApiCall {
            api.loadMainPage(page, mainCategory, orderBy, tag)
        }
    }

}