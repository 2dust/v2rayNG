package com.v2ray.ang.ui.subscription

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.settings.PhoneSettingsRows
import com.v2ray.ang.ui.settings.SettingsRows
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal enum class SubscriptionShareAction(@StringRes val labelRes: Int) {
    QRCode(R.string.share_subscription_qrcode),
    Clipboard(R.string.share_subscription_clipboard)
}

private data class SubscriptionDeleteTarget(val guid: String, val name: String)

abstract class BaseSubSettingActivity : BaseComponentActivity() {
    protected val viewModel: SubscriptionsViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        if (!isFinishing) viewModel.reload()
    }

    protected fun openEditor(subId: String? = null) {
        startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", subId))
    }

    protected fun copyLink(url: String) {
        Utils.setClipboard(this, url)
        toast(getString(R.string.toast_success))
    }
}

class SubSettingActivity : BaseSubSettingActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION) {
            startActivity(Intent(this, TvSubSettingActivity::class.java))
            finish()
        }
    }

    @Composable
    override fun ScreenContent() {
        if (isFinishing) return
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        SubSettingScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            onBackClick = { finish() },
            onAddClick = { openEditor() },
            onSubUpdate = { viewModel.updateSubscriptions() },
            onEditSub = ::openEditor,
            onRemoveSub = { viewModel.remove(it) },
            onShareQRCode = viewModel::shareQRCode,
            onShareClipboard = ::copyLink
        )
    }
}

@Composable
fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onSubUpdate: () -> Unit,
    onEditSub: (String) -> Unit,
    onRemoveSub: (String) -> Unit,
    onShareQRCode: (String) -> Unit,
    onShareClipboard: (String) -> Unit
) {
    val subscriptions by viewModel.subsFlow.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<SubscriptionDeleteTarget?>(null) }
    val options by viewModel.options.collectAsStateWithLifecycle()
    val confirmRemove = AppConfig.PREF_CONFIRM_REMOVE in options

    var shareTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val qrCodeBitmap by viewModel.qrCode.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.move(from.key as String, to.key as String).join()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_sub_setting),
                onBackClick = onBackClick,
                isLoading = isLoading,
                actions = {
                    IconButton(onClick = onAddClick) {
                        Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.acc_add_subscription))
                    }
                    IconButton(onClick = { showUpdateDialog = true }) {
                        Icon(painterResource(R.drawable.ic_restore_24dp), contentDescription = stringResource(R.string.acc_update_subscriptions))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(lazyListState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            itemsIndexed(
                items = subscriptions.orEmpty(),
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
                            SubscriptionSummary(subCache.subscription, Modifier.weight(1f))

                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Row {
                                    if (subCache.subscription.url.isNotEmpty()) {
                                        IconButton(onClick = {
                                            shareTarget = Pair(subCache.guid, subCache.subscription.url)
                                        }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_share_24dp),
                                                contentDescription = stringResource(R.string.acc_share_subscription)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onEditSub(subCache.guid) }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_edit_24dp),
                                            contentDescription = stringResource(R.string.acc_edit)
                                        )
                                    }
                                    IconButton(onClick = {
                                        if (confirmRemove) removeTarget = SubscriptionDeleteTarget(subCache.guid, subCache.subscription.remarks)
                                        else onRemoveSub(subCache.guid)
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete_24dp),
                                            contentDescription = stringResource(R.string.acc_delete)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Switch(
                                    checked = subCache.subscription.enabled,
                                    onCheckedChange = { viewModel.setEnabled(subCache.guid, it) },
                                    modifier = Modifier.scale(0.7f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                                        checkedTrackColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                    }
                    ItemDivider()
                }
            }
        }
    }

    if (shareTarget != null) {
        val (_, url) = shareTarget!!
        SelectListDialog(
            options = SubscriptionShareAction.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { action ->
                shareTarget = null
                when (action) {
                    SubscriptionShareAction.QRCode -> onShareQRCode(url)
                    SubscriptionShareAction.Clipboard -> onShareClipboard(url)
                }
            },
            onDismiss = { shareTarget = null }
        )
    }

    // QR Code Dialog
    if (qrCodeBitmap != null) {
        QRCodeDialog(
            bitmap = qrCodeBitmap,
            onDismiss = viewModel::dismissQRCode
        )
    }

    removeTarget?.let { target ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_subscription_group),
            itemName = target.name,
            onConfirm = {
                removeTarget = null
                onRemoveSub(target.guid)
            },
            onDismiss = { removeTarget = null }
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            text = { SubscriptionUpdateOptions(options, viewModel::setOption, PhoneSettingsRows) },
            confirmButton = {
                TextButton(onClick = { showUpdateDialog = false; onSubUpdate() }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
internal fun SubscriptionSummary(group: SubscriptionItem, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(group.remarks, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (group.url.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(group.url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(2.dp))
        Text(Utils.formatTimestamp(group.lastUpdated), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal enum class SubscriptionUpdateOption(val key: String, @StringRes val title: Int, @StringRes val summary: Int? = null) {
    Update(AppConfig.PREF_UPDATE_SUBSCRIPTION, R.string.title_sub_update),
    Test(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, R.string.title_pref_auto_test_after_update_subscription,
        R.string.summary_pref_auto_test_after_update_subscription),
    RemoveInvalid(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, R.string.title_pref_auto_remove_invalid_after_test,
        R.string.summary_pref_auto_remove_invalid_after_test),
    Sort(AppConfig.PREF_AUTO_SORT_AFTER_TEST, R.string.title_pref_auto_sort_after_test, R.string.summary_pref_auto_sort_after_test);

    fun isAvailable(enabled: Set<String>) = this == Update || this == Test || Test.key in enabled
}

@Composable
internal fun SubscriptionUpdateOptions(enabled: Set<String>, onChange: (String, Boolean) -> Unit, rows: SettingsRows) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SubscriptionUpdateOption.entries.forEach { option ->
            rows.SettingsSwitchItem(stringResource(option.title), option.summary?.let { stringResource(it) },
                checked = option.key in enabled, enabled = option.isAvailable(enabled), onCheckedChange = { onChange(option.key, it) })
        }
    }
}
