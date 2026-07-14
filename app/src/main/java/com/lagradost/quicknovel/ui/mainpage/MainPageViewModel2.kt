package com.lagradost.quicknovel.ui.mainpage

import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.compose.ActionHandler
import com.lagradost.quicknovel.compose.DefaultEffectContainer
import com.lagradost.quicknovel.compose.DefaultStateContainer
import com.lagradost.quicknovel.compose.EffectContainer
import com.lagradost.quicknovel.compose.StateContainer
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

@Immutable
data class MainPageState(
    val openQuery: Boolean = false,
    val filter: FilterState,
    val filterVisual: FilterQueryVisual = FilterQueryVisual(),
    val query: QueryState = QueryState(),
    val dialog: MainPageDialog? = null,
)

@Immutable
data class QueryState(
    val loading: Boolean = true,
    val query: String = "",
    val items: PersistentList<SearchResponse> = persistentListOf(),
    val error: Throwable? = null,
)

@Immutable
data class FilterState(
    val loading: Boolean = true,
    val items: PersistentList<SearchResponse> = persistentListOf(),
    val error: Throwable? = null,
    val url: String = "",
    val query: FilterQuery,
)

@Immutable
data class FilterQueryVisual(
    val category: String? = null,
    val orderBy: String? = null,
    val tag: String? = null,
)

@Immutable
data class FilterQuery(
    val page: Int = 0,
    val category: Int = 0,
    val orderBy: Int = 0,
    val tag: Int = 0,
)

enum class DialogType {
    Tags,
    Category,
    OrderBy
}

@Immutable
data class MainPageDialog(
    val selected: Int,
    val options: PersistentList<String>,
    val type: DialogType,
)

sealed class MainPageAction {
    data class ResultAction(val data: SearchResponse, val operation: SearchOperation) :
        MainPageAction()

    data class Search(val data: String) : MainPageAction()
    object Expand : MainPageAction()
    data class OpenInBrowser(val url: String) : MainPageAction()
    object Back : MainPageAction()
    object Dismiss : MainPageAction()
    data class OpenDialog(val type: DialogType) : MainPageAction()
    data class SelectDialog(val type: DialogType, val selected: Int) : MainPageAction()
}

enum class SearchOperation {
    Open,
    Metadata,
}

sealed class MainPageEffect {
    data class ErrorLoading(val error: Throwable) : MainPageEffect()
}

