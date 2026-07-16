package com.lagradost.quicknovel.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.ImmutableSearchResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.compose.ActionHandler
import com.lagradost.quicknovel.compose.DefaultEffectContainer
import com.lagradost.quicknovel.compose.DefaultStateContainer
import com.lagradost.quicknovel.compose.EffectContainer
import com.lagradost.quicknovel.compose.SingleActiveQuery
import com.lagradost.quicknovel.compose.StateContainer
import com.lagradost.quicknovel.ui.mainpage.FilterQuery
import com.lagradost.quicknovel.ui.mainpage.SearchOperation
import com.lagradost.quicknovel.ui.search.HomeEffect.NavigateToMainPage
import com.lagradost.quicknovel.util.Apis
import com.lagradost.quicknovel.util.cmap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Immutable
data class HomeViewModelState(
    val allApis: ImmutableList<MainAPI> = Apis.apis.toImmutableList(),
    val mainPageApis: ImmutableList<MainAPI> = allApis.filter { api -> api.hasMainPage }
        .toImmutableList(),
    val shownMainPageApis: ImmutableList<MainAPI> = mainPageApis,
    val filterNames: ImmutableSet<String> = persistentSetOf(),
    val filterLanguages: ImmutableSet<String> = persistentSetOf(),
    val isConfigureShow: Boolean = false,
    val searchRows: PersistentList<SearchRow> = persistentListOf(),
    val isLoading: Boolean = false,
    val isQueryOpen: Boolean = false,
    val openRow: SearchRow? = null,
)

@Immutable
data class SearchRow(
    val name: String,
    val items: ImmutableList<ImmutableSearchResponse> = persistentListOf(),
    val error: Throwable? = null,
)

sealed class HomeAction {
    data class Search(val query: String) : HomeAction()
    object ConfigureApis : HomeAction()
    object DismissConfigureApis : HomeAction()
    data class Open(val item: MainAPI) : HomeAction()
    data class ConfigureApisNames(val names: ImmutableSet<String>) : HomeAction()
    data class ConfigureApisLanguages(val languages: ImmutableSet<String>) : HomeAction()
    object CloseQuery : HomeAction()
    data class ResultAction(val data: ImmutableSearchResponse, val operation: SearchOperation) :
        HomeAction()

    data class OpenRow(val row: SearchRow) : HomeAction()
    object CloseRow : HomeAction()
}

sealed class HomeEffect {
    data class NavigateToMainPage(val api: String, val filter: FilterQuery) : HomeEffect()
}

class HomeViewModel2 : ViewModel(),
    StateContainer<HomeViewModelState> by DefaultStateContainer(HomeViewModelState()),
    ActionHandler<HomeAction>,
    EffectContainer<HomeEffect> by DefaultEffectContainer() {

    /*companion object {
        // Todo do this with injection of perf instead?
        fun provideFactory(selection: PreferenceData<Set<String>>) = viewModelFactory {
            initializer {
                val selection = selection.get()
                val state = HomeViewModelState()
                HomeViewModel2(
                    state.copy(shownMainPageApis = state.shownMainPageApis.filter { api ->
                        selection.contains(
                            api.name
                        )
                    }.toPersistentList())
                )
            }
        }
    }*/

    fun filterApis(
        apis: ImmutableList<MainAPI>,
        names: ImmutableSet<String>,
        languages: ImmutableSet<String>
    ): ImmutableList<MainAPI> {
        if (names.isEmpty() || languages.isEmpty()) return persistentListOf()

        return apis.filter { api ->
            languages.contains(api.lang) && names.contains(
                api.name
            )
        }.toPersistentList()
    }

    override fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.ConfigureApis -> {
                updateState {
                    copy(isConfigureShow = true)
                }
            }

            is HomeAction.Open -> {
                viewModelScope.launch {
                    postEffect {
                        NavigateToMainPage(action.item.name, FilterQuery())
                    }
                }
            }

            is HomeAction.Search -> {
                search(action.query)
            }

            HomeAction.DismissConfigureApis -> {
                updateState {
                    copy(isConfigureShow = false)
                }
            }

            is HomeAction.ConfigureApisNames -> {
                updateState {
                    copy(
                        filterNames = action.names,
                        shownMainPageApis = filterApis(
                            mainPageApis,
                            action.names,
                            filterLanguages
                        )
                    )
                }
            }

            is HomeAction.ConfigureApisLanguages -> {
                updateState {
                    copy(
                        filterLanguages = action.languages,
                        shownMainPageApis = filterApis(
                            mainPageApis,
                            filterNames,
                            action.languages
                        )
                    )
                }
            }

            HomeAction.CloseQuery -> updateState {
                copy(isQueryOpen = false)
            }

            is HomeAction.ResultAction -> {
                resultAction(action.data, action.operation)
            }

            HomeAction.CloseRow -> {
                updateState {
                    copy(openRow = null)
                }
            }

            is HomeAction.OpenRow -> {
                updateState {
                    copy(openRow = action.row)
                }
            }
        }
    }

    private fun resultAction(data: ImmutableSearchResponse, operation: SearchOperation) {
        when (operation) {
            SearchOperation.Open -> loadResult(data.url, data.apiName)
            SearchOperation.Metadata -> {
                MainActivity.loadPreviewPage(data)
            }
        }
    }

    val searchQuery = SingleActiveQuery(Dispatchers.IO)

    private fun search(query: String) = viewModelScope.launch {
        searchQuery.launch {
            val state = state.value
            val searchApis = filterApis(state.allApis, state.filterNames, state.filterLanguages)
            searchQuery(query, searchApis)
        }
    }

    private suspend fun searchQuery(query: String, apis: ImmutableList<MainAPI>) {
        updateState {
            copy(
                isQueryOpen = true,
                isLoading = true,
                searchRows = persistentListOf()
            )
        }
        apis.cmap { api ->
            APIRepository(api).searchResult(query).onSuccess { value ->
                val row = SearchRow(name = api.name, items = value.toImmutableList())
                updateState {
                    if (value.isEmpty()) {
                        copy(searchRows = searchRows.adding(row))
                    } else {
                        val hasResults = searchRows.count { it.items.isNotEmpty() }
                        copy(searchRows = searchRows.addingAt(hasResults, row))
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                val row = SearchRow(name = api.name, error = error)
                updateState {
                    copy(searchRows = searchRows.adding(row))
                }
            }
        }
        updateState {
            copy(isLoading = false)
        }
    }
}