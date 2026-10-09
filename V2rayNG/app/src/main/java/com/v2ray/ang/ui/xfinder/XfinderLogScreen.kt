package com.v2ray.ang.ui.xfinder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.XfPurpleLight

@Composable
fun XfinderLogScreen(onBack: () -> Unit, viewModel: XfinderLogViewModel) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Follow the newest line as it arrives.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }

    XfinderPage(
        title = stringResource(R.string.xf_logs_title),
        onBack = onBack,
        actions = {
            TextButton(onClick = viewModel::clear, enabled = lines.isNotEmpty()) {
                Text(stringResource(R.string.xf_logs_clear))
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            if (lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.xf_logs_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    // Lines repeat (same text twice is legal), so the key is the position in the append-only list.
                    itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
                        Text(
                            text = line,
                            modifier = Modifier.fillMaxWidth(),
                            color = XfPurpleLight,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                        )
                    }
                }
            }
        }
    }
}
