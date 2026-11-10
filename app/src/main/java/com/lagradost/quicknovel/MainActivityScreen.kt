package com.lagradost.quicknovel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import coil3.compose.AsyncImage
import com.lagradost.cloudstream4.compose.ActionDialog
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.compose.circle
import com.lagradost.cloudstream4.compose.rounded
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.lagradost.quicknovel.MainActivity.Companion.loadResult
import com.lagradost.quicknovel.MainActivityScreen.InnerContent
import com.lagradost.quicknovel.downloader.BookDownloadStatus
import com.lagradost.quicknovel.providers.RoyalRoadProvider
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.LoadingButton
import com.lagradost.quicknovel.ui.common.LoadingLine
import com.lagradost.quicknovel.ui.common.LoadingPoster
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseOperation
import com.lagradost.quicknovel.ui.common.downloadOutline
import com.lagradost.quicknovel.ui.common.html
import com.lagradost.quicknovel.ui.common.loading
import com.lagradost.quicknovel.ui.result.ResultPageAction
import com.lagradost.quicknovel.ui.result.ResultScreen
import com.lagradost.quicknovel.ui.result.ResultScreen.IconTextButton
import com.lagradost.quicknovel.ui.result.ResultScreen.IconTextButton2
import com.lagradost.quicknovel.ui.result.ResultState
import com.lagradost.quicknovel.ui.txt
import com.lagradost.quicknovel.update.GithubScreen.getStackTracePretty
import com.lagradost.quicknovel.util.AppUtils.openInBrowser
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.UIHelper.clipboardHelper
import com.mihon.material.padding
import kotlinx.coroutines.launch

