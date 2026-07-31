package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.ui.compose.SelectListDialog

internal enum class ServerMenuAction(
    @StringRes val labelRes: Int,
    val isShareAction: Boolean,
    val supportsComplexProfiles: Boolean,
) {
    ShareQRCode(R.string.share_method_qrcode, isShareAction = true, supportsComplexProfiles = false),
    ShareClipboard(R.string.share_method_clipboard, isShareAction = true, supportsComplexProfiles = false),
    ShareFullContent(R.string.share_method_full_content, isShareAction = true, supportsComplexProfiles = true),
    Edit(R.string.action_edit, isShareAction = false, supportsComplexProfiles = true),
    Delete(R.string.action_delete, isShareAction = false, supportsComplexProfiles = true),
}

internal fun serverMenuActions(
    isComplexProfile: Boolean,
    includeManagementActions: Boolean,
): List<ServerMenuAction> = ServerMenuAction.entries.filter { action ->
    (includeManagementActions || action.isShareAction) && (!isComplexProfile || action.supportsComplexProfiles)
}

@Composable
fun ImportMenuContent(
    onAction: (MainAction) -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_qrcode)) },
        onClick = { onAction(MainAction.ImportQRcode) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_clipboard)) },
        onClick = { onAction(MainAction.ImportClipboard) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_local)) },
        onClick = { onAction(MainAction.ImportConfigLocal) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_policy_group)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.POLICYGROUP.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_proxy_chain)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.PROXYCHAIN.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_vmess)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.VMESS.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_vless)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.VLESS.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_ss)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.SHADOWSOCKS.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_socks)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.SOCKS.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_http)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.HTTP.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_trojan)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.TROJAN.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_wireguard)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.WIREGUARD.value)) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_hysteria2)) },
        onClick = { onAction(MainAction.ImportManually(EConfigType.HYSTERIA2.value)) }
    )
}

@Composable
fun MoreMenuContent(
    onAction: (MainAction) -> Unit,
    onDelAllConfig: () -> Unit,
    onDelDuplicateConfig: () -> Unit,
    onDelInvalidConfig: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_service_restart)) },
        onClick = { onAction(MainAction.RestartService) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_del_all_config)) },
        onClick = onDelAllConfig
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_del_duplicate_config)) },
        onClick = onDelDuplicateConfig
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_del_invalid_config)) },
        onClick = onDelInvalidConfig
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_export_all)) },
        onClick = { onAction(MainAction.ExportAll) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_locate_selected_config)) },
        onClick = { onAction(MainAction.LocateSelectedServer) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_sort_by_test_results)) },
        onClick = { onAction(MainAction.SortByTestResults) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_ping_all_server)) },
        onClick = { onAction(MainAction.TestAllServers) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_real_ping_all_server)) },
        onClick = { onAction(MainAction.TestRealAllServers) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_sub_update)) },
        onClick = { onAction(MainAction.UpdateSubscriptions) }
    )
}

@Composable
fun ShareMethodDialog(
    guid: String,
    profile: ProfileItem,
    more: Boolean,
    onDismiss: () -> Unit,
    onAction: (MainAction) -> Unit,
    onRemove: (String) -> Unit,
) {
    val menuActions = serverMenuActions(
        isComplexProfile = profile.configType.isComplexType(),
        includeManagementActions = more,
    )
    SelectListDialog(
        options = menuActions.map { stringResource(it.labelRes) },
        onSelected = { index, _ ->
            onDismiss()
            when (menuActions[index]) {
                ServerMenuAction.ShareQRCode -> onAction(MainAction.ShareQRCode(guid))
                ServerMenuAction.ShareClipboard -> onAction(MainAction.ShareClipboard(guid))
                ServerMenuAction.ShareFullContent -> onAction(MainAction.ShareFullContent(guid))
                ServerMenuAction.Edit -> onAction(MainAction.EditServer(guid, profile))
                ServerMenuAction.Delete -> onRemove(guid)
            }
        },
        onDismiss = onDismiss
    )
}
