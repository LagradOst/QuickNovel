package com.lagradost.quicknovel.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.removeKeys
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.ImmutableSearchResponse
import com.lagradost.quicknovel.SearchResponseAction
import com.lagradost.quicknovel.SearchResponseOperation
import com.lagradost.quicknovel.compose.ActionHandler
import com.lagradost.quicknovel.compose.DebounceQuery
import com.lagradost.quicknovel.compose.DefaultStateContainer
import com.lagradost.quicknovel.compose.StateContainer
import com.lagradost.quicknovel.compose.removingBy
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class HistoryState(
    val loading: Boolean = true,
    val allHistory: PersistentList<ImmutableSearchResponse> = persistentListOf(),
    val filteredHistory: PersistentList<ImmutableSearchResponse> = persistentListOf(),
    val dialog: HistoryDialog? = null,
    val query: String = "",
)


@Immutable
data class HistoryDialog(
    val about: ImmutableSearchResponse?,
)

sealed class HistoryAction {
    data class ResultAction(val action: SearchResponseAction) : HistoryAction()
    object AskDeleteAll : HistoryAction()
    object DeleteAll : HistoryAction()
    object DismissDialog : HistoryAction()
    data class Search(val data: String) : HistoryAction()
    object Refresh : HistoryAction()
}


// HistoryAction, HistoryState, HistoryEffect
@OptIn(FlowPreview::class)
class HistoryViewModel2 : ViewModel(),
    StateContainer<HistoryState> by DefaultStateContainer(HistoryState()),
    //EffectContainer<HistoryEffect> by effectContainer,
    ActionHandler<HistoryAction> {
    private val searchPipe = DebounceQuery()

    override fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.ResultAction -> {
                resultAction(action.action)
            }

            is HistoryAction.AskDeleteAll -> {
                updateState {
                    copy(dialog = HistoryDialog(about = null))
                }
            }

            is HistoryAction.DeleteAll -> {
                updateState {
                    copy(dialog = null)
                }
                CoroutineScope(Dispatchers.Default).launch {
                    removeKeys(HISTORY_FOLDER)
                    updateHistory()
                }
            }

            is HistoryAction.DismissDialog -> {
                updateState {
                    copy(dialog = null)
                }
            }

            is HistoryAction.Search -> {
                viewModelScope.launch {
                    searchPipe.emit(action.data)
                }
            }

            is HistoryAction.Refresh -> {
                updateHistory()
            }
        }
    }

    private fun resultAction(action: SearchResponseAction) {
        when (action.operation) {
            SearchResponseOperation.AskDelete -> {
                updateState {
                    copy(dialog = HistoryDialog(action.response))
                }
            }

            SearchResponseOperation.Delete -> {
                removeKey(HISTORY_FOLDER, action.response.id.toString())
                val id = action.response.id
                updateState {
                    copy(
                        dialog = null,
                        allHistory = allHistory.removingBy { it.id == id },
                        filteredHistory = filteredHistory.removingBy { it.id == id }
                    )
                }
            }

            else -> action.doAction()
        }
    }


    init {
        //  updateHistory()
        viewModelScope.launch {
            searchPipe.launch { query ->
                updateState {
                    if (query == this.query) {
                        this
                    } else {
                        copy(filteredHistory = filterHistory(query, allHistory), query = query)
                    }
                }
            }
        }
    }

    fun filterHistory(
        query: String,
        data: PersistentList<ImmutableSearchResponse>
    ): PersistentList<ImmutableSearchResponse> {
        val results = if (query.isBlank() || query.length < 2) {
            data
        } else {
            data.filter { item ->
                item.filter(query)
            }.toPersistentList()
        }
        return results
    }

    private fun updateHistory() = viewModelScope.launch {
        withContext(Dispatchers.Default) {
            updateState {
                copy(loading = true)
            }
            val keys = getKeys(HISTORY_FOLDER) ?: return@withContext
            val data =
                keys.mapNotNull { getKey<ResultCached>(it)?.let(ImmutableSearchResponse::from) }
                    .sortedBy { -it.updateTime }
                    .toPersistentList()

            updateState {
                copy(
                    loading = false,
                    allHistory = data,
                    filteredHistory = filterHistory(query, data)
                )
            }
        }
    }
}