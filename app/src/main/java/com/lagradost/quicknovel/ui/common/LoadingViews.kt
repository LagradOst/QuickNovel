package com.lagradost.quicknovel.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.quicknovel.compose.gradientEffect

val grayColor = Color(0x24696969)
val shimmerColor = Color.White.copy(alpha = 0.05f)
const val loadingLineHeight = 15
const val loadingLineMargin = 7.5

@Composable
fun Modifier.loading(): Modifier {
    return this
        .clip(RoundedCornerShape(5.dp))
        .background(grayColor)
        .gradientEffect(shimmerColor)
}

@Composable
fun LoadingLine(fraction: Float = 1.0f) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .padding(loadingLineMargin.dp)
            .height(loadingLineHeight.dp)
            .loading()
    )
}

@Composable
fun RowScope.LoadingButton() {
    Box(
        modifier = Modifier
            .weight(1.0f)
            .padding(loadingLineMargin.dp)
            .height(35.dp)
            .loading()
    )
}

@Composable
fun LoadingWidth(width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(loadingLineMargin.dp)
            .height(loadingLineHeight.dp)
            .loading()
    )
}

@Composable
fun RowScope.LoadingWeight() {
    Box(
        modifier = Modifier
            .weight(1.0f)
            .padding(loadingLineMargin.dp)
            .height(loadingLineHeight.dp)
            .loading()
    )
}

@Composable
fun LoadingPoster() {
    Box(
        modifier = Modifier
            .size(width = 100.dp, height = 140.dp)
            .padding(loadingLineMargin.dp)
            .loading()
        //.gradientEffect(listOf(Color.White, Color.Transparent))
    )
}

//this si for related novels loading
@Composable
fun LoadingGridPoster() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .padding(loadingLineMargin.dp)
                .loading()
        )
        LoadingLine()
    }
}

//this is for author, status and last chapter from ResultScreen
@Composable
fun <T> LoadingPlaceholder(
    data: T?,
    isLoading: Boolean,
    placeholder: @Composable () -> Unit = { LoadingWidth(80.dp) },
    content: @Composable (T) -> Unit
) {
    if (data == null && isLoading) {
        placeholder()
    } else if (data != null) {
        content(data)
    }
}