class MainPageViewModel2(
    val api: APIRepository,
    val initQuery: FilterQuery,
    stateContainer: StateContainer<MainPageState> = DefaultStateContainer(MainPageState(
        filter = FilterState(query = initQuery)
    )),
    effectContainer: EffectContainer<MainPageEffect> = DefaultEffectContainer(),
): ViewModel(),
    StateContainer<MainPageState> by stateContainer,
    EffectContainer<MainPageEffect> by effectContainer,
    ActionHandler<MainPageAction>
{
    companion object {
        fun provideFactory(bundle: Bundle) = viewModelFactory {
            initializer {
                val mainCategory = bundle.getInt("mainCategory", 0)
                val orderBy = bundle.getInt("orderBy", 0)
                val tag = bundle.getInt("tag", 0)
                val apiName = bundle.getString("apiName")!!
                MainPageViewModel2(
                    getApiFromName(apiName),
                    FilterQuery(page = 0, category = mainCategory, tag = tag, orderBy = orderBy)
                )
            }
        }
    }

    private val expandMutex = Mutex()

    init {
        viewModelScope.launch {
            expand()
        }
    }

    suspend fun expand() = withContext(Dispatchers.IO) {
        val from = state.value.filter.query
        val fromList = state.value.filter.items

        try {
            expandMutex.lock()
            if (from != state.value.filter.query) return@withContext

            val mainCategory = api.mainCategories.getOrNull(from.category)
            val tag = api.tags.getOrNull(from.tag)
            val orderBy = api.orderBys.getOrNull(from.orderBy)
            updateState {
                copy(
                    filterVisual = FilterQueryVisual(
                        category = mainCategory?.first,
                        tag = tag?.first,
                        orderBy = orderBy?.first
                    ),
                    filter = filter.copy(
                        loading = true,
                    )
                )
            }

            val nextPage = from.page + 1
            api.loadMainPageResult(
                page = nextPage,
                mainCategory = mainCategory?.second,
                tag = tag?.second,
                orderBy = orderBy?.second
            ).onFailure { error ->
                if (error is CancellationException) {
                    return@withContext
                }
                updateState {
                    copy(filter = filter.copy(loading = false, error = error))
                }
                postEffect {
                    MainPageEffect.ErrorLoading(error)
                }
            }.onSuccess { response ->
                val newList = fromList.addingAll(response.list)
                updateState {
                    // Outdated query, drop it
                    if (this.filter.query != from) {
                        return@updateState copy(filter = filter.copy(loading = false))
                    }
                    copy(
                        filter = filter.copy(
                            loading = false,
                            error = null,
                            items = newList,
                            query = from.copy(page = nextPage),
                            url = response.url
                        )
                    )
                }
            }
        } finally {
            updateState {
                copy(filter = filter.copy(loading = false))
            }
            expandMutex.unlock()
        }
    }

    suspend fun search(queryText: String) = withContext(Dispatchers.IO) {
        updateState {
            copy(openQuery = true, query = query.copy(loading = true, items = persistentListOf()))
        }
        api.searchResult(queryText).onFailure { error ->
            if (error is CancellationException) {
                return@withContext
            }
            updateState {
                copy(query = query.copy(loading = false, error = error))
            }
            postEffect {
                MainPageEffect.ErrorLoading(error)
            }
        }.onSuccess { response ->
            updateState {
                copy(
                    query = query.copy(
                        items = response.toPersistentList(),
                        loading = false,
                        error = null,
                        query = queryText,
                    )
                )
            }
        }
    }

    var searchQueryJob: Job? = null
    override fun onAction(action: MainPageAction) {
        when (action) {
            is MainPageAction.ResultAction -> {
                resultAction(action.data, action.operation)
            }

            is MainPageAction.Search -> {
                searchQueryJob?.cancel()
                searchQueryJob = viewModelScope.launch {
                    search(action.data)
                }
            }

            is MainPageAction.Expand -> {
                if (state.value.filter.loading) return
                viewModelScope.launch {
                    expand()
                }
            }

            is MainPageAction.OpenInBrowser -> {
                //
            }

            is MainPageAction.Back -> {
                updateState { copy(openQuery = false) }
            }

            is MainPageAction.OpenDialog -> {
                val names = when (action.type) {
                    DialogType.Category -> api.mainCategories
                    DialogType.Tags -> api.tags
                    DialogType.OrderBy -> api.orderBys
                }.map { it.first }
                updateState {
                    copy(
                        dialog = MainPageDialog(
                            selected = when (action.type) {
                                DialogType.Category -> filter.query.orderBy
                                DialogType.Tags -> filter.query.tag
                                DialogType.OrderBy -> filter.query.orderBy
                            },
                            options = names.toPersistentList(),
                            type = action.type
                        )
                    )
                }
            }

            is MainPageAction.SelectDialog -> {
                updateState {
                    copy(
                        filter = filter.copy(
                            query = filter.query.copy(
                                tag = if (action.type == DialogType.Tags) action.selected else filter.query.tag,
                                category = if (action.type == DialogType.Category) action.selected else filter.query.category,
                                orderBy = if (action.type == DialogType.OrderBy) action.selected else filter.query.orderBy,
                                page = 0,
                            ), items = persistentListOf(), error = null, url = ""
                        ),
                        dialog = null
                    )
                }
                viewModelScope.launch {
                    expand()
                }
            }

            MainPageAction.Dismiss -> {
                updateState {
                    copy(dialog = null)
                }
            }
        }
    }

    private fun resultAction(data: SearchResponse, operation: SearchOperation) {
        when (operation) {
            SearchOperation.Open -> loadResult(data.url, data.apiName)
            SearchOperation.Metadata -> {
                MainActivity.loadPreviewPage(data)
            }
        }
    }
}