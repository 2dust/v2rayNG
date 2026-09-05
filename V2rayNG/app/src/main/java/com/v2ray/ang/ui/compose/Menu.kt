package com.v2ray.ang.ui.compose

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun <T> AppDropdownMenuItems(
    items: List<T>,
    labelRes: (T) -> Int,
    onSelected: (T) -> Unit,
    enabled: (T) -> Boolean = { true }
) {
    items.forEach { item ->
        DropdownMenuItem(
            text = { Text(stringResource(labelRes(item))) },
            onClick = { onSelected(item) },
            enabled = enabled(item)
        )
    }
}
