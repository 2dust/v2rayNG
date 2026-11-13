package com.v2ray.ang.ui.logcat

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.AppDialogButton
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.dpadMovePreviousNavigation
import com.v2ray.ang.ui.compose.dpadOrderedFocusNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.LocalAppSnackbar
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.ToastType
import com.v2ray.ang.ui.compose.dpadFocusOutline
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LogcatRow(val key: String, val raw: String, val tag: String, val content: String)

private fun parseLogcatRow(entry: LogcatEntry): LogcatRow {
    if (entry.text.isEmpty()) return LogcatRow(entry.key, "", "", "")
    val parts = entry.text.split("):", limit = 2)
    return LogcatRow(
        key = entry.key,
        raw = entry.text,
        tag = parts.first().split("(", limit = 2).first().trim(),
        content = if (parts.size > 1) parts.last().trim() else ""
    )
}

class LogcatActivity : BaseComponentActivity() {
    private val viewModel: LogcatViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        LogcatScreen(
            viewModel = viewModel,
            onBackClick = { finish() },
            onShareLogcat = { shareLogcat() }
        )
    }

    private fun shareLogcat() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logText = viewModel.filteredLogs.value.joinToString("\n")

            val result = try {
                val shareDir = File(cacheDir, "shared_logs").apply {
                    mkdirs()
                }

                shareDir.listFiles()?.forEach { it.delete() }

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val logFile = File(shareDir, "v2rayNG_logcat_$timestamp.txt")
                logFile.writeText(logText, Charsets.UTF_8)

                val uri = FileProvider.getUriForFile(
                    this@LogcatActivity,
                    "${packageName}.cache",
                    logFile
                )

                uri to logFile.name
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to share Logcat", e)
                withContext(Dispatchers.Main) {
                    toastError(R.string.toast_failure)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, result.first)
                    putExtra(Intent.EXTRA_SUBJECT, result.second)
                    putExtra(Intent.EXTRA_TITLE, result.second)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(contentResolver, result.second, result.first)
                }

                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(R.string.logcat_share)
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogcatScreen(
    viewModel: LogcatViewModel,
    onBackClick: () -> Unit,
    onShareLogcat: () -> Unit
) {
    val context = LocalContext.current
    val isTelevision = isTelevisionDevice()
    val scope = rememberCoroutineScope()
    val logs by viewModel.logEntries.collectAsStateWithLifecycle()
    val rows = remember(logs) { logs.map(::parseLogcatRow) }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    fun closeSearch() {
        searchQuery = ""
        viewModel.filter("")
        showSearch = false
    }
    BackHandler(enabled = isTelevision && showSearch, onBack = ::closeSearch)

    val snackbar = LocalAppSnackbar.current
    val successMessage = stringResource(R.string.toast_success)
    val listState = rememberLazyListState()
    val backFocusRequester = rememberDpadFocusRequester(requestFocus = !showSearch, requestKey = showSearch)
    val rowFocusTargets = rememberDpadFocusTargets(rows.map { it.key }) { FocusRequester() }
    val requestRowFocus = rememberLazyDpadFocus(rows.map { listOf(rowFocusTargets.getValue(it.key)) }) {
        listState.scrollToItem(it)
    }
    var detail by remember { mutableStateOf<LogcatRow?>(null) }
    detail?.let { row ->
        LogcatDetailDialog(row.raw) {
            detail = null
            rowFocusTargets[row.key]?.let(requestRowFocus)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_logcat),
                onBackClick = onBackClick,
                isLoading = isLoading,
                isSearchActive = showSearch,
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    searchQuery = it
                    viewModel.filter(it)
                },
                onSearchClose = ::closeSearch,
                searchPlaceholder = stringResource(R.string.menu_item_search),
                navigationFocusRequester = backFocusRequester,
                onMoveDown = { rows.firstOrNull()?.let { requestRowFocus(rowFocusTargets.getValue(it.key)) } ?: false },
                actionItems = buildList {
                    if (isTelevision) add(
                        AppTopBarAction(
                            icon = painterResource(R.drawable.ic_check_update_24dp),
                            label = stringResource(R.string.logcat_update),
                            onClick = viewModel::loadLogcat
                        )
                    )
                    if (!showSearch) add(
                        AppTopBarAction(
                            icon = painterResource(R.drawable.ic_search_24dp),
                            label = stringResource(R.string.menu_item_search),
                            onClick = { showSearch = true }
                        )
                    )
                    add(AppTopBarAction(
                        icon = painterResource(R.drawable.ic_delete_24dp),
                        label = stringResource(R.string.logcat_clear),
                        onClick = { scope.launch(Dispatchers.IO) { viewModel.clearLogcat() } }
                    ))
                    add(AppTopBarAction(
                        icon = painterResource(R.drawable.ic_copy),
                        label = stringResource(R.string.logcat_copy),
                        onClick = {
                            val all = viewModel.filteredLogs.value.joinToString("\n")
                            Utils.setClipboard(context, all)
                            snackbar.show(successMessage, ToastType.SUCCESS)
                        }
                    ))
                    add(AppTopBarAction(
                        icon = painterResource(R.drawable.ic_share_24dp),
                        label = stringResource(R.string.logcat_share),
                        onClick = onShareLogcat
                    ))
                }
            )
        },
        floatingActionButton = {
            if (!isTelevision) {
                FloatingActionButton(onClick = viewModel::loadLogcat, modifier = Modifier.navigationBarsPadding()) {
                    Icon(
                        painterResource(R.drawable.ic_restore_24dp),
                        contentDescription = stringResource(R.string.acc_refresh)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvSafeAreaPadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .dpadMovePreviousNavigation { backFocusRequester.requestFocus() }
                    .verticalScrollbar(listState),
                contentPadding = NavigationBarsBottomPadding()
            ) {
                itemsIndexed(items = rows, key = { _, row -> row.key }) { index, row ->
                    LogcatItem(
                        row = row,
                        focusRequester = rowFocusTargets.getValue(row.key),
                        onClick = { if (isTelevision) detail = row },
                        onLongClick = { Utils.setClipboard(context, row.raw) }
                    )
                    ItemDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogcatItem(row: LogcatRow, focusRequester: FocusRequester?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusOutline(focusRequester = focusRequester)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        Text(text = row.tag, style = MaterialTheme.typography.bodySmall)
        if (row.content.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = row.content, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A single log entry may be taller than the list viewport; Center opens its complete text. */
@Composable
private fun LogcatDetailDialog(text: String, onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val textFocus = rememberDpadFocusRequester()
    val closeFocus = remember { FocusRequester() }
    val focusOrder = listOf(textFocus, closeFocus)
    var viewportHeight by remember { mutableIntStateOf(0) }
    fun scroll(forward: Boolean): Boolean {
        if (if (forward) scrollState.canScrollForward else scrollState.canScrollBackward) {
            scope.launch { scrollState.scrollBy(viewportHeight * if (forward) 0.75f else -0.75f) }
        } else closeFocus.requestFocus()
        return true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_logcat)) },
        text = {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { viewportHeight = it.height }
                .dpadFocusOutline(textFocus)
                .dpadOrderedFocusNavigation(textFocus, focusOrder)
                .dpadVerticalFocusNavigation(onMoveUp = { scroll(false) }, onMoveDown = { scroll(true) })
                .focusable()
                .verticalScroll(scrollState)
                .padding(8.dp))
        },
        confirmButton = {
            AppDialogButton(
                stringResource(R.string.action_close), onDismiss, focusRequester = closeFocus,
                modifier = Modifier.dpadOrderedFocusNavigation(closeFocus, focusOrder)
                    .dpadVerticalFocusNavigation(onMoveUp = { textFocus.requestFocus() }, onMoveDown = { true })
            )
        }
    )
}
