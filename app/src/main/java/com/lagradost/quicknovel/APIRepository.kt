package com.lagradost.quicknovel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE
import com.lagradost.quicknovel.BookDownloader2Helper.IMPORT_SOURCE_PDF
import com.lagradost.quicknovel.BookDownloader2Helper.getFilenameIMG
import com.lagradost.quicknovel.BookDownloader2Helper.sanitizeFilename
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.logError
import com.lagradost.quicknovel.mvvm.safeApiCall
import com.lagradost.quicknovel.ui.download.DownloadFragment
import com.lagradost.quicknovel.util.Coroutines.threadSafeListOf
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jsoup.Jsoup
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class OnGoingSearch(
    val apiName: String,
    val data: Resource<List<SearchResponse>>
)

// This function is somewhat like preParseHtml
private fun String?.removeAds(): String? {
    if (this.isNullOrBlank()) return null
    return try {
        val document = Jsoup.parse(this)
        //document.select("style").remove() // Style might be good, but is removed in the internal reader
        document.select("small.ads-title").remove()
        document.select("script").remove()
        document.select("iframe").remove()
        document.select(".adsbygoogle").remove()

        // Remove aside https://html.spec.whatwg.org/multipage/sections.html#the-aside-element?
        // https://stackoverflow.com/questions/14384431/html-element-for-ad

        document.html()
    } catch (t: Throwable) {
        logError(t)
        this
    }
}


enum class SearchResponseOperation {
    Open,
    Stream,
    AskDelete,
    Delete,
    Metadata,
}

@Immutable
data class ImmutableSearchResponse @ExperimentalUuidApi constructor(
    val name: String,
    val apiName: String,
    val author: String? = null,

    val url: String,
    val posterUrl: String? = null,
    val rating: Int? = null,
    val latestChapterName: String? = null,
    val posterHeaders: ImmutableMap<String, String>? = null,
    val randomUuid: Uuid = Uuid.random(),
    val totalChapters: Int? = null,
    val downloadState: ImmutableDownloadState? = null,
    val synopsis: String? = null,
    val tags: ImmutableList<String>? = null,

    val id: Int? = null,
    val timeOfCached: Long,
    val timeOfChapterDownloaded: Long? = null,
    val timeOfPageOpened: Long? = null,
) {
    fun matchesQuery(query: String): Boolean =
        FuzzySearch.partialRatio(name.lowercase(), query) > 50

    val isImported: Boolean get() = (apiName == IMPORT_SOURCE || apiName == IMPORT_SOURCE_PDF)
    val imageRequest
        get() = @Composable {
            val context = LocalContext.current
            remember(context) {
                if (isImported) {
                    (context.filesDir.toString() + getFilenameIMG(
                        sanitizeFilename(apiName),
                        sanitizeFilename(author ?: ""),
                        sanitizeFilename(name)
                    )).toUri()
                } else {
                    ImageRequest.Builder(context)
                        .data(posterUrl)
                        .httpHeaders(NetworkHeaders.Builder().also { headerBuilder ->
                            posterHeaders?.forEach { (key, value) ->
                                headerBuilder[key] = value
                            }
                        }.build()) // Set the headers here
                        .crossfade(true)
                        .build()
                }
            }
        }

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun from(response: SearchResponse): ImmutableSearchResponse =
            ImmutableSearchResponse(
                name = response.name,
                url = response.url,
                posterUrl = response.posterUrl,
                rating = response.rating,
                latestChapterName = response.latestChapter,
                apiName = response.apiName,
                posterHeaders = response.posterHeaders?.toImmutableMap(),
                timeOfCached = System.currentTimeMillis(),
            )

        @OptIn(ExperimentalUuidApi::class)
        fun from(cache: ResultCached): ImmutableSearchResponse =
            ImmutableSearchResponse(
                name = cache.name,
                url = cache.source,
                posterUrl = cache.poster,
                posterHeaders = persistentMapOf(),
                apiName = cache.apiName,
                rating = cache.rating,
                id = cache.id,
                timeOfCached = cache.cachedTime,
                totalChapters = cache.totalChapters,
                author = cache.author,
                synopsis = cache.synopsis,
                timeOfPageOpened = getKey<Long>(
                    DOWNLOAD_EPUB_LAST_ACCESS,
                    cache.id.toString(),
                ) ?: 0
            )

        @OptIn(ExperimentalUuidApi::class)
        fun from(
            id: Int,
            cache: DownloadFragment.DownloadData,
            downloadState: ImmutableDownloadState? = null
        ) =
            ImmutableSearchResponse(
                name = cache.name,
                url = cache.source,
                posterUrl = cache.posterUrl,
                posterHeaders = persistentMapOf(),
                apiName = cache.apiName,
                rating = cache.rating,
                id = id,
                timeOfCached = cache.lastUpdated ?: System.currentTimeMillis(),
                timeOfChapterDownloaded = cache.lastDownloaded,
                author = cache.author,
                downloadState = downloadState,
                synopsis = cache.synopsis,
                tags = cache.tags?.toImmutableList(),
                timeOfPageOpened = getKey<Long>(
                    DOWNLOAD_EPUB_LAST_ACCESS,
                    id.toString(),
                ) ?: 0
            )
    }

    fun doAction(operation: SearchResponseOperation) {
        when (operation) {
            SearchResponseOperation.Open -> loadResult(url, apiName)
            SearchResponseOperation.Stream -> BookDownloader2.stream(this)
            SearchResponseOperation.Metadata -> {
                MainActivity.loadPreviewPage(this)
            }

            else -> throw NotImplementedError()
        }
    }
}


