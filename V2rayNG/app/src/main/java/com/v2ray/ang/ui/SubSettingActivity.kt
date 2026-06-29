package com.v2ray.ang.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.SelectListDialog
import com.v2ray.ang.compose.QRCodeDialog
import com.v2ray.ang.compose.ReorderableListItem
import com.v2ray.ang.compose.colorFabActive
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.MyContextWrapper
import com.v2ray.ang.util.QRCodeDecoder
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class SubSettingActivity : ComponentActivity() {
    private val viewModel: SubscriptionsViewModel by viewModels()
    private val isLoadingState = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                SubSettingScreen(
                    viewModel = viewModel,
                    isLoadingState = isLoadingState,
                    onBackClick = { finish() },
                    onAddClick = { startActivity(Intent(this, SubEditActivity::class.java)) },
                    onSubUpdate = { updateSubscriptions() },
                    onEditSub = { subId ->
                        startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", subId))
                    },
                    onRemoveSub = { subId -> removeSub(subId) },
                    onShareQRCode = { url -> QRCodeDecoder.createQRCode(url) },
                    onShareClipboard = { url ->
                        Utils.setClipboard(this, url)
                        toast(getString(R.string.toast_success))
                    },
                    shareSubMethodEntries = resources.getStringArray(R.array.share_sub_method).toList()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(MyContextWrapper.wrap(newBase, SettingsManager.getLocale()))
    }

    private fun removeSub(subId: String) {
        viewModel.remove(subId)
    }

    private fun updateSubscriptions() {
        isLoadingState.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            val result = AngConfigManager.updateConfigViaSubAll()
            delay(500L)
            launch(Dispatchers.Main) {
                if (result.successCount + result.failureCount + result.skipCount == 0) {
                    toast(R.string.title_update_subscription_no_subscription)
                } else if (result.successCount > 0 && result.failureCount + result.skipCount == 0) {
                    toast(getString(R.string.title_update_config_count, result.configCount))
                } else {
                    toast(
                        getString(
                            R.string.title_update_subscription_result,
                            result.configCount, result.successCount, result.failureCount, result.skipCount
                        )
                    )
                }
                isLoadingState.value = false
                viewModel.reload()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    isLoadingState: MutableStateFlow<Boolean>,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubUpdate: () -> Unit,
    onEditSub: (String) -> Unit,
    onRemoveSub: (String) -> Unit,
    onShareQRCode: (String) -> android.graphics.Bitmap?,
    onShareClipboard: (String) -> Unit,
    shareSubMethodEntries: List<String>
) {
    val subscriptions by viewModel.subsFlow.collectAsStateWithLifecycle()
    val isLoading by isLoadingState.collectAsState()
    var removeTarget by remember { mutableStateOf<String?>(null) }
    val confirmRemove = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, true)

    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showQRCodeBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.swap(from.index, to.index)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_sub_setting),
                onBackClick = onBackClick,
                isLoading = isLoading,
                actions = {
                    IconButton(onClick = onAddClick) {
                        Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.menu_item_add_config))
                    }
                    IconButton(onClick = onSubUpdate) {
                        Icon(painterResource(R.drawable.ic_restore_24dp), contentDescription = stringResource(R.string.title_sub_update))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            itemsIndexed(
                items = subscriptions,
                key = { _, item -> item.guid }
            ) { _, subCache ->
                ReorderableItem(reorderableState, key = subCache.guid) { isDragging ->
                    ReorderableListItem(
                        scope = this,
                        isDragging = isDragging
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subCache.subscription.remarks,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (subCache.subscription.url.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subCache.subscription.url,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = Utils.formatTimestamp(subCache.subscription.lastUpdated),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Row {
                                    IconButton(onClick = {
                                        shareTarget = Pair(subCache.guid, subCache.subscription.url)
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_share_24dp),
                                            contentDescription = "Share"
                                        )
                                    }
                                    IconButton(onClick = { onEditSub(subCache.guid) }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_edit_24dp),
                                            contentDescription = "Edit"
                                        )
                                    }
                                    IconButton(onClick = {
                                        if (confirmRemove) removeTarget = subCache.guid
                                        else onRemoveSub(subCache.guid)
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete_24dp),
                                            contentDescription = "Delete"
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Switch(
                                    checked = subCache.subscription.enabled,
                                    onCheckedChange = { checked ->
                                        val updated = subCache.subscription.copy()
                                        updated.enabled = checked
                                        viewModel.update(subCache.guid, updated)
                                    },
                                    modifier = Modifier.scale(0.7f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                        checkedTrackColor = colorFabActive
                                    )
                                )
                            }
                        }
                    }
                    AppDivider(modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
        }
    }

    if (shareTarget != null) {
        val (_, url) = shareTarget!!
        SelectListDialog(
            options = shareSubMethodEntries,
            onSelected = { index, _ ->
                shareTarget = null
                when (index) {
                    0 -> {
                        // QRCode
                        showQRCodeBitmap = onShareQRCode(url)
                    }
                    1 -> {
                        // Export to clipboard
                        onShareClipboard(url)
                    }
                }
            },
            onDismiss = { shareTarget = null }
        )
    }

    // QR Code Dialog
    if (showQRCodeBitmap != null) {
        QRCodeDialog(
            bitmap = showQRCodeBitmap,
            onDismiss = { showQRCodeBitmap = null }
        )
    }

    if (removeTarget != null) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = {
                onRemoveSub(removeTarget!!)
                removeTarget = null
            },
            onDismiss = { removeTarget = null }
        )
    }
}
