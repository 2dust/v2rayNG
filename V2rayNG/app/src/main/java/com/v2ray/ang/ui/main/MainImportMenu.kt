package com.v2ray.ang.ui.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.SelectListDialog

private enum class ImportMenuAction(@StringRes val labelRes: Int, val action: MainAction) {
    QRCode(R.string.menu_item_import_config_qrcode, MainAction.ImportQRcode),
    Clipboard(R.string.menu_item_import_config_clipboard, MainAction.ImportClipboard),
    LocalFile(R.string.menu_item_import_config_local, MainAction.ImportConfigLocal),
    PolicyGroup(R.string.menu_item_import_config_policy_group, MainAction.ImportManually(EConfigType.POLICYGROUP.value)),
    ProxyChain(R.string.menu_item_import_config_proxy_chain, MainAction.ImportManually(EConfigType.PROXYCHAIN.value)),
    Vmess(R.string.menu_item_import_config_manually_vmess, MainAction.ImportManually(EConfigType.VMESS.value)),
    Vless(R.string.menu_item_import_config_manually_vless, MainAction.ImportManually(EConfigType.VLESS.value)),
    Shadowsocks(R.string.menu_item_import_config_manually_ss, MainAction.ImportManually(EConfigType.SHADOWSOCKS.value)),
    Socks(R.string.menu_item_import_config_manually_socks, MainAction.ImportManually(EConfigType.SOCKS.value)),
    Http(R.string.menu_item_import_config_manually_http, MainAction.ImportManually(EConfigType.HTTP.value)),
    Trojan(R.string.menu_item_import_config_manually_trojan, MainAction.ImportManually(EConfigType.TROJAN.value)),
    WireGuard(R.string.menu_item_import_config_manually_wireguard, MainAction.ImportManually(EConfigType.WIREGUARD.value)),
    Hysteria2(R.string.menu_item_import_config_manually_hysteria2, MainAction.ImportManually(EConfigType.HYSTERIA2.value)),
    Aether(R.string.menu_item_import_config_manually_aether, MainAction.ImportManually(EConfigType.AETHER.value))
}

enum class MainMoreMenuAction(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    RestartService(R.string.title_service_restart, R.drawable.ic_restore_24dp),
    DeleteAll(R.string.title_del_all_config, R.drawable.ic_delete_24dp),
    DeleteDuplicate(R.string.title_del_duplicate_config, R.drawable.ic_select_all_24dp),
    DeleteInvalid(R.string.title_del_invalid_config, R.drawable.ic_flash_off_24dp),
    ExportAll(R.string.title_export_all, R.drawable.ic_share_24dp),
    LocateSelected(R.string.title_locate_selected_config, R.drawable.ic_search_24dp),
    SortByTestResults(R.string.title_sort_by_test_results, R.drawable.ic_outline_filter_alt_24),
    TestAll(R.string.title_ping_all_server, R.drawable.ic_flash_on_24dp),
    TestAllRealPing(R.string.title_real_ping_all_server, R.drawable.ic_speed_24dp),
    UpdateSubscriptions(R.string.title_sub_update, R.drawable.ic_subscriptions_24dp)
}

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
fun ImportMenuContent(onAction: (MainAction) -> Unit) = AppDropdownMenuItems(
    items = ImportMenuAction.entries,
    labelRes = { it.labelRes },
    onSelected = { onAction(it.action) }
)

/** The three-dot menu: every entry has an icon in front of its label, and the destructive ones are tinted red. */
@Composable
fun MoreMenuContent(onSelected: (MainMoreMenuAction) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    MainMoreMenuAction.entries.forEach { action ->
        val destructive = action == MainMoreMenuAction.DeleteAll ||
            action == MainMoreMenuAction.DeleteDuplicate ||
            action == MainMoreMenuAction.DeleteInvalid
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(action.labelRes),
                    color = if (destructive) scheme.error else scheme.onSurface,
                )
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(action.iconRes),
                    contentDescription = null,
                    tint = if (destructive) scheme.error else scheme.secondary,
                )
            },
            onClick = { onSelected(action) },
        )
    }
}

@Composable
fun ShareMethodDialog(
    guid: String,
    profile: ProfileItem,
    more: Boolean,
    onDismiss: () -> Unit,
    onAction: (MainAction) -> Unit,
    onRemove: (String, String) -> Unit,
) {
    val menuActions = serverMenuActions(
        isComplexProfile = profile.configType.isComplexType(),
        includeManagementActions = more,
    )
    SelectListDialog(
        options = menuActions,
        optionText = { stringResource(it.labelRes) },
        onSelected = { action ->
            onDismiss()
            when (action) {
                ServerMenuAction.ShareQRCode -> onAction(MainAction.ShareQRCode(guid))
                ServerMenuAction.ShareClipboard -> onAction(MainAction.ShareClipboard(guid))
                ServerMenuAction.ShareFullContent -> onAction(MainAction.ShareFullContent(guid))
                ServerMenuAction.Edit -> onAction(MainAction.EditServer(guid, profile))
                ServerMenuAction.Delete -> onRemove(guid, profile.remarks)
            }
        },
        onDismiss = onDismiss
    )
}