object MainActivityScreen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Content(
        state: ResultState,
        onAction: (ResultPageAction) -> Unit,
        onDismissRequest: () -> Unit = {},
    ) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            dragHandle = {},
            sheetState = sheetState,
        ) {
            val scope = rememberCoroutineScope()
            InnerContent(state = state, onAction = onAction, onDismissRequest = {
                scope.launch {
                    sheetState.hide()
                    onDismissRequest()
                }
            })
        }
    }

    @Composable
    fun InnerContent(
        state: ResultState,
        onAction: (ResultPageAction) -> Unit,
        onDismissRequest: () -> Unit = {},
    ) {
        val innerPadding = PaddingValues(
            all = MaterialTheme.padding.large
        )
        if (state.loadingResponse) {
            LoadingScreen(
                innerPadding = innerPadding
            )
        } else if (state.responseError != null) {
            ErrorScreen(
                error = state.responseError,
                innerPadding = innerPadding,
                onAction = onAction,
                url = state.loadUrl
            )
        } else {
            ResultScreenImpl(
                state = state,
                onAction = onAction,
                onDismissRequest = onDismissRequest
            )
        }
    }

    @Composable
    fun ErrorScreen(
        innerPadding: PaddingValues,
        error: Throwable,
        onAction: (ResultPageAction) -> Unit,
        url: String,
    ) {
        var descriptionDialogShown by remember { mutableStateOf(false) }

        val message = error.getStackTracePretty()
        val title = error.message ?: stringResource(R.string.error_loading)
        if (descriptionDialogShown) {
            AlertDialog(containerColor = MaterialTheme.colorScheme.background, onDismissRequest = {
                descriptionDialogShown = false
            }, title = {
                Text(text = title)
            }, text = {
                Text(
                    text = error.getStackTracePretty(),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }, confirmButton = {
                WhiteButton(text = stringResource(R.string.copylog), onClick = {
                    clipboardHelper(
                        txt("Logcat"), message
                    )
                })
            }, dismissButton = {
                BlackButton(text = stringResource(R.string.close), onClick = {
                    descriptionDialogShown = false
                })
            })
        }

        Column(
            modifier = Modifier.padding(top = MaterialTheme.padding.large),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.large)
            ) {
                Icon(painter = painterResource(R.drawable.warning), contentDescription = null)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.padding.medium))

            val modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium)

            IconTextButton2(
                modifier = modifier,
                onClick = {
                    openInBrowser(url)
                },
                icon = painterResource(R.drawable.ic_baseline_public_24),
                text = stringResource(R.string.open_in_browser),
            )
            IconTextButton2(
                modifier = modifier,
                onClick = {
                    onAction(ResultPageAction.Reload)
                },
                icon = painterResource(R.drawable.refresh),
                text = stringResource(R.string.reload_error),
            )
            IconTextButton2(
                modifier = modifier,
                onClick = {
                    descriptionDialogShown = true
                },
                icon = painterResource(R.drawable.info_24px),
                text = stringResource(R.string.more_info),
            )
            /*Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier.heightIn(max=200.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .rounded()
                    .clickable {
                        descriptionDialogShown = true
                    }.padding(horizontal = MaterialTheme.padding.small))*/
        }
    }

    @Composable
    fun LoadingScreen(innerPadding: PaddingValues) {
        Column(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 50.dp, height = 75.dp)
                        .width(width = 100.dp)
                        .fillMaxHeight()
                        .loading()
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        space = MaterialTheme.padding.small,
                        alignment = Alignment.CenterVertically
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    LoadingLine()
                    LoadingLine(0.7f)
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
            ) {
                LoadingLine()
                LoadingLine()
                LoadingLine()
                LoadingLine(0.7f)

                LoadingButton()
                LoadingButton()
                LoadingButton()
                LoadingButton()
            }
        }
    }

    @Composable
    fun ResultScreenImpl(
        state: ResultState,
        onAction: (ResultPageAction) -> Unit,
        onDismissRequest: () -> Unit
    ) {
        state.dialog?.let { dialog ->
            ResultScreen.ResultScreenDialog(dialog = dialog, onAction = onAction)
        }
        val response = state.response ?: return

        var descriptionDialogShown by remember { mutableStateOf(false) }
        if (descriptionDialogShown && response.synopsis != null) {
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.background,
                properties = DialogProperties(usePlatformDefaultWidth = false),
                onDismissRequest = {
                    descriptionDialogShown = false
                },
                title = {
                    Text(text = response.name)
                },
                text = {
                    Text(
                        color = MaterialTheme.colorScheme.onBackground,
                        text = response.synopsis.html(),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                },
                confirmButton = {},
                dismissButton = {})
        }

        Column(
            modifier = Modifier.padding(top = MaterialTheme.padding.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.large),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)
            ) {
                AsyncImage(
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(width = 50.dp)
                        .height(height = 75.dp)
                        .rounded()
                        .clickable {
                            loadResult(url = response.url, apiName = response.apiName)
                            onDismissRequest()
                        },
                    model = response.imageRequest(),
                    contentDescription = stringResource(R.string.poster_descript),
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        space = MaterialTheme.padding.extraSmall,
                        alignment = Alignment.CenterVertically
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 75.dp)
                        .rounded()
                        .clickable(onClick = {
                            //descriptionDialogShown = true
                            loadResult(url = response.url, apiName = response.apiName)
                            onDismissRequest()
                        })
                        .padding(MaterialTheme.padding.small),
                ) {
                    Text(
                        text = response.name,
                        style = MaterialTheme.typography.titleMedium,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2
                    )

                    val chapters = response.chapters
                    val showChapters = chapters != null && chapters > 1
                    if (response.rating != null || showChapters) {
                        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                            if (response.rating != null) {
                                Text(
                                    text = LocalContext.current.getRating(response.rating),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (showChapters) {
                                Text(
                                    text = "$chapters " + stringResource(R.string.chapters),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    /*if (!response.synopsis.isNullOrBlank()) {
                        Text(
                            text = response.synopsis.html(),
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }*/
                }
            }

            val modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium)
            Column {
                if (!response.synopsis.isNullOrBlank()) {
                    Text(
                        text = response.synopsis.html(),
                        modifier = Modifier
                            .padding(
                                horizontal = MaterialTheme.padding.medium,
                            )
                            .rounded()
                            .clickable {
                                descriptionDialogShown = true
                            }
                            .padding(MaterialTheme.padding.small),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconTextButton2(
                    modifier = modifier,
                    onClick = {
                        onAction(ResultPageAction.OpenBookmark)
                    },
                    icon = painterResource(if (state.bookmark == ReadType.NONE) R.drawable.bookmark_add else R.drawable.bookmark_added),
                    text = stringResource(if (state.bookmark == ReadType.NONE) R.string.bookmark else state.bookmark.stringRes),
                )

                /*if (!response.synopsis.isNullOrBlank()) {
                    IconTextButton2(
                        modifier = modifier,
                        onClick = {
                            descriptionDialogShown = true
                        },
                        icon = painterResource(R.drawable.description),
                        text = stringResource(R.string.synopsis),
                    )
                }*/

                if (!response.isImported) {
                    IconTextButton2(
                        modifier = modifier,
                        onClick = {
                            loadResult(url = response.url, apiName = response.apiName)
                            onDismissRequest()
                        },
                        icon = painterResource(R.drawable.info_24px),
                        text = stringResource(R.string.more_info),
                    )
                }

                IconTextButton2(
                    modifier = modifier,
                    onClick = {
                        onAction(
                            ResultPageAction.ResultAction(
                                SearchResponseAction(
                                    response = response,
                                    operation = SearchResponseOperation.Stream
                                )
                            )
                        )
                    },
                    icon = painterResource(R.drawable.play_circle),
                    text = if (response.isImported) stringResource(R.string.read) else stringResource(
                        R.string.stream_read
                    ),
                    loading = response.generating
                )

                when {
                    response.downloadState != null && (!response.downloadState.downloadedChapters.isEmpty || response.downloadState.downloadStatus != BookDownloadStatus.Idle) -> {
                        Triple(
                            SearchResponseOperation.AskDelete,
                            R.string.delete,
                            R.drawable.delete_sweep
                        )
                    }

                    else -> {
                        Triple(
                            SearchResponseOperation.Download,
                            R.string.download,
                            R.drawable.arrow_circle_down_24px
                        )
                    }
                }.let { (operation, text, icon) ->
                    val loading =
                        response.downloadState?.downloadStatus == BookDownloadStatus.Pending
                    IconTextButton2(
                        modifier = modifier,
                        onClick = {
                            onAction(
                                ResultPageAction.ResultAction(
                                    SearchResponseAction(
                                        response = response,
                                        operation = operation
                                    )
                                )
                            )
                        },
                        loading = loading,
                        icon = painterResource(icon),
                        text = if (loading) stringResource(R.string.loading) else stringResource(
                            text
                        ),
                    )
                }
            }
        }
    }
}


@Composable
@PreviewLightDark
fun Preview() {
    CloudStreamPreviewTheme {
        Surface(
            contentColor = MaterialTheme.colorScheme.onBackground,
            color = MaterialTheme.colorScheme.background
        ) {
            InnerContent(
                state = ResultState(
                    loadingResponse = false,
                    responseError = null,//Throwable("Hello world"),
                    response = ImmutableSearchResponse.preview,
                    api = APIRepository(RoyalRoadProvider)
                ), onAction = { }, onDismissRequest = {})
        }
    }
}