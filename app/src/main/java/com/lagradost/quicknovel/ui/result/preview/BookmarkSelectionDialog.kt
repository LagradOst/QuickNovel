package com.lagradost.quicknovel.ui.result.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.DefaultBookmark
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.ActionDialog
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.InputDialog
import com.lagradost.quicknovel.compose.SingleSelectDialog
import com.lagradost.quicknovel.ui.result.BookmarkAction
import com.lagradost.quicknovel.ui.result.ResultPageAction
import java.util.Collections

sealed class BookmarkLocalDialog {
    object Add : BookmarkLocalDialog()
    data class Rename(val library: DefaultBookmark) : BookmarkLocalDialog()
    data class Delete(val library: DefaultBookmark) : BookmarkLocalDialog()
    data class Merge(val library: DefaultBookmark) : BookmarkLocalDialog()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSelectionDialog(
    bookmarks: List<DefaultBookmark>,
    currentBookmarkId: Int,
    onDismiss: () -> Unit,
    onAction: (ResultPageAction) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var localDialog by remember { mutableStateOf<BookmarkLocalDialog?>(null) }

    // Reordering state
    val localLibraries = remember { mutableStateListOf<DefaultBookmark>() }
    LaunchedEffect(bookmarks) {
        localLibraries.clear()
        localLibraries.addAll(bookmarks.sortedBy { it.position })
    }

    when (val d = localDialog) {
        is BookmarkLocalDialog.Add -> {
            InputDialog(
                title = stringResource(R.string.library_create),
                initialValue = "",
                label = stringResource(R.string.library_name_hint),
                confirmText = stringResource(R.string.save),
                dismissText = stringResource(R.string.cancel),
                maxCharacters = 25,
                dismiss = { localDialog = null },
                confirm = {
                    onAction(ResultPageAction.ModifyBookmark(BookmarkAction.AddBookmark(it)))
                    localDialog = null
                }
            )
        }

        is BookmarkLocalDialog.Rename -> {
            InputDialog(
                title = stringResource(R.string.library_rename),
                initialValue = d.library.title,
                label = stringResource(R.string.library_name_hint),
                confirmText = stringResource(R.string.save),
                dismissText = stringResource(R.string.cancel),
                maxCharacters = 25,
                dismiss = { localDialog = null },
                confirm = {
                    onAction(ResultPageAction.ModifyBookmark(BookmarkAction.RenameBookmark(d.library, it)))
                    localDialog = null
                }
            )
        }

        is BookmarkLocalDialog.Delete -> {
            ActionDialog(
                title = stringResource(R.string.library_delete),
                text = stringResource(R.string.permanently_delete_format).format(d.library.title),
                confirmText = stringResource(R.string.ok),
                dismissText = stringResource(R.string.cancel),
                dismiss = { localDialog = null },
                confirm = {
                    onAction(ResultPageAction.ModifyBookmark(BookmarkAction.DeleteBookmark(d.library.id)))
                    localDialog = null
                }
            )
        }

        is BookmarkLocalDialog.Merge -> {
            val targetCandidates = localLibraries.filter { it.id != d.library.id }
            if (targetCandidates.isNotEmpty()) {
                SingleSelectDialog(
                    entries = targetCandidates.map { it.title },
                    selectedIndex = -1,
                    title = stringResource(R.string.library_merge),
                    dismiss = { localDialog = null },
                    confirm = { index ->
                        val target = targetCandidates.getOrNull(index)
                        if (target != null) {
                            onAction(ResultPageAction.ModifyBookmark(BookmarkAction.MergeBookmarks(d.library.id, target.id)))
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
                        onAction(ResultPageAction.ModifyBookmark(BookmarkAction.ReorderBookmarks(localLibraries.toList())))
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
                // Item "None"
                item {
                    val isSelected = currentBookmarkId == 0
                    BookmarkItem(
                        title = stringResource(R.string.type_none),
                        isSelected = isSelected,
                        isEditing = false,
                        isBuiltIn = true,
                        onClick = {
                            if (!isEditing) {
                                onAction(ResultPageAction.SetBookmark(0))
                                onDismiss()
                            }
                        }
                    )
                }

                itemsIndexed(localLibraries, key = { _, it -> it.id }) { index, library ->
                    val isSelected = library.id == currentBookmarkId
                    // Default libraries have IDs 1-5 and editable = false
                    val isSystemLibrary = library.id in 1..5 || !library.editable
                    BookmarkItem(
                        title = library.title,
                        isSelected = isSelected,
                        isEditing = isEditing,
                        isBuiltIn = isSystemLibrary,
                        onClick = {
                            if (isEditing) {
                                // Rename is allowed for all libraries!
                                localDialog = BookmarkLocalDialog.Rename(library)
                            } else {
                                onAction(ResultPageAction.SetBookmark(library.id))
                                onDismiss()
                            }
                        },
                        onMoveUp = {
                            if (index > 0) {
                                Collections.swap(localLibraries, index, index - 1)
                            }
                        },
                        onMoveDown = {
                            if (index < localLibraries.size - 1) {
                                Collections.swap(localLibraries, index, index + 1)
                            }
                        },
                        onDelete = { localDialog = BookmarkLocalDialog.Delete(library) },
                        onMerge = { localDialog = BookmarkLocalDialog.Merge(library) }
                    )
                }
            }

            // Bottom "Add" button
            if (isEditing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { localDialog = BookmarkLocalDialog.Add }
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
                    Spacer(Modifier.size(18.dp).clickable(onClick = {
                        onMoveUp()
                    }))
                    /*
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(18.dp)) {
                        Icon(painterResource(R.drawable.ic_baseline_arrow_upward_24), null, tint = colors.onBackground, modifier = Modifier.size(12.dp))
                    }*/
                    Spacer(Modifier.size(18.dp).clickable(onClick = {
                        onMoveDown()
                    }))
                    /*
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(18.dp)) {
                        Icon(painterResource(R.drawable.ic_baseline_arrow_downward_24), null, tint = colors.onBackground, modifier = Modifier.size(12.dp))
                    }*/
                }
            } else if (isSelected && !isEditing) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_check_24),
                    contentDescription = null,
                    tint = colors.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = title,
            color = if (isSelected || isEditing) colors.onBackground else colors.onSurfaceVariant,
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
