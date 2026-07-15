package com.lagradost.quicknovel.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.removeKeys
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.compose.ActionHandler
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class HistoryState(
    val loading: Boolean = true,
    val allHistory: PersistentList<ResultCached> = persistentListOf(),
    val filteredHistory: PersistentList<ResultCached> = persistentListOf(),
    val dialog: HistoryDialog? = null,
    val query: String = "",
) {
    fun filter(item: ResultCached) =
        FuzzySearch.partialRatio(item.name.lowercase(), query) > 50
}


@Immutable
data class HistoryDialog(
    val about: ResultCached?,
)

sealed class HistoryAction {
    data class ResultAction(val data: ResultCached, val operation: ResultOperation) :
        HistoryAction()

    object AskDeleteAll : HistoryAction()
    object DeleteAll : HistoryAction()
    object DismissDialog : HistoryAction()
    data class Search(val data: String) : HistoryAction()
    object Refresh : HistoryAction()
}

enum class ResultOperation {
    Open,
    Stream,
    AskDelete,
    Delete,
    Metadata,
}

// HistoryAction, HistoryState, HistoryEffect
@OptIn(FlowPreview::class)
class HistoryViewModel2 : ViewModel(),
    StateContainer<HistoryState> by DefaultStateContainer(HistoryState()),
    //EffectContainer<HistoryEffect> by effectContainer,
    ActionHandler<HistoryAction>
{
    private val searchPipe = MutableSharedFlow<String>(extraBufferCapacity = 64)

    override fun onAction(action: HistoryAction) {
        when (action) {
            is HistoryAction.ResultAction -> {
                resultAction(action.data, action.operation)
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

    private fun resultAction(data: ResultCached, operation: ResultOperation) {
        when (operation) {
            ResultOperation.Open -> loadResult(data.source, data.apiName)
            ResultOperation.Stream -> BookDownloader2.stream(data)
            ResultOperation.AskDelete -> {
                updateState {
                    copy(dialog = HistoryDialog(data))
                }
            }

            ResultOperation.Delete -> {
                removeKey(HISTORY_FOLDER, data.id.toString())
                updateState {
                    copy(
                        dialog = null,
                        allHistory = allHistory.removingBy { it.id == data.id },
                        filteredHistory = filteredHistory.removingBy { it.id == data.id }
                    )
                }
            }

            ResultOperation.Metadata -> {
                MainActivity.loadPreviewPage(data)
            }
        }
    }


    init {
        //  updateHistory()

        viewModelScope.launch {
            searchPipe.debounce(200L.milliseconds).distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .collect { query ->
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
        data: PersistentList<ResultCached>
    ): PersistentList<ResultCached> {
        val results = if (query.isBlank() || query.length < 2) {
            data
        } else {
            data.filter { item ->
                FuzzySearch.partialRatio(query, item.name) > 50
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
            val data = keys.mapNotNull { getKey<ResultCached>(it) }.sortedBy { -it.cachedTime }
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