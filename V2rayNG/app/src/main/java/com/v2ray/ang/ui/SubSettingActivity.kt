package com.v2ray.ang.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubSettingActivity : BaseActivity() {
    private val viewModel: SubscriptionsViewModel by viewModels()
    private var qrCodeUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val url by qrCodeUrl
                SubSettingScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onAdd = { startActivity(Intent(this, SubEditActivity::class.java)) },
                    onUpdateAll = { updateAllSubscriptions() },
                    onEdit = { guid ->
                        startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", guid))
                    },
                    onRemove = { guid -> removeSubscription(guid) },
                    onShare = { shareUrl -> qrCodeUrl.value = shareUrl }
                )

                if (url != null) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { qrCodeUrl.value = null },
                        confirmButton = { TextButton(onClick = { qrCodeUrl.value = null }) { Text("OK") } },
                        title = { Text("QR Code") },
                        text = {
                            val bitmap = remember(url) { com.v2ray.ang.util.QRCodeDecoder.createQRCode(url!!) }
                            bitmap?.let {
                                androidx.compose.foundation.Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(300.dp).padding(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun updateAllSubscriptions() {
        showLoading()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.updateConfigViaSubAll()
            delay(500L)
            withContext(Dispatchers.Main) {
                if (result.successCount + result.failureCount + result.skipCount == 0) {
                    toast(R.string.title_update_subscription_no_subscription)
                } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toast(getString(R.string.title_update_subscription_result,
                        result.configCount, result.successCount, result.failureCount, result.skipCount))
                }
                hideLoading()
                viewModel.reload()
            }
        }
    }

    private fun removeSubscription(guid: String) {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.remove(guid)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            viewModel.remove(guid)
        }
    }

    private fun shareSubscription(url: String) {
        val share_method = resources.getStringArray(R.array.share_sub_method)
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setItems(share_method) { _, i ->
                try {
                    when (i) {
                        0 -> {
                            qrCodeUrl.value = url
                        }
                        1 -> Utils.setClipboard(this, url)
                    }
                } catch (e: Exception) {
                    com.v2ray.ang.util.LogUtil.e(AppConfig.TAG, "Share subscription failed", e)
                }
            }.show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onUpdateAll: () -> Unit,
    onEdit: (String) -> Unit,
    onRemove: (String) -> Unit,
    onShare: (String) -> Unit
) {
    val subscriptions by viewModel.subscriptionsFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_sub_setting)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                    IconButton(onClick = onUpdateAll) {
                        Icon(Icons.Default.Refresh, contentDescription = "Update All")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            items(subscriptions, key = { it.guid }) { sub ->
                SubscriptionItem(
                    subscription = sub,
                    onClick = { onEdit(sub.guid) },
                    onRemove = { onRemove(sub.guid) },
                    onShare = { onShare(sub.subscription.url) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
fun SubscriptionItem(
    subscription: SubscriptionCache,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subscription.subscription.remarks,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subscription.subscription.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = { 
                        showMenu = false
                        onClick() 
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = { 
                        showMenu = false
                        onShare() 
                    }
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = { 
                        showMenu = false
                        onRemove() 
                    }
                )
            }
        }
    }
}
