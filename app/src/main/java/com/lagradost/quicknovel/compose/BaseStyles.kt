package com.lagradost.quicknovel.compose

import androidx.compose.material3.ButtonColors
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

    val blackButtonColors  @Composable @ReadOnlyComposable get() = ButtonColors(
        containerColor = colors.surface,
        contentColor = colors.onBackground,
        disabledContainerColor = colors.surface.copy(alpha = 0.9f),
        disabledContentColor = colors.onBackground.copy(alpha = 0.9f)
    )

    val whiteButtonColors @Composable @ReadOnlyComposable get() =  ButtonColors(
        containerColor = colors.onBackground,
        contentColor = colors.surface,
        disabledContainerColor = colors.onBackground.copy(alpha = 0.9f),
        disabledContentColor = colors.surface.copy(alpha = 0.9f)
    )
}
