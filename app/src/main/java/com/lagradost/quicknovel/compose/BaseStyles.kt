package com.lagradost.quicknovel.compose

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

object Colors {
    /*val textStyle : TextStyle  @Composable @ReadOnlyComposable get() = TextStyle(
        color = colors.onBackground,
        fontSize = 14.sp,
        lineHeight = 15.sp,
        fontFamily = AppFont.googleSans,
    )

    val textAltStyle : TextStyle  @Composable @ReadOnlyComposable get() = TextStyle(
        color = colors.onSurfaceVariant,
        fontSize = 14.sp,
        lineHeight = 15.sp,
        fontFamily = AppFont.googleSans,
    )*/

    val blackButton  @Composable @ReadOnlyComposable get() = ButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onBackground,
        disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        disabledContentColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
    )

    val whiteButton @Composable @ReadOnlyComposable get() =  ButtonColors(
        containerColor = MaterialTheme.colorScheme.onBackground,
        contentColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
        disabledContentColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    )
}
