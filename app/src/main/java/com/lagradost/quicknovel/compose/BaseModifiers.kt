package com.lagradost.quicknovel.compose

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors


@Composable
fun Modifier.ripple(
    interactionSource: MutableInteractionSource,
    bounded: Boolean = true,
): Modifier = indication(
    interactionSource = interactionSource,
    indication = ripple(bounded = bounded, color = colors.onBackground),
)

@Composable
fun RoundedImageShape() = RoundedCornerShape(dimensionResource(R.dimen.roundedImageRadius))

@Composable
fun Modifier.rounded(): Modifier =
    clip(RoundedImageShape())


@Composable
fun Modifier.circle(): Modifier =
    clip(CircleShape)