package com.lagradost.quicknovel.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.ui.download.DownloadRow
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun HorizontalTab(
    pagerState: PagerState,
    names: PersistentList<Int>,
    containerColor: Color,
    edgePadding: Dp = 0.dp,
) {
    val currentPage = pagerState.currentPage

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tabWidth =
            if (names.isNotEmpty()) (maxWidth - edgePadding * 2.0f) / names.size else Dp.Unspecified

        SecondaryScrollableTabRow(
            currentPage,
            edgePadding = edgePadding,
            containerColor = containerColor,
            indicator = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .zIndex(-1.0f)
                        .tabIndicatorOffset(currentPage, matchContentSize = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(5.dp)
                            .circle()
                            .background(colors.onBackground)
                    )
                }
            }, divider = {}
        ) {
            names.forEachIndexed { index, row ->
                val selected = index == currentPage
                Tab(
                    modifier = Modifier
                        .height(40.dp)
                        .widthIn(min = tabWidth)
                        .circle(),
                    selected = selected, onClick = {
                        pagerState.requestScrollToPage(index)
                    }, text = {
                        Text(
                            stringResource(row), color = if (selected) {
                                colors.background
                            } else {
                                colors.onBackground
                            }
                        )
                    })
            }
        }
    }
}

@PreviewLightDark
@Composable
fun Preview() {
    CloudStreamTheme {
        val list = persistentListOf(
            R.string.downloaded,
            R.string.type_reading,
            R.string.type_dropped,
            R.string.type_completed,
            R.string.type_completed,
            R.string.type_completed,
            R.string.type_completed,
            R.string.type_completed,
            R.string.type_completed,
            R.string.type_completed,
            R.string.type_completed,
            R.string.type_completed
        )
        val pager = rememberPagerState() { list.size }
        HorizontalTab(pager, list, Color.Transparent)
    }
}