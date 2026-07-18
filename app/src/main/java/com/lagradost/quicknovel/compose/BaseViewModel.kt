package com.lagradost.quicknovel.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lagradost.quicknovel.mvvm.logError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/*
Base viewmodel is not used to "Composition over inheritance"

Instead, use delegation like https://kotlinlang.org/docs/delegation.html

class XViewModel(
    stateContainer: StateContainer<TyState> = DefaultStateContainer(HistoryState()),
    effectContainer: EffectContainer<TyEffect> = DefaultEffectContainer(),
) : ViewModel(),
    StateContainer<TyState> by stateContainer,
    EffectContainer<TyEffect> by effectContainer,
    ActionHandler<TyAction>
{

}


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
}*/


interface StateContainer<State> {
    val state: StateFlow<State>
    fun updateState(reducer: State.() -> State)
}

interface EffectContainer<Effect> {
    val effect: Flow<Effect>
    suspend fun postEffect(builder: () -> Effect)
}

interface ActionHandler<Action> {
    fun onAction(action: Action)
}

class DefaultStateContainer<State>(initialState: State) : StateContainer<State> {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<State> = _state.asStateFlow()

    @Synchronized
    override fun updateState(reducer: State.() -> State) {
        _state.update(reducer)
    }
}

class DefaultEffectContainer<Effect> : EffectContainer<Effect> {
    private val _effect = Channel<Effect>() // To ensure events are sent
    override val effect: Flow<Effect> get() = _effect.receiveAsFlow()

    override suspend fun postEffect(builder: () -> Effect) {
        _effect.send(builder())
    }
}

@Composable
fun <T> ObserveEffect(flow: Flow<T>, onEvent: (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner.lifecycle) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.Main.immediate) { // To ensure events are sent
                flow.collect(onEvent)
            }
        }
    }
}

data class SingleActiveQuery(
    val dispatcher: CoroutineDispatcher,
    private var job: Job? = null,
    private val mutex: Mutex = Mutex(),
) {
    suspend fun launch(block: suspend CoroutineScope.() -> Unit) {
        val currentScope = CoroutineScope(currentCoroutineContext())
        val obj: suspend CoroutineScope.() -> Unit = {
            try {
                withContext(dispatcher) {
                    block()
                }
            } catch (t: Throwable) {
                logError(t)
            }
        }
        mutex.withLock {
            job?.cancel()
            job?.join()
            job = currentScope.launch(block = obj)
        }
    }
}

data class DebounceQuery(
    private val pipe: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 64, replay = 64)
) {
    @OptIn(FlowPreview::class)
    suspend fun launch(collector: FlowCollector<String>) {
        pipe.debounce(200L.milliseconds).distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .collect(collector)
    }
    suspend fun emit(query : String) {
        this.pipe.emit(query)
    }
}