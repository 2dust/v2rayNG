package com.v2ray.ang.ui

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.extension.toast
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.LogcatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogcatActivity : ComponentActivity() {
    private val viewModel: LogcatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                LogcatScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onShareLogcat = { shareLogcat() }
                )
            }
        }
        toast(getString(R.string.pull_down_to_refresh))
    }

    private fun shareLogcat() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logText = viewModel.getAll().joinToString("\n")

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
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_logcat),
                onBackClick = onBackClick,
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
                                painterResource(R.drawable.ic_outline_filter_alt_24),
                                contentDescription = "filter"
                            )
                        }
                    }
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) { viewModel.clearLogcat() }
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_delete_24dp),
                            contentDescription = stringResource(R.string.logcat_clear)
                        )
                    }
                    IconButton(onClick = {
                        val all = viewModel.getAll().joinToString("\n")
                        Utils.setClipboard(context, all)
                        context.toast(context.getString(R.string.toast_success))
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_copy),
                            contentDescription = stringResource(R.string.logcat_copy)
                        )
                    }
                    IconButton(onClick = onShareLogcat) {
                        Icon(
                            painterResource(R.drawable.ic_share_24dp),
                            contentDescription = stringResource(R.string.logcat_share)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch(Dispatchers.IO) { viewModel.loadLogcat() }
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
