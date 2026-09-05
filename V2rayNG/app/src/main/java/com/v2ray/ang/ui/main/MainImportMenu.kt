package com.v2ray.ang.ui.main

import androidx.annotation.StringRes
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.ui.compose.AppDropdownMenuItems
import com.v2ray.ang.ui.compose.SelectListDialog

private enum class ImportMenuItem(@StringRes val labelRes: Int, val action: MainAction) {
    QrCode(R.string.menu_item_import_config_qrcode, MainAction.ImportFromQrCode),
    Clipboard(R.string.menu_item_import_config_clipboard, MainAction.ImportFromClipboard),
    LocalFile(R.string.menu_item_import_config_local, MainAction.ImportFromFile),
    PolicyGroup(R.string.menu_item_import_config_policy_group, MainAction.AddServer(EConfigType.POLICYGROUP)),
    ProxyChain(R.string.menu_item_import_config_proxy_chain, MainAction.AddServer(EConfigType.PROXYCHAIN)),
    Vmess(R.string.menu_item_import_config_manually_vmess, MainAction.AddServer(EConfigType.VMESS)),
    Vless(R.string.menu_item_import_config_manually_vless, MainAction.AddServer(EConfigType.VLESS)),
    Shadowsocks(R.string.menu_item_import_config_manually_ss, MainAction.AddServer(EConfigType.SHADOWSOCKS)),
    Socks(R.string.menu_item_import_config_manually_socks, MainAction.AddServer(EConfigType.SOCKS)),
    Http(R.string.menu_item_import_config_manually_http, MainAction.AddServer(EConfigType.HTTP)),
    Trojan(R.string.menu_item_import_config_manually_trojan, MainAction.AddServer(EConfigType.TROJAN)),
    WireGuard(R.string.menu_item_import_config_manually_wireguard, MainAction.AddServer(EConfigType.WIREGUARD)),
    Hysteria2(R.string.menu_item_import_config_manually_hysteria2, MainAction.AddServer(EConfigType.HYSTERIA2)),
}

private enum class MoreMenuItem(@StringRes val labelRes: Int) {
    RestartService(R.string.title_service_restart),
    DeleteAll(R.string.title_del_all_config),
    DeleteDuplicate(R.string.title_del_duplicate_config),
    DeleteInvalid(R.string.title_del_invalid_config),
    ExportAll(R.string.title_export_all),
    LocateSelected(R.string.title_locate_selected_config),
    SortByTestResults(R.string.title_sort_by_test_results),
    TestAll(R.string.title_ping_all_server),
    TestAllRealPing(R.string.title_real_ping_all_server),
    UpdateSubscriptions(R.string.title_sub_update),
}

private enum class ServerMenuItem(
    @StringRes val labelRes: Int,
    val isShareAction: Boolean,
    val supportsComplexProfiles: Boolean,
) {
    ShareQrCode(R.string.share_method_qrcode, true, false),
    ShareClipboard(R.string.share_method_clipboard, true, false),
    ShareFullContent(R.string.share_method_full_content, true, true),
    Edit(R.string.action_edit, false, true),
    Delete(R.string.action_delete, false, true),
}

@Composable
fun ImportMenuContent(onAction: (MainAction) -> Unit) = AppDropdownMenuItems(
    items = ImportMenuItem.entries,
    labelRes = { it.labelRes },
    onSelected = { onAction(it.action) },
)

@Composable
fun MoreMenuContent(
    onAction: (MainAction) -> Unit,
    onShowDialog: (MainDialog) -> Unit,
) = MoreMenuItem.entries.forEach { item ->
    DropdownMenuItem(
        text = { Text(stringResource(item.labelRes)) },
        onClick = {
            when (item) {
                MoreMenuItem.DeleteAll -> onShowDialog(MainDialog.DeleteAll)
                MoreMenuItem.DeleteDuplicate -> onShowDialog(MainDialog.DeleteDuplicate)
                MoreMenuItem.DeleteInvalid -> onShowDialog(MainDialog.DeleteInvalid)
                MoreMenuItem.RestartService -> onAction(MainAction.RestartService)
                MoreMenuItem.ExportAll -> onAction(MainAction.ExportAll)
                MoreMenuItem.LocateSelected -> onAction(MainAction.LocateSelectedServer)
                MoreMenuItem.SortByTestResults -> onAction(MainAction.SortByTestResults)
                MoreMenuItem.TestAll -> onAction(MainAction.TestAllServers)
                MoreMenuItem.TestAllRealPing -> onAction(MainAction.TestRealAllServers)
                MoreMenuItem.UpdateSubscriptions -> onAction(MainAction.UpdateSubscriptions)
            }
        },
    )
}

@Composable
fun ShareMethodDialog(
    guid: String,
    configType: EConfigType,
    includeManagement: Boolean,
    onDismiss: () -> Unit,
    onAction: (MainAction) -> Unit,
    onRequestRemove: (String) -> Unit,
) {
    val isComplex = configType.isComplexType()
    val items = ServerMenuItem.entries.filter { item ->
        (includeManagement || item.isShareAction) && (!isComplex || item.supportsComplexProfiles)
    }
    SelectListDialog(
        options = items,
        optionText = { stringResource(it.labelRes) },
        onSelected = { item ->
            onDismiss()
            when (item) {
                ServerMenuItem.ShareQrCode -> onAction(MainAction.ShareQrCode(guid))
                ServerMenuItem.ShareClipboard -> onAction(MainAction.ShareClipboard(guid))
                ServerMenuItem.ShareFullContent -> onAction(MainAction.ShareFullContent(guid))
                ServerMenuItem.Edit -> onAction(MainAction.EditServer(guid, configType))
                ServerMenuItem.Delete -> onRequestRemove(guid)
            }
        },
        onDismiss = onDismiss,
    )
}
