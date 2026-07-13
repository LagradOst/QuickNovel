package com.lagradost.quicknovel.compose

import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors

@Composable
fun BaseDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,

    dismiss: () -> Unit,
    confirm: () -> Unit
) {
    AlertDialog(
        containerColor = colors.background,
        onDismissRequest = dismiss,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = { Button(onClick = confirm) { Text(text = confirmText) } },
        dismissButton = {
            Button(onClick = dismiss) { Text(text = dismissText) }
        }
    )
}