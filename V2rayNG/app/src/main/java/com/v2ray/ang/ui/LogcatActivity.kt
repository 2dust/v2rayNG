package com.v2ray.ang.ui

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.LocalAppSnackbar
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.LogcatViewModel
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogcatActivity : BaseComponentActivity() {
    private val viewModel: LogcatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

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
                withContext(Dispatchers.Main) {
                    toast(e.localizedMessage ?: e.toString())
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
    val scope = rememberCoroutineScope()
    val logs by viewModel.filteredLogs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val snackbar = LocalAppSnackbar.current
    val listState = rememberLazyListState()

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
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
                onSearchClose = {
                    searchQuery = ""
                    viewModel.filter("")
                    showSearch = false
                },
                searchPlaceholder = stringResource(R.string.menu_item_search),
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                painterResource(R.drawable.ic_search_24dp),
                                contentDescription = "filter"
                            )
                        }
                    }
                    IconButton(onClick = {
                        val all = viewModel.filteredLogs.value.joinToString("\n")
                        Utils.setClipboard(context, all)
                        snackbar.showInfo(context,R.string.toast_success)
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_copy),
                            contentDescription = stringResource(R.string.logcat_copy)
                        )
                    }
                    IconButton(onClick = { onShareLogcat() }) {
                        Icon(
                            painterResource(R.drawable.ic_share_24dp),
                            contentDescription = stringResource(R.string.logcat_share)
                        )
                    }
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) { viewModel.clearLogcat() }
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_delete_24dp),
                            contentDescription = stringResource(R.string.logcat_clear)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.loadLogcat()
            }) {
                Icon(
                    painterResource(R.drawable.ic_restore_24dp),
                    contentDescription = stringResource(R.string.pull_down_to_refresh)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(listState)
            ) {
                itemsIndexed(items = logs, key = { index, _ -> index }) { _, log ->
                    LogcatItem(log = log, onLongClick = { Utils.setClipboard(context, log) })
                    AppDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogcatItem(log: String, onLongClick: () -> Unit) {
    val (tag, content) = if (log.isEmpty()) {
        "" to ""
    } else {
        val parts = log.split("):", limit = 2)
        val tagPart = parts.first().split("(", limit = 2).first().trim()
        val contentPart = if (parts.size > 1) parts.last().trim() else ""
        tagPart to contentPart
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(8.dp)
    ) {
        Text(text = tag, style = MaterialTheme.typography.bodySmall)
        if (content.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = content, style = MaterialTheme.typography.bodySmall)
        }
    }
}
