package com.v2ray.ang.ui.logcat

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppSearchState
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val RowHorizontalPad = 12.dp
private val RowVerticalPad = 8.dp
private val TagContentGap = 4.dp
private val MinRowHeight = 48.dp

/** Keeps the newest row clear of the refresh FAB (56.dp) plus its margin. */
private val ListBottomPad = 88.dp

private const val LogLineContentType = "log-line"

/** Long log rows stay readable without letting one entry own the whole viewport. */
private const val ContentMaxLines = 12

@Composable
fun LogcatScreen(
    viewModel: LogcatViewModel,
    onPlatformEvent: (LogcatEvent) -> Boolean
) {
    val dispatch = remember(viewModel) { viewModel::onAction }

    BackHandler { dispatch(LogcatAction.Back) }

    BaseScreen(
        viewModel = viewModel,
        showLoading = false,
        onEvent = { event ->
            when (event) {
                is LogcatEvent -> onPlatformEvent(event)
                else -> false
            }
        },
        topBar = {
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            val search by rememberSearchState(viewModel)
            LogcatTopBar(
                isLoading = isLoading,
                isSearchActive = search.active,
                query = search.query,
                onAction = dispatch
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { dispatch(LogcatAction.Refresh) },
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_restore_24dp),
                    contentDescription = stringResource(R.string.acc_refresh)
                )
            }
        }
    ) { uiState, action ->
        LogcatContent(lines = uiState.lines, onAction = action)
    }
}

@Immutable
private data class LogcatSearchState(
    val active: Boolean = false,
    val query: String = ""
)

@Composable
private fun rememberSearchState(viewModel: LogcatViewModel): State<LogcatSearchState> {
    val flow = remember(viewModel) {
        viewModel.uiState
            .map { LogcatSearchState(it.searchActive, it.query) }
            .distinctUntilChanged()
    }
    val initial = remember(viewModel) {
        viewModel.uiState.value.let { LogcatSearchState(it.searchActive, it.query) }
    }
    return flow.collectAsStateWithLifecycle(initialValue = initial)
}

@Composable
private fun LogcatTopBar(
    isLoading: Boolean,
    isSearchActive: Boolean,
    query: String,
    onAction: (LogcatAction) -> Unit,
    modifier: Modifier = Modifier
) {
    AppTopBar(
        title = stringResource(R.string.title_logcat),
        onBackClick = { onAction(LogcatAction.Back) },
        modifier = modifier,
        isLoading = isLoading,
        searchState = if (isSearchActive) {
            AppSearchState(
                isActive = true,
                query = query,
                placeholder = stringResource(R.string.menu_item_search)
            )
        } else {
            null
        },
        onSearchQueryChange = { onAction(LogcatAction.QueryChanged(it)) },
        onSearchClose = { onAction(LogcatAction.SearchClosed) },
        actions = {
            if (!isSearchActive) {
                IconButton(onClick = { onAction(LogcatAction.SearchOpened) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search_24dp),
                        contentDescription = stringResource(R.string.acc_search)
                    )
                }
            }
            IconButton(onClick = { onAction(LogcatAction.CopyAll) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_copy),
                    contentDescription = stringResource(R.string.acc_copy_log)
                )
            }
            IconButton(onClick = { onAction(LogcatAction.Share) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_share_24dp),
                    contentDescription = stringResource(R.string.acc_share_log)
                )
            }
            IconButton(onClick = { onAction(LogcatAction.Clear) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = stringResource(R.string.acc_clear_log)
                )
            }
        }
    )
}

@Composable
private fun LogcatContent(
    lines: List<LogLine>,
    onAction: (LogcatAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val copyLabel = stringResource(R.string.acc_copy_log)

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .verticalScrollbar(listState),
        contentPadding = NavigationBarsBottomPadding(extra = ListBottomPad)
    ) {
        items(
            items = lines,
            key = { it.id },
            contentType = { LogLineContentType }
        ) { line ->
            LogcatItem(
                line = line,
                copyLabel = copyLabel,
                onLongClick = { onAction(LogcatAction.LineLongPressed(line.raw)) }
            )
            ItemDivider()
        }
    }
}

@Composable
private fun LogcatItem(
    line: LogLine,
    copyLabel: String,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val copy by rememberUpdatedState(onLongClick)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinRowHeight)
            .semantics(mergeDescendants = true) {
                onLongClick(label = copyLabel) {
                    copy()
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        copy()
                    }
                )
            }
            .padding(horizontal = RowHorizontalPad, vertical = RowVerticalPad)
    ) {
        Text(
            text = line.tag,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (line.content.isNotEmpty()) {
            Spacer(modifier = Modifier.height(TagContentGap))
            Text(
                text = line.content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = ContentMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ===== previews =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogcatContentPreview() = AppTheme {
    LogcatContent(
        lines = listOf(
            LogLine(
                id = 0L,
                tag = "01-02 03:04:05.678 I/GoLog",
                content = "core started, version 26.2.6",
                raw = "01-02 03:04:05.678 I/GoLog(1234): core started, version 26.2.6"
            ),
            LogLine(
                id = 1L,
                tag = "01-02 03:04:06.001 E/System.err",
                content = "java.net.SocketTimeoutException: failed to connect to " +
                    "example.invalid/203.0.113.9 (port 443) after 5000ms",
                raw = "01-02 03:04:06.001 E/System.err(1234): timeout"
            ),
            LogLine(
                id = 2L,
                tag = "01-02 03:04:07.120 W/com.v2ray.ang",
                content = "",
                raw = "01-02 03:04:07.120 W/com.v2ray.ang(1234): "
            )
        ),
        onAction = {}
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LogcatTopBarPreview() = AppTheme {
    Column {
        LogcatTopBar(isLoading = true, isSearchActive = false, query = "", onAction = {})
        LogcatTopBar(isLoading = false, isSearchActive = true, query = "GoLog", onAction = {})
    }
}
