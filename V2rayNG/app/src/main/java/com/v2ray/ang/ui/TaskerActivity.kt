package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager

class TaskerActivity : BaseActivity() {
    private val lstData = mutableListOf<String>()
    private val lstGuid = mutableListOf<String>()
    
    private var selectedIndex by mutableIntStateOf(-1)
    private var startService by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lstData.add("Default")
        lstGuid.add(AppConfig.TASKER_DEFAULT_GUID)

        MmkvManager.decodeAllServerList().forEach { key ->
            MmkvManager.decodeServerConfig(key)?.let { config ->
                lstData.add(config.remarks)
                lstGuid.add(key)
            }
        }

        val bundle = intent?.getBundleExtra(AppConfig.TASKER_EXTRA_BUNDLE)
        startService = bundle?.getBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, false) ?: false
        val guid = bundle?.getString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, "")
        selectedIndex = lstGuid.indexOf(guid)

        setContent {
            MaterialTheme {
                TaskerScreen(
                    lstData = lstData,
                    selectedIndex = selectedIndex,
                    startService = startService,
                    onBack = { finish() },
                    onSave = { confirmFinish() },
                    onSelectIndex = { selectedIndex = it },
                    onToggleStartService = { startService = it }
                )
            }
        }
    }

    private fun confirmFinish() {
        if (selectedIndex < 0) return

        val extraBundle = Bundle().apply {
            putBoolean(AppConfig.TASKER_EXTRA_BUNDLE_SWITCH, startService)
            putString(AppConfig.TASKER_EXTRA_BUNDLE_GUID, lstGuid[selectedIndex])
        }
        
        val remarks = lstData[selectedIndex]
        val blurb = if (startService) "Start $remarks" else "Stop $remarks"

        val resultIntent = Intent().apply {
            putExtra(AppConfig.TASKER_EXTRA_BUNDLE, extraBundle)
            putExtra(AppConfig.TASKER_EXTRA_STRING_BLURB, blurb)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskerScreen(
    lstData: List<String>,
    selectedIndex: Int,
    startService: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onToggleStartService: (Boolean) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasker Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onSave, enabled = selectedIndex >= 0) {
                        Icon(Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Start service", modifier = Modifier.weight(1f))
                Switch(checked = startService, onCheckedChange = onToggleStartService)
            }
            
            HorizontalDivider()
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lstData) { index, item ->
                    ListItem(
                        modifier = Modifier.clickable { onSelectIndex(index) },
                        headlineContent = { Text(item) },
                        leadingContent = {
                            RadioButton(selected = index == selectedIndex, onClick = { onSelectIndex(index) })
                        }
                    )
                }
            }
        }
    }
}
