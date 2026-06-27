package com.v2ray.ang.ui

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.LogcatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogcatActivity : BaseActivity() {
    private val viewModel: LogcatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LogcatScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onCopyAll = { copyAll() },
                    onShareAll = { shareLogcat() },
                    onClearAll = { clearAll() },
                    onLogLongClick = { onLogLongClick(it) }
                )
            }
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.loadLogcat()
        }
    }

    private fun onLogLongClick(log: String) {
        Utils.setClipboard(this, log)
        toastSuccess(R.string.toast_success)
    }

    private fun copyAll() {
        val all = viewModel.getAll().joinToString("\n")
        Utils.setClipboard(this, all)
        toastSuccess(R.string.toast_success)
    }

    private fun clearAll() {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.clearLogcat()
        }
    }

    private fun shareLogcat() {
        lifecycleScope.launch(Dispatchers.IO) {
            val logText = viewModel.getAll().joinToString("\n")
            val result = try {
                val shareDir = File(cacheDir, "shared_logs").apply { mkdirs() }
                shareDir.listFiles()?.forEach { it.delete() }
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val logFile = File(shareDir, "v2rayNG_logcat_$timestamp.txt")
                logFile.writeText(logText, Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(this@LogcatActivity, "${packageName}.cache", logFile)
                uri to logFile.name
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast(e.localizedMessage ?: e.toString()) }
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
                startActivity(Intent.createChooser(shareIntent, getString(R.string.logcat_share)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogcatScreen(
    viewModel: LogcatViewModel,
    onBack: () -> Unit,
    onCopyAll: () -> Unit,
    onShareAll: () -> Unit,
    onClearAll: () -> Unit,
    onLogLongClick: (String) -> Unit
) {
    val logs by viewModel.logsFlow.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_logcat)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onCopyAll) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = "Copy All")
                    }
                    IconButton(onClick = onShareAll) {
                        Icon(Icons.Default.Share, contentDescription = "Share All")
                    }
                    IconButton(onClick = onClearAll) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    viewModel.filter(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text(stringResource(R.string.menu_item_search)) },
                singleLine = true
            )
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(logs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onLogLongClick(log) }
                            )
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
