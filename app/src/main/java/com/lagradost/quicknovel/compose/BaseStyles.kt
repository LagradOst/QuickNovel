package com.lagradost.quicknovel.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors

object BaseStyles {
    val textStyle : TextStyle  @Composable @ReadOnlyComposable get() = TextStyle(
        color = colors.onBackground,
        fontSize = 14.sp,
        lineHeight = 15.sp,
    )

    val textAltStyle : TextStyle  @Composable @ReadOnlyComposable get() = TextStyle(
        color = colors.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 15.sp,
    )
}
