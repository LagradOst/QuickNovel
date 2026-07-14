package com.lagradost.quicknovel.compose

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.collections.immutable.PersistentList

fun <T> PersistentList<T>.removingBy(filter : (T) -> Boolean) : PersistentList<T> {
    val index = this.indexOfFirst(filter)
    return if(index == -1) {
        this
    } else {
        this.removingAt(index)
    }
}

val isLandscape : Boolean @Composable @ReadOnlyComposable get() = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
