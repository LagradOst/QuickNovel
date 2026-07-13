package com.lagradost.quicknovel.compose

import kotlinx.collections.immutable.PersistentList

fun <T> PersistentList<T>.removingBy(filter : (T) -> Boolean) : PersistentList<T> {
    val index = this.indexOfFirst(filter)
    return if(index == -1) {
        this
    } else {
        this.removingAt(index)
    }
}