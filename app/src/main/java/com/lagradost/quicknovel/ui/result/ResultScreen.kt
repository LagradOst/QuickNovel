package com.lagradost.quicknovel.ui.result

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.ui.common.HorizontalTab
import com.lagradost.quicknovel.ui.common.LoadingButton
import com.lagradost.quicknovel.ui.common.LoadingLine
import com.lagradost.quicknovel.ui.common.LoadingPoster
import com.lagradost.quicknovel.ui.common.LoadingWeight
import com.lagradost.quicknovel.ui.common.LoadingWidth
import com.lagradost.quicknovel.ui.common.loading
import com.lagradost.quicknovel.ui.common.loadingLineMargin
import com.lagradost.quicknovel.util.AppUtils.openInBrowser
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.UIHelper.humanReadableByteCountSI
import kotlinx.collections.immutable.persistentListOf


@Composable
fun ResultScreen(state: ResultState, action: (ResultPageAction) -> Unit) {
    Scaffold { innerPadding ->
        if (state.loading) {
            LoadingScreen(Modifier.padding(innerPadding))
        } else {
            ResultScreenImpl(Modifier.padding(innerPadding), state, action)
        }
    }
}

@Composable
fun ResultScreenImpl(
    modifier: Modifier,
    state: ResultState,
    action: (ResultPageAction) -> Unit
) {
    val response = state.response ?: return
    val scrollState = rememberScrollState()


    val tabNames = persistentListOf(
        R.string.novel,
        R.string.reviews,
        R.string.related,
        R.string.chapters
    )
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { tabNames.size }
    )

    Box() {
        Box(modifier = modifier.height(height = 190.dp)) {
            AsyncImage(
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                model = response.blurImageRequest(),
                contentDescription = stringResource(R.string.poster_descript),
                alpha = 0.3f
            )
            Row(
                modifier = Modifier
                    .padding(10.dp)
            ) {
                AsyncImage(
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 100.dp, height = 150.dp)
                        .rounded(),
                    model = response.imageRequest(),
                    contentDescription = stringResource(R.string.poster_descript)
                )
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        response.name,
                        fontSize = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (response.author != null) {
                        Text(response.author, color = colors.primary, fontSize = 14.sp)
                    }
                    response.loadData?.status?.let { status ->
                        Text(
                            stringResource(status.resource),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    response.loadData?.chapters?.lastOrNull()?.let { chapter ->
                        Text(stringResource(R.string.latest_format, chapter.name), fontSize = 14.sp)
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .offset(y = 170.dp)
                .verticalScroll(scrollState)
                .fillMaxHeight()
                .clip(RoundedCornerShape(15.dp, 15.dp))
                .background(color = colors.background)
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(5.dp)
                    .fillMaxWidth()
                    .height(75.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.surface)
            ) {
                TextIcon(
                    R.string.bookmark,
                    R.drawable.ic_baseline_add_24,
                ) {

                }
                TextIcon(
                    R.string.open_in_browser,
                    R.drawable.ic_baseline_public_24,
                ) {
                    safe {
                        openInBrowser(response.url)
                    }
                }
                TextIcon(
                    R.string.result_share,
                    R.drawable.ic_outline_share_24,
                ) {
                    safe {
                        val i = Intent(Intent.ACTION_SEND)
                        i.type = "text/plain"
                        i.putExtra(Intent.EXTRA_SUBJECT, response.name)
                        i.putExtra(Intent.EXTRA_TEXT, response.url)
                        activity?.startActivity(Intent.createChooser(i, response.name))
                    }
                }
            }

            HorizontalTab(
                pagerState = pagerState,
                names = tabNames,
                containerColor = Color.Transparent
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> {
                        NovelPage(state, action)
                    }

                    else -> {

                    }
                }
            }
        }

    }
}

@Composable
fun NovelPage(
    state: ResultState,
    action: (ResultPageAction) -> Unit
) {
    val expanded = rememberSaveable { mutableStateOf(false) }
    val textInteractionSource = remember { MutableInteractionSource() }
    val response = state.response ?: return

    Column {
        Row(
            modifier = Modifier
                .padding(5.dp)
                .fillMaxWidth()
                .height(60.dp)
        ) {
            TextInfo(
                response.loadData?.views?.let(::humanReadableByteCountSI)
                    ?: stringResource(R.string.no_data),
                stringResource(R.string.views)
            )
            TextInfo(
                response.rating?.let {
                    LocalContext.current.getRating(it)
                } ?: stringResource(R.string.no_data),
                response.loadData?.peopleVoted?.let { stringResource(R.string.votes_format, it) }
                    ?: stringResource(R.string.no_data)
            )
            TextInfo(
                response.loadData?.chapters?.size?.toString() ?: stringResource(R.string.no_data),
                stringResource(R.string.chapters)
            )
        }

        Box(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(color = colors.onBackground.copy(alpha = 0.5f))
                .padding(horizontal = 15.dp)
        )

        if (!response.synopsis.isNullOrBlank()) {
            Text(
                modifier = Modifier
                    .padding(5.dp)
                    .clickable(
                        interactionSource = textInteractionSource,
                        indication = null,
                        onClick = {
                            expanded.value = !expanded.value
                        })
                    .rounded()
                    .ripple(textInteractionSource)
                    .padding(5.dp),
                text = response.synopsis,
                style = BaseStyles.textStyle,
                maxLines = if (expanded.value) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis
            )
        }

        val downloadState = response.downloadState ?: return

        LinearProgressIndicator(
            progress = { downloadState.downloadPercentage },
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
        )
    }
}


@Composable
fun RowScope.TextInfo(
    text: String,
    subText: String
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1.0f)
            .fillMaxHeight()
    ) {
        Text(text, fontSize = 17.sp, lineHeight = 16.sp)
        Text(
            subText,
            fontSize = 12.sp, lineHeight = 11.sp,
            modifier = Modifier.padding(2.dp),
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
fun RowScope.TextIcon(text: Int, icon: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1.0f)
            .fillMaxHeight()
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .circle()
    ) {
        Text(stringResource(text), fontSize = 16.sp, modifier = Modifier.padding(2.dp))
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(text)
        )
    }
}

@Composable
fun LoadingScreen(modifier: Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingPoster()
            Column {
                LoadingLine()
                LoadingLine(0.7f)
            }
        }
        LoadingLine()
        Row(horizontalArrangement = Arrangement.SpaceEvenly) {
            Spacer(Modifier.weight(0.3f))
            LoadingWeight()
            Spacer(Modifier.weight(0.3f))
            LoadingWeight()
            Spacer(Modifier.weight(0.3f))
        }
        Spacer(Modifier.height(20.dp))
        Row {
            LoadingWidth(100.dp)
            LoadingWidth(100.dp)
        }
        Spacer(Modifier.height(20.dp))
        LoadingLine()
        LoadingLine()
        LoadingLine(0.4f)
        Spacer(Modifier.height(20.dp))
        LoadingLine(0.3f)
        LoadingLine()
        LoadingLine()
        LoadingLine(0.4f)
        Spacer(Modifier.height(20.dp))
        LoadingLine()
        Row {
            LoadingButton()
            LoadingButton()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(loadingLineMargin.dp)
                .height(35.dp)
                .loading()
        )
    }
}


@PreviewLightDark
@Composable
fun LoadingPreview() {
    CloudStreamTheme {
        Surface {
            LoadingScreen(Modifier)
        }
    }
}