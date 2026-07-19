package com.lagradost.quicknovel.compose

import android.content.res.Configuration
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable

fun <T> PersistentList<T>.removingBy(filter : (T) -> Boolean) : PersistentList<T> {
    val index = this.indexOfFirst(filter)
    return if(index == -1) {
        this
    } else {
        this.removingAt(index)
    }
}

val isLandscape : Boolean @Composable @ReadOnlyComposable get() = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

fun LazyListState.isFirstItemVisible() = firstVisibleItemIndex == 0

@Composable
fun LazyGridState.IsScrolling(up : Runnable, down : Runnable) {
    LaunchedEffect(lastScrolledBackward) {
        if(lastScrolledBackward) up.run()
    }
    LaunchedEffect(lastScrolledForward) {
        if(lastScrolledForward) down.run()
    }
}
@Composable
fun LazyListState.IsScrolling(up : Runnable, down : Runnable) {
    LaunchedEffect(lastScrolledBackward) {
        if(lastScrolledBackward) up.run()
    }
    LaunchedEffect(lastScrolledForward) {
        if(lastScrolledForward) down.run()
    }
}
@Composable
fun LaunchedEffectSkipFirst(
    key: Any?,
    block: suspend CoroutineScope.() -> Unit
) {
    var isFirstChange by remember { mutableStateOf(true) }

    LaunchedEffect(key) {
        if (isFirstChange) {
            isFirstChange = false
        } else {
            block()
        }
    }
}
