package com.lagradost.quicknovel.ui.result

import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.compose.ActionHandler
import com.lagradost.quicknovel.compose.DefaultEffectContainer
import com.lagradost.quicknovel.compose.DefaultStateContainer
import com.lagradost.quicknovel.compose.EffectContainer
import com.lagradost.quicknovel.compose.StateContainer
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.download.DownloadPageAction
import com.lagradost.quicknovel.ui.download.DownloadPageState
import com.lagradost.quicknovel.ui.mainpage.FilterQuery
import com.lagradost.quicknovel.ui.mainpage.MainPageViewModel2
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName
import kotlinx.coroutines.launch

@Immutable
data class ResultState(
    val response: ImmutableSearchResponse? = null,
    val error: Throwable? = null,
    val loading: Boolean = true,
)

@Immutable
sealed class ResultPageAction {
    data class ResultAction(val action: SearchResponseAction) : ResultPageAction()
}

@Immutable
sealed class ResultPageEffect {
}

class ResultViewModel2(
    val api: APIRepository,
    val url: String,
) : ViewModel(), ActionHandler<ResultPageAction>,
    StateContainer<ResultState> by DefaultStateContainer(ResultState()),
    EffectContainer<ResultPageEffect> by DefaultEffectContainer() {
    companion object {
        fun provideFactory(bundle: Bundle) = viewModelFactory {
            initializer {
                val url = bundle.getString("url")!!
                val apiName = bundle.getString("apiName")!!
                ResultViewModel2(api = getApiFromName(apiName), url = url)
            }
        }
    }

    init {
        viewModelScope.launch {
            api.loadResult(url).onFailure { error ->
                updateState { copy(error = error, loading = false) }
            }.onSuccess { value ->
                updateState { copy(response = value, loading = false, error = null) }
            }
        }
    }

    override fun onAction(action: ResultPageAction) {
        when (action) {
            is ResultPageAction.ResultAction -> {

            }
        }
    }
}