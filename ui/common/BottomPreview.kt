package com.lagradost.quicknovel.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
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
import coil3.network.httpHeaders
import com.lagradost.quicknovel.DEFAULT_BOOKMARKS
import com.lagradost.quicknovel.DefaultBookmark
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.ActionDialog
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.InputDialog
import com.lagradost.quicknovel.compose.SingleSelectDialog
import com.lagradost.quicknovel.compose.ripple
import kotlinx.coroutines.launch
import java.util.Collections

data class NovelPreviewData(
    val title: String,
    val author: String?,
    val poster: Any?,
    val posterHeaders: Map<String, String>? = null,
    val rating: String?,
    val status: String?,
    val chapters: String?,
    val description: String?,
    val isBookmarked: Boolean = false,
    val bookmarkText: String? = null,
    val showBookmark: Boolean = true,
    val showMoreInfo: Boolean = true,
    val url: String,
    val apiName: String,
)

@Composable
fun BottomPreview(
    data: NovelPreviewData?,
    isLoading: Boolean,
    onDeleteClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    onReadMoreClick: () -> Unit = {},
    onMoreInfoClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
    ) {
        if (data != null) {
            BottomPreviewContent(
                data = data,
                onDeleteClick = onDeleteClick,
                onBookmarkClick = onBookmarkClick,
                onReadMoreClick = onReadMoreClick,
                onMoreInfoClick = onMoreInfoClick
            )
        } else if (isLoading) {
            BottomPreviewLoading()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomPreviewDialog(
    data: NovelPreviewData?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onDeleteClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    onReadMoreClick: () -> Unit = {},
    onMoreInfoClick: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        dragHandle = null,
        shape = RoundedCornerShape(7.dp)
    ) {
        BottomPreview(
            data = data,
            isLoading = isLoading,
            onDeleteClick = onDeleteClick,
            onBookmarkClick = onBookmarkClick,
            onReadMoreClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismiss()
                        onReadMoreClick()
                    }
                }
            },
            onMoreInfoClick = onMoreInfoClick
        )
    }
}

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
    data: NovelPreviewData,
    onDeleteClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onReadMoreClick: () -> Unit,
    onMoreInfoClick: () -> Unit,
) {
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
                    .size(88.dp, 138.dp),
                shape = RoundedCornerShape(dimensionResource(R.dimen.roundedImageRadius)),
                color = colors.surfaceVariant
            ) {
                AsyncImage(
                    model = if (data.poster is String && data.posterHeaders != null) {
                        coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(data.poster)
                            .httpHeaders(coil3.network.NetworkHeaders.Builder().also { builder ->
                                data.posterHeaders.forEach { (k, v) -> builder[k] = v }
                            }.build())
                            .build()
                    } else {
                        data.poster
                    },
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
                        text = data.title,
                        color = colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(end = 30.dp)
                            .align(Alignment.CenterStart),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = onDeleteClick,
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
                    data.rating?.let { ResultInfoText(it) }
                    data.status?.let { ResultInfoText(it) }
                    data.chapters?.let { ResultInfoText(it) }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Description
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = if (data.description.isNullOrBlank()) stringResource(R.string.no_data) else data.description,
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
            if (data.showBookmark) {
                Button(
                    onClick = onBookmarkClick,
                    modifier = Modifier.weight(1f),
                    colors = BaseStyles.blackButtonColors,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(if (data.isBookmarked) R.drawable.ic_baseline_bookmark_24 else R.drawable.ic_baseline_bookmark_border_24),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = data.bookmarkText ?: stringResource(R.string.bookmark))
                }
            }

            if (data.showMoreInfo) {
                Button(
                    onClick = onReadMoreClick,
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
fun ResultInfoText(text: String) {
    Text(
        text = text,
        color = colors.onBackground,
        fontSize = 12.sp,
        modifier = Modifier.wrapContentSize(Alignment.Center)
    )
}

sealed class LibraryLocalDialog {
    object Add : LibraryLocalDialog()
    data class Rename(val library: DefaultBookmark) : LibraryLocalDialog()
    data class Delete(val library: DefaultBookmark) : LibraryLocalDialog()
    data class Merge(val library: DefaultBookmark) : LibraryLocalDialog()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSelectionDialog(
    libraries: List<DefaultBookmark>,
    currentLibraryId: Int,
    onDismiss: () -> Unit,
    onLibrarySelected: (Int) -> Unit,
    onAddLibrary: (String) -> Unit,
    onRenameLibrary: (DefaultBookmark, String) -> Unit,
    onDeleteLibrary: (Int) -> Unit,
    onMergeLibrary: (Int, Int) -> Unit,
    onReorder: (List<DefaultBookmark>) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var localDialog by remember { mutableStateOf<LibraryLocalDialog?>(null) }
    val builtInKeys = remember { DEFAULT_BOOKMARKS.map { it.key }.toSet() }

    // Reordering state
    val listState = remember { mutableStateListOf<DefaultBookmark>().apply { addAll(libraries) } }
    LaunchedEffect(libraries) {
        listState.clear()
        listState.addAll(libraries)
    }

    when (val d = localDialog) {
        is LibraryLocalDialog.Add -> {
            InputDialog(
                title = stringResource(R.string.library_create),
                initialValue = "",
                label = stringResource(R.string.library_name_hint),
                confirmText = stringResource(R.string.save),
                dismissText = stringResource(R.string.cancel),
                dismiss = { localDialog = null },
                confirm = {
                    onAddLibrary(it)
                    localDialog = null
                }
            )
        }

        is LibraryLocalDialog.Rename -> {
            InputDialog(
                title = stringResource(R.string.library_rename),
                initialValue = d.library.title,
                label = stringResource(R.string.library_name_hint),
                confirmText = stringResource(R.string.save),
                dismissText = stringResource(R.string.cancel),
                dismiss = { localDialog = null },
                confirm = {
                    onRenameLibrary(d.library, it)
                    localDialog = null
                }
            )
        }

        is LibraryLocalDialog.Delete -> {
            ActionDialog(
                title = stringResource(R.string.library_delete),
                text = stringResource(R.string.permanently_delete_format).format(d.library.title),
                confirmText = stringResource(R.string.ok),
                dismissText = stringResource(R.string.cancel),
                dismiss = { localDialog = null },
                confirm = {
                    onDeleteLibrary(d.library.id)
                    localDialog = null
                }
            )
        }

        is LibraryLocalDialog.Merge -> {
            val targetCandidates = listState.filter { it.id != d.library.id }
            if (targetCandidates.isNotEmpty()) {
                SingleSelectDialog(
                    entries = targetCandidates.map { it.title },
                    selectedIndex = -1,
                    title = stringResource(R.string.library_merge),
                    dismiss = { localDialog = null },
                    confirm = { index ->
                        val target = targetCandidates.getOrNull(index)
                        if (target != null) {
                            onMergeLibrary(d.library.id, target.id)
                        }
                        localDialog = null
                    }
                )
            } else {
                localDialog = null
            }
        }

        null -> {}
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        dragHandle = null,
        shape = RoundedCornerShape(7.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.bookmark),
                    color = colors.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { 
                    if (isEditing) {
                        onReorder(listState.toList())
                    }
                    isEditing = !isEditing 
                }) {
                    Icon(
                        painter = painterResource(if (isEditing) R.drawable.ic_sharp_clear_24 else R.drawable.ic_baseline_edit_24),
                        contentDescription = stringResource(R.string.library_rename),
                        tint = colors.onBackground
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                // Item "Ninguno" (Especial)
                item {
                    val isSelected = currentLibraryId == 0
                    BookmarkItem(
                        title = stringResource(R.string.type_none),
                        isSelected = isSelected,
                        isEditing = false, // None is never editable
                        isBuiltIn = true,
                        onClick = {
                            if (!isEditing) {
                                onLibrarySelected(0)
                                onDismiss()
                            }
                        }
                    )
                }

                itemsIndexed(listState, key = { _, it -> it.id }) { index, library ->
                    val isSelected = library.id == currentLibraryId
                    val isBuiltIn = library.key in builtInKeys
                    BookmarkItem(
                        title = library.title,
                        isSelected = isSelected,
                        isEditing = isEditing,
                        isBuiltIn = isBuiltIn,
                        onClick = {
                            if (isEditing) {
                                if (!isBuiltIn) {
                                    localDialog = LibraryLocalDialog.Rename(library)
                                }
                            } else {
                                onLibrarySelected(library.id)
                                onDismiss()
                            }
                        },
                        onMoveUp = {
                            if (index > 0) {
                                Collections.swap(listState, index, index - 1)
                            }
                        },
                        onMoveDown = {
                            if (index < listState.size - 1) {
                                Collections.swap(listState, index, index + 1)
                            }
                        },
                        onDelete = { localDialog = LibraryLocalDialog.Delete(library) },
                        onMerge = { localDialog = LibraryLocalDialog.Merge(library) }
                    )
                }
            }

            // Bottom "Add" button
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { localDialog = LibraryLocalDialog.Add }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_add_24),
                        contentDescription = null,
                        tint = colors.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.add_new_library),
                        color = colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkItem(
    title: String,
    isSelected: Boolean,
    isEditing: Boolean,
    isBuiltIn: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMerge: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Icon
        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (isEditing && title != stringResource(R.string.type_none)) {
                Column {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(18.dp)) {
                        Icon(painterResource(R.drawable.ic_baseline_arrow_upward_24), null, tint = colors.onBackground, modifier = Modifier.size(12.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(18.dp)) {
                        Icon(painterResource(R.drawable.ic_baseline_arrow_downward_24), null, tint = colors.onBackground, modifier = Modifier.size(12.dp))
                    }
                }
            } else if (isSelected && !isEditing) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_check_24),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = title,
            color = if (isSelected && !isEditing) colors.primary else colors.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        )

        if (isEditing && !isBuiltIn) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_more_vert_24),
                        contentDescription = null,
                        tint = colors.onBackground
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(colors.surfaceVariant)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_merge)) },
                        onClick = {
                            showMenu = false
                            onMerge()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBottomPreview() {
    CloudStreamTheme {
        BottomPreview(
            data = NovelPreviewData(
                title = "The Perfect Run",
                poster = null,
                rating = "Rated: 8.5/10.0",
                status = "Ongoing",
                chapters = "121ch",
                description = "Ryan Quicksave Romano is an eccentric adventurer with a strange power: he can create a save-point in time and redo his life whenever he dies. Arriving in New Rome, the glitzy capital of sin of a rebuilding Europe, he finds the city torn between mega-corporations, sponsored heroes, superpowered criminals, and true monsters. It's a time of chaos, where potions can grant the power to rule the world and dangers lurk everywhere. ",
                url = "",
                apiName = "",
                author = "Void"
            ),
            isLoading = false
        )
    }
}
