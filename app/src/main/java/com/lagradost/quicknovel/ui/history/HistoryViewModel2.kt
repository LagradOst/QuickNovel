package com.lagradost.quicknovel.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.lagradost.quicknovel.BaseApplication.Companion.getKey
import com.lagradost.quicknovel.BaseApplication.Companion.getKeys
import com.lagradost.quicknovel.BaseApplication.Companion.removeKey
import com.lagradost.quicknovel.BaseApplication.Companion.removeKeys
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.HISTORY_FOLDER
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.compose.BaseViewModel
import com.lagradost.quicknovel.util.ResultCached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class HistoryState(
    val loading: Boolean = true,
    val history: List<ResultCached> = listOf(),
    val dialog: HistoryDialog? = null,
)

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
}

enum class ResultOperation {
    Open,
    Stream,
    AskDelete,
    Delete,
    Metadata,
}


sealed class HistoryEffect {
}

class HistoryViewModel2 : BaseViewModel<HistoryAction, HistoryState, HistoryEffect>() {
    override fun initialState() = HistoryState()

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
                updateState {
                    copy(dialog = null)
                }
                removeKey(HISTORY_FOLDER, data.id.toString())
                updateHistory()
            }

            ResultOperation.Metadata -> {
                MainActivity.loadPreviewPage(data)
            }
        }
    }


    init {
        updateHistory()
    }

    private fun updateHistory() = viewModelScope.launch {
        withContext(Dispatchers.Default) {
            updateState {
                copy(loading = true)
            }
            val keys = getKeys(HISTORY_FOLDER) ?: return@withContext
            val data = keys.mapNotNull { getKey<ResultCached>(it) }.sortedBy { -it.cachedTime }

            updateState {
                copy(loading = false, history = data)
            }
        }
    }
}