@Immutable
data class SearchResponseAction(
    val response: ImmutableSearchResponse,
    val operation: SearchResponseOperation,
) {
    fun doAction() {
        response.doAction(operation)
    }
}

@Immutable
data class ImmutableDownloadState(
    val state: DownloadState,
    // How many chapters/bytes much have we downloaded, not including skipped chapters
    val progress: Long,
    // How many have we actually downloaded
    val downloaded: Long,
    // How many is there in total
    val total: Long,
    val lastUpdatedMs: Long,
    val etaMs: Long?
) {
    val downloadPercentage get() = downloaded.toFloat() / maxOf(total, 1)
    val progressPercentage get() = progress.toFloat() / maxOf(total, 1)

    companion object {
        fun from(state: DownloadProgressState) = ImmutableDownloadState(
            state = state.state,
            progress = state.progress,
            downloaded = state.downloaded,
            total = state.total,
            lastUpdatedMs = state.lastUpdatedMs,
            etaMs = state.etaMs
        )
    }
}

@Immutable
data class ImmutableHeadMainPageResponse(
    val url: String,
    val list: ImmutableList<ImmutableSearchResponse>,
) {
    companion object {
        fun from(response: HeadMainPageResponse) = ImmutableHeadMainPageResponse(
            url = response.url,
            list = response.list.map(ImmutableSearchResponse::from).toImmutableList()
        )
    }
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
            try {
                if (api.hasRateLimit) {
                    api.rateLimitMutex.lock()
                }
                val fixedUrl = api.fixUrl(url)
                val lookingForHash = api.name to fixedUrl

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
            } finally {
                if (api.hasRateLimit) {
                    api.rateLimitMutex.unlock()
                }
            }
        }
    }

    suspend fun search(query: String): Resource<List<SearchResponse>> {
        return safeApiCall {
            api.search(query) ?: throw ErrorLoadingException("No data")
        }
    }

    suspend fun searchResult(query: String): Result<List<ImmutableSearchResponse>> = runCatching {
        api.search(query)?.map(ImmutableSearchResponse::from)
            ?: throw ErrorLoadingException("No data")
    }

    suspend fun loadMainPageResult(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): Result<ImmutableHeadMainPageResponse> = runCatching {
        ImmutableHeadMainPageResponse.from(api.loadMainPage(page, mainCategory, orderBy, tag))
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