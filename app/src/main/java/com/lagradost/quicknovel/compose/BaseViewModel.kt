package com.lagradost.quicknovel.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<Action, State, Effect> : ViewModel() {
    private val _state: MutableStateFlow<State> by lazy { MutableStateFlow(initialState()) }
    val state: StateFlow<State> get() = _state.asStateFlow()

    private val _effect = MutableSharedFlow<Effect>()
    val effect: SharedFlow<Effect> get() = _effect.asSharedFlow()

    protected abstract fun initialState(): State
    abstract fun onAction(action: Action)

    protected fun updateState(reducer: State.() -> State) {
        _state.update(reducer)
    }

    protected fun postEffect(builder: () -> Effect) = viewModelScope.launch {
        _effect.emit(builder())
    }
}