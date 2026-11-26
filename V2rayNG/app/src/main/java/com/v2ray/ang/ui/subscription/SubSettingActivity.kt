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
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.extension.toast
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.ui.settings.PhoneSettingsRows
import com.v2ray.ang.util.Utils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

internal enum class SubscriptionShareAction(@StringRes val labelRes: Int) {
    QRCode(R.string.share_subscription_qrcode),
    Clipboard(R.string.share_subscription_clipboard)
}

abstract class BaseSubSettingActivity : BaseComponentActivity() {
    protected val viewModel: SubscriptionsViewModel by viewModels()

    private var forwardingToTv = false

    protected open fun televisionDestination(): Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val destination = televisionDestination()
        forwardingToTv = destination != null
        super.onCreate(savedInstanceState)
        if (destination != null) {
            startActivity(destination)
            finish()
            return
        }
    }

    @Composable
    final override fun ScreenContent() {
        if (forwardingToTv) return
        SubscriptionListContent()
    }

    @Composable
    internal abstract fun SubscriptionListContent()

    override fun onResume() {
        super.onResume()
        if (!forwardingToTv) viewModel.reload()
    }

    protected fun openEditor(subId: String? = null) {
        startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", subId))
    }

    internal fun shareLink(action: SubscriptionShareAction, url: String) {
        when (action) {
            SubscriptionShareAction.QRCode -> viewModel.shareQRCode(url)
            SubscriptionShareAction.Clipboard -> {
                Utils.setClipboard(this, url)
                toast(getString(R.string.toast_success))
            }
        }
    }
}

class SubSettingActivity : BaseSubSettingActivity() {
    override fun televisionDestination(): Intent? {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) Intent(this, TvSubSettingActivity::class.java) else null
    }

    @Composable
    override fun SubscriptionListContent() {
        SubSettingScreen(viewModel, ::finish, ::openEditor, ::shareLink)
    }
}

@Composable
private fun SubSettingScreen(
    viewModel: SubscriptionsViewModel,
    onBackClick: () -> Unit,
    openEditor: (String?) -> Unit,
    shareLink: (SubscriptionShareAction, String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var pendingUpdateSubId by remember { mutableStateOf<String?>(null) }
    var removeId by remember { mutableStateOf<String?>(null) }
    var shareId by remember { mutableStateOf<String?>(null) }
    val qrCodeBitmap by viewModel.qrCode.collectAsStateWithLifecycle()
    fun groupById(id: String) = state.groups?.find { it.guid == id }

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
                    IconButton(onClick = { openEditor(null) }) {
                        Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.acc_add_subscription))
                    }
                    IconButton(onClick = {
                        pendingUpdateSubId = null
                        showUpdateDialog = true
                    }) {
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
                items = state.groups.orEmpty(),
                key = { _, item -> item.guid }
            ) { _, subCache ->
                ReorderableItem(reorderableState, key = subCache.guid) { isDragging ->
                    ReorderableListItem(
                        scope = this,
                        isDragging = isDragging
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
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
                                            pendingUpdateSubId = subCache.guid
                                            showUpdateDialog = true
                                        }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_refresh_24dp),
                                                contentDescription = stringResource(R.string.title_sub_update)
                                            )
                                        }
                                        IconButton(onClick = { shareId = subCache.guid }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_share_24dp),
                                                contentDescription = stringResource(R.string.acc_share_subscription)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { openEditor(subCache.guid) }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_edit_24dp),
                                            contentDescription = stringResource(R.string.acc_edit)
                                        )
                                    }
                                    IconButton(onClick = {
                                        if (state.confirmRemove) removeId = subCache.guid
                                        else viewModel.remove(subCache.guid)
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

    shareId?.let(::groupById)?.let { group ->
        SelectListDialog(
            options = SubscriptionShareAction.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { action ->
                shareId = null
                shareLink(action, group.subscription.url)
            },
            onDismiss = { shareId = null }
        )
    }

    // QR Code Dialog
    if (qrCodeBitmap != null) {
        QRCodeDialog(
            bitmap = qrCodeBitmap,
            onDismiss = viewModel::dismissQRCode
        )
    }

    removeId?.let(::groupById)?.let { group ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_subscription_group),
            itemName = group.subscription.remarks,
            onConfirm = {
                removeId = null
                viewModel.remove(group.guid)
            },
            onDismiss = { removeId = null }
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            text = { SubscriptionUpdateOptions(state.updateOptions, viewModel::setOption, PhoneSettingsRows) },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    viewModel.updateSubscriptions(pendingUpdateSubId)
                }) {
                    Text(text = stringResource(R.string.action_ok))
                }
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
        group.usageInfo?.let { usage ->
            val total = if (usage.total > 0L) usage.total.toTrafficString()
                else stringResource(R.string.sub_info_unlimited)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.sub_info_traffic, usage.used.toTrafficString(), total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(Utils.formatTimestamp(group.lastUpdated), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
