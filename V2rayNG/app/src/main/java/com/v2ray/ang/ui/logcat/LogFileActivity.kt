package com.v2ray.ang.ui.logcat

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows the contents of a single log file written by the core.
 */
class LogFileActivity : BaseComponentActivity() {

    companion object {
        const val EXTRA_PATH = "log_file_path"
        const val EXTRA_NAME = "log_file_name"
    }

    private val viewModel: LogFileViewModel by viewModels()

    private val filePath: String by lazy { intent.getStringExtra(EXTRA_PATH).orEmpty() }
    private val fileName: String by lazy {
        intent.getStringExtra(EXTRA_NAME) ?: File(filePath).name
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (filePath.isEmpty()) {
            finish()
        }
    }

    @Composable
    override fun ScreenContent() {
        LogFileScreen(
            viewModel = viewModel,
            title = fileName,
            path = filePath,
            onBackClick = { finish() },
            onShareClick = { shareLogFile() }
        )
    }

    private fun shareLogFile() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                val source = File(filePath)
                if (!source.isFile) {
                    withContext(Dispatchers.Main) { toast(R.string.toast_failure) }
                    return@launch
                }

                val shareDir = File(cacheDir, "shared_logs").apply { mkdirs() }
                shareDir.listFiles()?.forEach { it.delete() }

                val shared = File(shareDir, source.name)
                source.copyTo(shared, overwrite = true)

                FileProvider.getUriForFile(this@LogFileActivity, "${packageName}.cache", shared) to shared.name
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

                startActivity(Intent.createChooser(shareIntent, getString(R.string.logcat_share)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogFileScreen(
    viewModel: LogFileViewModel,
    title: String,
    path: String,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(path) { viewModel.load(path) }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = title,
                onBackClick = onBackClick,
                isLoading = isLoading,
                actions = {
                    IconButton(onClick = { viewModel.copyToClipboard() }) {
                        Icon(
                            painterResource(R.drawable.ic_copy),
                            contentDescription = stringResource(R.string.logcat_copy)
                        )
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(
                            painterResource(R.drawable.ic_share_24dp),
                            contentDescription = stringResource(R.string.logcat_share)
                        )
                    }
                    IconButton(onClick = { viewModel.clear(path) }) {
                        Icon(
                            painterResource(R.drawable.ic_delete_24dp),
                            contentDescription = stringResource(R.string.logcat_clear)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (lines.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.log_files_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollbar(listState)
                ) {
                    itemsIndexed(items = lines, key = { index, _ -> index }) { _, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        ItemDivider()
                    }
                }
            }
        }
    }
}
