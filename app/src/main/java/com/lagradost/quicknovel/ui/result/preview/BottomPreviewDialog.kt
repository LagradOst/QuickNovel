package com.lagradost.quicknovel.ui.result.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.quicknovel.DefaultBookmark
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.result.ResultPageAction
import kotlinx.coroutines.launch

@Composable
fun BottomPreview(
    response: ImmutableSearchResponse?,
    isLoading: Boolean,
    bookmarks: List<DefaultBookmark>,
    currentBookmarkId: Int,
    showMoreInfo: Boolean,
    onAction: (ResultPageAction) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
    ) {
        if (!isLoading || response?.synopsis != null) {
            BottomPreviewContent(
                response = response!!,
                bookmarks = bookmarks,
                currentBookmarkId = currentBookmarkId,
                showMoreInfo = showMoreInfo,
                onAction = onAction
            )
        } else {
            BottomPreviewLoading()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomPreviewDialog(
    response: ImmutableSearchResponse?,
    isLoading: Boolean,
    bookmarks: List<DefaultBookmark>,
    currentBookmarkId: Int,
    showMoreInfo: Boolean,
    onDismiss: () -> Unit,
    onAction: (ResultPageAction) -> Unit
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
            response = response,
            isLoading = isLoading,
            bookmarks = bookmarks,
            currentBookmarkId = currentBookmarkId,
            showMoreInfo = showMoreInfo,
            onAction = { action ->
                if (action is ResultPageAction.DismissDialog) {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            onDismiss()
                        }
                    }
                } else {
                    onAction(action)
                }
            }
        )
    }
}
