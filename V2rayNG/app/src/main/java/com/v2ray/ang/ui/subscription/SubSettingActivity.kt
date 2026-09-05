package com.v2ray.ang.ui.subscription

import android.content.Intent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.MmkvManager.rememberMmkvBool
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.QRCodeDialog
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.reorderAccessibilityActions
import com.v2ray.ang.ui.compose.rememberAccessibilityActionFeedback
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private enum class SubscriptionShareAction(@StringRes val labelRes: Int) {
    QRCode(R.string.share_subscription_qrcode),
    Clipboard(R.string.share_subscription_clipboard)
}

private data class SubscriptionDeleteTarget(
    val guid: String,
    val name: String
)

class SubSettingActivity : BaseComponentActivity() {
    private val viewModel: SubscriptionsViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
        SubSettingScreen(
            viewModel = viewModel,
            isLoading = isLoading,
            onBackClick = { finish() },
            onAddClick = { startActivity(Intent(this, SubEditActivity::class.java)) },
            onSubUpdate = { viewModel.updateSubscriptions() },
            onEditSub = { subId ->
                startActivity(Intent(this, SubEditActivity::class.java).putExtra("subId", subId))
            },
            onRemoveSub = { subId -> removeSub(subId) },
            onShareQRCode = viewModel::shareQRCode,
            onShareClipboard = { url ->
                Utils.setClipboard(this, url)
                toast(getString(R.string.toast_success))
            }
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun removeSub(subId: String) {
        viewModel.remove(subId)
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
    val confirmRemove = MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE, false)

    var shareUrl by remember { mutableStateOf<String?>(null) }
    val qrCodeBitmap by viewModel.qrCode.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val context = LocalContext.current
    val actionFeedback = rememberAccessibilityActionFeedback()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner, context, actionFeedback) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.autoUpdateChanges.collect { enabled ->
                actionFeedback(context.getString(
                    if (enabled) R.string.acc_subscription_auto_update_enabled
                    else R.string.acc_subscription_auto_update_disabled
                ))
            }
        }
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.move(from.index, to.index)
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
                items = subscriptions,
                key = { _, item -> item.guid }
            ) { index, subCache ->
                val subscriptionName = subscriptionAccessibilityName(
                    subCache.subscription.remarks, subCache.subscription.url, stringResource(R.string.acc_unnamed_subscription)
                )
                val requestDelete = {
                    if (confirmRemove) {
                        removeTarget = SubscriptionDeleteTarget(
                            guid = subCache.guid,
                            name = subscriptionName
                        )
                    } else {
                        onRemoveSub(subCache.guid)
                    }
                }
                val itemActions = buildList {
                    add(CustomAccessibilityAction(
                        label = stringResource(R.string.acc_edit_named, subscriptionName),
                        action = { onEditSub(subCache.guid); true },
                    ))
                    add(CustomAccessibilityAction(
                        label = stringResource(R.string.acc_delete_named, subscriptionName),
                        action = { requestDelete(); true },
                    ))
                    if (subCache.subscription.url.isNotEmpty()) {
                        add(CustomAccessibilityAction(
                            label = stringResource(
                                if (subCache.subscription.autoUpdate) R.string.acc_disable_subscription_auto_update
                                else R.string.acc_enable_subscription_auto_update
                            ),
                            action = {
                                viewModel.setAutoUpdate(subCache.guid, !subCache.subscription.autoUpdate)
                            },
                        ))
                        add(CustomAccessibilityAction(
                            label = stringResource(SubscriptionShareAction.QRCode.labelRes),
                            action = {
                                onShareQRCode(subCache.subscription.url)
                                true
                            },
                        ))
                        add(CustomAccessibilityAction(
                            label = stringResource(SubscriptionShareAction.Clipboard.labelRes),
                            action = {
                                onShareClipboard(subCache.subscription.url)
                                true
                            },
                        ))
                    }
                }
                val accessibilityActions = itemActions + reorderAccessibilityActions(
                    currentIndex = index,
                    itemCount = subscriptions.size,
                    onFeedback = actionFeedback,
                    onMove = { command -> viewModel.move(subCache.guid, command) },
                )
                ReorderableItem(reorderableState, key = subCache.guid) { isDragging ->
                    ReorderableListItem(
                        scope = this,
                        isDragging = isDragging
                    ) {
                        SubscriptionRow(
                            subscription = subCache.subscription,
                            subscriptionName = subscriptionName,
                            actions = accessibilityActions,
                            onUpdate = { checked ->
                                viewModel.update(subCache.guid, subCache.subscription.copy(enabled = checked))
                            },
                            onShare = { shareUrl = subCache.subscription.url },
                            onEdit = { onEditSub(subCache.guid) },
                            onDelete = requestDelete,
                        )
                    }
                    ItemDivider()
                }
            }
        }
    }

    val url = shareUrl
    if (url != null) {
        SelectListDialog(
            options = SubscriptionShareAction.entries,
            optionText = { stringResource(it.labelRes) },
            onSelected = { action ->
                shareUrl = null
                when (action) {
                    SubscriptionShareAction.QRCode -> onShareQRCode(url)
                    SubscriptionShareAction.Clipboard -> onShareClipboard(url)
                }
            },
            onDismiss = { shareUrl = null }
        )
    }

    // QR Code Dialog
    if (qrCodeBitmap != null) {
        QRCodeDialog(
            bitmap = qrCodeBitmap,
            onDismiss = viewModel::dismissQRCode
        )
    }

    val deleteTarget = removeTarget
    if (deleteTarget != null) {
        DeleteConfirmDialog(
            message = stringResource(
                R.string.confirm_delete_subscription_group_named,
                deleteTarget.name
            ),
            onConfirm = {
                onRemoveSub(deleteTarget.guid)
                removeTarget = null
            },
            onDismiss = { removeTarget = null }
        )
    }

    if (showUpdateDialog) {

        var updateSubscription by rememberMmkvBool(AppConfig.PREF_UPDATE_SUBSCRIPTION, false)
        var autoTestAfterUpdateSubscription by rememberMmkvBool(AppConfig.PREF_AUTO_TEST_AFTER_UPDATE_SUBSCRIPTION, false)
        var autoRemoveInvalidAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_REMOVE_INVALID_AFTER_TEST, false)
        var autoSortAfterTest by rememberMmkvBool(AppConfig.PREF_AUTO_SORT_AFTER_TEST, false)

        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            text = {
                Column {
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_sub_update),
                        checked = updateSubscription,
                        onCheckedChange = { updateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_test_after_update_subscription),
                        summary = stringResource(R.string.summary_pref_auto_test_after_update_subscription),
                        checked = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoTestAfterUpdateSubscription = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_remove_invalid_after_test),
                        summary = stringResource(R.string.summary_pref_auto_remove_invalid_after_test),
                        checked = autoRemoveInvalidAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoRemoveInvalidAfterTest = it }
                    )
                    SettingsSwitchItem(
                        title = stringResource(R.string.title_pref_auto_sort_after_test),
                        summary = stringResource(R.string.summary_pref_auto_sort_after_test),
                        checked = autoSortAfterTest,
                        enabled = autoTestAfterUpdateSubscription,
                        onCheckedChange = { autoSortAfterTest = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    onSubUpdate()
                }) {
                    Text(text = stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
