package com.v2ray.ang.ui.routing

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.ConfirmDialog
import com.v2ray.ang.ui.compose.SelectListDialog
import com.v2ray.ang.ui.compose.rememberStringOptions

private enum class RoutingPreset(val type: RoutingType, @StringRes val labelRes: Int) {
    ChinaWhitelist(RoutingType.WHITE, R.string.routing_preset_china_whitelist),
    ChinaBlacklist(RoutingType.BLACK, R.string.routing_preset_china_blacklist),
    Global(RoutingType.GLOBAL, R.string.routing_preset_global),
    IranWhitelist(RoutingType.WHITE_IRAN, R.string.routing_preset_iran_whitelist),
    RussiaWhitelist(RoutingType.WHITE_RUSSIA, R.string.routing_preset_russia_whitelist),
}

private enum class RoutingMenuAction(@StringRes val labelRes: Int) {
    ImportPredefined(R.string.routing_settings_import_predefined_rulesets),
    ImportClipboard(R.string.routing_settings_import_rulesets_from_clipboard),
    ImportQRCode(R.string.routing_settings_import_rulesets_from_qrcode),
    ExportClipboard(R.string.routing_settings_export_rulesets_to_clipboard),
}

@Stable
private class RoutingDialogHost {
    var current by mutableStateOf<RoutingDialog?>(null)
        private set
    val show: (RoutingDialog) -> Unit = { current = it }
    val dismiss: () -> Unit = { current = null }
}

@Composable
fun RoutingSettingScreen(
    viewModel: RoutingSettingViewModel,
    onPlatformEvent: (RoutingEvent) -> Boolean,
    modifier: Modifier = Modifier
) {
    val onAction = remember(viewModel) { viewModel::onAction }
    val domainStrategies = rememberStringOptions(R.array.routing_domain_strategy)
    val dialogs = remember { RoutingDialogHost() }

    BackHandler { onAction(RoutingAction.Back) }

    BaseScreen(
        viewModel = viewModel,
        modifier = modifier,
        onEvent = { event ->
            when (event) {
                is RoutingEvent.ShowDialog -> {
                    dialogs.show(event.dialog)
                    true
                }
                is RoutingEvent -> onPlatformEvent(event)
                else -> false
            }
        },
        onResult = { result -> onAction(RoutingAction.ResultReceived(result)) },
        topBar = {
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            RoutingSettingTopBar(isLoading = isLoading, onAction = onAction)
        }
    ) { state, _ ->
        RoutingRuleList(
            rules = state.rules,
            domainStrategies = domainStrategies,
            selectedStrategy = state.domainStrategy.ifEmpty { domainStrategies.firstOrNull().orEmpty() },
            onAction = onAction,
        )
        RoutingDialogs(dialog = dialogs.current, onAction = onAction, onDismiss = dialogs.dismiss)
    }
}

@Composable
private fun RoutingSettingTopBar(
    isLoading: Boolean,
    onAction: (RoutingAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }

    AppTopBar(
        title = stringResource(R.string.routing_settings_title),
        onBackClick = { onAction(RoutingAction.Back) },
        modifier = modifier,
        isLoading = isLoading,
        actions = {
            IconButton(onClick = { onAction(RoutingAction.AddRule) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_24dp),
                    contentDescription = stringResource(R.string.acc_add_rule),
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert_24dp),
                        contentDescription = stringResource(R.string.acc_more),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    RoutingMenuAction.entries.forEach { menuAction ->
                        DropdownMenuItem(
                            text = { Text(stringResource(menuAction.labelRes)) },
                            onClick = {
                                showMenu = false
                                onAction(
                                    when (menuAction) {
                                        RoutingMenuAction.ImportPredefined -> RoutingAction.PresetClicked
                                        RoutingMenuAction.ImportClipboard -> RoutingAction.ImportFromClipboard
                                        RoutingMenuAction.ImportQRCode -> RoutingAction.ImportFromQrCode
                                        RoutingMenuAction.ExportClipboard -> RoutingAction.ExportToClipboard
                                    }
                                )
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun RoutingDialogs(
    dialog: RoutingDialog?,
    onAction: (RoutingAction) -> Unit,
    onDismiss: () -> Unit
) {
    when (dialog) {
        RoutingDialog.Presets -> SelectListDialog(
            title = stringResource(R.string.routing_settings_import_predefined_rulesets),
            options = RoutingPreset.entries,
            optionText = { preset -> stringResource(preset.labelRes) },
            onSelected = { preset -> onAction(RoutingAction.PresetSelected(preset.type)) },
            onDismiss = onDismiss,
        )
        is RoutingDialog.ConfirmImport -> ConfirmDialog(
            message = stringResource(R.string.routing_settings_import_rulesets_tip),
            onConfirm = { onAction(RoutingAction.ConfirmImport(dialog.pending)) },
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}
