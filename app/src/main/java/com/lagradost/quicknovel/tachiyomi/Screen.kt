package com.lagradost.quicknovel.tachiyomi

import androidx.compose.runtime.Composable

// https://github.com/adrielcafe/voyager
interface Screen {
    @Composable
    fun Content()
}