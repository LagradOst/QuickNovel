package com.lagradost.quicknovel.tachiyomi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PreferenceData<T> {

    fun key(): String

    fun get(): T

    fun set(value: T)

    fun isSet(): Boolean

    fun delete()

    fun defaultValue(): T

    fun changes(): Flow<T>

    fun stateIn(scope: CoroutineScope): StateFlow<T>

    companion object {
        /**
         * A preference that should not be exposed in places like backups without user consent.
         */
        fun isPrivate(key: String): Boolean {
            return key.startsWith(PRIVATE_PREFIX)
        }
        fun privateKey(key: String): String {
            return "${PRIVATE_PREFIX}$key"
        }

        /**
         * A preference used for internal app state that isn't really a user preference
         * and therefore should not be in places like backups.
         */
        fun isAppState(key: String): Boolean {
            return key.startsWith(APP_STATE_PREFIX)
        }
        fun appStateKey(key: String): String {
            return "${APP_STATE_PREFIX}$key"
        }

        private const val APP_STATE_PREFIX = "__APP_STATE_"
        private const val PRIVATE_PREFIX = "__PRIVATE_"
    }
}

inline fun <reified T, R : T> PreferenceData<T>.getAndSet(crossinline block: (T) -> R) = set(
    block(get()),
)

operator fun <T> PreferenceData<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> PreferenceData<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun PreferenceData<Boolean>.toggle(): Boolean {
    set(!get())
    return get()
}

@Composable
fun <T> PreferenceData<T>.collectAsState(): androidx.compose.runtime.State<T> {
    val flow : Flow<T> = remember(this) { changes() }
    return flow.collectAsState(initial = this.get())
}