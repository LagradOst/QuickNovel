package com.lagradost.quicknovel.ui.result.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.DefaultBookmark
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.SimpleTextDialog
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseOperation
import com.lagradost.quicknovel.ui.common.LoadingButton
import com.lagradost.quicknovel.ui.common.LoadingLine
import com.lagradost.quicknovel.ui.common.LoadingPoster
import com.lagradost.quicknovel.ui.common.html
import com.lagradost.quicknovel.ui.result.ResultPageAction
import com.lagradost.quicknovel.util.SettingsHelper.getRating

@Composable
fun BottomPreviewLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            LoadingPoster()

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .height(138.dp)
                    .weight(1f)
            ) {
                LoadingLine(fraction = 0.9f)
                LoadingLine(fraction = 0.6f)
                LoadingLine(fraction = 0.8f)
                LoadingLine(fraction = 1.0f)
                LoadingLine(fraction = 0.7f)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 4.dp)
        ) {
            LoadingButton()
            LoadingButton()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BottomPreviewContent(
    response: ImmutableSearchResponse,
    bookmarks: List<DefaultBookmark>,
    currentBookmarkId: Int,
    showMoreInfo: Boolean,
    onAction: (ResultPageAction) -> Unit
) {
    val context = LocalContext.current
    var showFullDescription by remember { mutableStateOf(false) }
    val description =
        if (response.synopsis.isNullOrBlank()) stringResource(R.string.no_data) else response.synopsis
    if (showFullDescription) {
        SimpleTextDialog(
            title = response.name,
            text = description,
            onDismiss = { showFullDescription = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Poster
            Surface(
                modifier = Modifier
                    .size(88.dp, 138.dp)
                    .clickable {
                        if (response.isImported) {
                            onAction(ResultPageAction.ResultAction(SearchResponseAction(response, SearchResponseOperation.Read)))
                        } else {
                            onAction(ResultPageAction.ResultAction(SearchResponseAction(response, SearchResponseOperation.Open)))
                        }
                    },
                shape = RoundedCornerShape(dimensionResource(R.dimen.roundedImageRadius)),
                color = colors.surfaceVariant
            ) {
                AsyncImage(
                    model = response.imageRequest(),
                    contentDescription = stringResource(R.string.poster_descript),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Info Column
            Column(
                modifier = Modifier
                    .height(138.dp)
                    .weight(1f)
            ) {
                // Title and Delete
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = response.name,
                        color = colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(end = 30.dp)
                            .align(Alignment.CenterStart)
                            .clickable {
                                onAction(ResultPageAction.ResultAction(SearchResponseAction(response, SearchResponseOperation.Open)))
                            },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = {
                            onAction(ResultPageAction.AskDeleteNovel(response))
                        },
                        modifier = Modifier
                            .size(25.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_delete_outline_24),
                            contentDescription = stringResource(R.string.delete),
                            tint = colors.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Meta Info (Rating, Status, Chapters)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    response.rating?.let { ResultInfoText(context.getRating(it)) }
                    response.statusRes?.let { ResultInfoText(stringResource(it), color = colors.primary) }
                    response.chapters?.let { chapters ->
                        val chaptersText = "$chapters " + stringResource(if (chapters == 1L) R.string.chapter else R.string.chapters)
                        ResultInfoText(chaptersText)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Description
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { showFullDescription = true }
                ) {
                    Text(
                        text = description.html(),
                        color = colors.onBackground,
                        fontSize = 13.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Bottom Shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, colors.background)
                                )
                            )
                    )
                }
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Button(
                onClick = { onAction(ResultPageAction.ShowBookmarkDialog(context)) },
                modifier = Modifier.weight(1f),
                colors = BaseStyles.blackButtonColors,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                val isBookmarked = currentBookmarkId != 0
                Icon(
                    painter = painterResource(if (isBookmarked) R.drawable.ic_baseline_bookmark_24 else R.drawable.ic_baseline_bookmark_border_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))

                val bookmarkText = when {
                    currentBookmarkId > 0 -> bookmarks.find { it.id == currentBookmarkId }?.title
                    currentBookmarkId != 0 -> stringResource(R.string.download)
                    else -> null
                } ?: stringResource(R.string.bookmark)

                Text(text = bookmarkText)
            }

            if (showMoreInfo) {
                Button(
                    onClick = {
                        onAction(ResultPageAction.DismissDialog)
                        onAction(ResultPageAction.ResultAction(SearchResponseAction(response, SearchResponseOperation.Open)))
                    },
                    modifier = Modifier.weight(1f),
                    colors = BaseStyles.whiteButtonColors,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_open_in_new_24),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.more_info))
                }
            }
        }
    }
}

@Composable
fun ResultInfoText(text: String, color: Color = colors.onBackground) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        modifier = Modifier.wrapContentSize(Alignment.Center)
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewBottomPreviewContent() {
    CloudStreamTheme {
        BottomPreviewContent(
            response = ImmutableSearchResponse(
                name = "Test Novel",
                url = "",
                apiName = "",
                timeOfCached = 0,
                chaptersRead = 0,
                synopsis = "This is a test synopsis for the preview content."
            ),
            bookmarks = emptyList(),
            currentBookmarkId = 0,
            showMoreInfo = true,
            onAction = {}
        )
    }
}
