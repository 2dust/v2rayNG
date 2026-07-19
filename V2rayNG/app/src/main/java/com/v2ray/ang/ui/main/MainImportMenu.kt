package com.v2ray.ang.ui.main

import android.graphics.Bitmap
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.R
import com.v2ray.ang.compose.QRCodeDialog
import com.v2ray.ang.compose.SelectListDialog
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType

@Composable
fun ImportMenuContent(
    onImportQRcode: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportLocal: () -> Unit,
    onImportManually: (Int) -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_qrcode)) },
        onClick = onImportQRcode
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_clipboard)) },
        onClick = onImportClipboard
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_local)) },
        onClick = onImportLocal
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_policy_group)) },
        onClick = { onImportManually(EConfigType.POLICYGROUP.value) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_proxy_chain)) },
        onClick = { onImportManually(EConfigType.PROXYCHAIN.value) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_vmess)) },
        onClick = { onImportManually(EConfigType.VMESS.value) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_vless)) },
        onClick = { onImportManually(EConfigType.VLESS.value) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_ss)) },
        onClick = { onImportManually(EConfigType.SHADOWSOCKS.value) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_socks)) },
        onClick = { onImportManually(EConfigType.SOCKS.value) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_http)) },
        onClick = { onImportManually(EConfigType.HTTP.value) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_trojan)) },
        onClick = { onImportManually(EConfigType.TROJAN.value) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_wireguard)) },
        onClick = { onImportManually(EConfigType.WIREGUARD.value) }
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.menu_item_import_config_manually_hysteria2)) },
        onClick = { onImportManually(EConfigType.HYSTERIA2.value) }
    )
}

@Composable
fun MoreMenuContent(
    onRestartService: () -> Unit,
    onDelAllConfig: () -> Unit,
    onDelDuplicateConfig: () -> Unit,
    onDelInvalidConfig: () -> Unit,
    onExportAll: () -> Unit,
    onRealPingAll: () -> Unit,
    onLocateSelectedServer: () -> Unit,
    onSortByTestResults: () -> Unit,
    onSubUpdate: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_service_restart)) },
        onClick = onRestartService
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
        onClick = onExportAll
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_real_ping_all_server)) },
        onClick = onRealPingAll
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_locate_selected_config)) },
        onClick = onLocateSelectedServer
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_sort_by_test_results)) },
        onClick = onSortByTestResults
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.title_sub_update)) },
        onClick = onSubUpdate
    )
}

@Composable
fun ShareMethodDialog(
    guid: String,
    profile: ProfileItem,
    more: Boolean,
    shareMethodEntries: List<String>,
    shareMethodMoreEntries: List<String>,
    onDismiss: () -> Unit,
    onShareQRCode: (String) -> Bitmap?,
    onShareClipboard: (String) -> Boolean,
    onShareFullContent: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    showQRCodeBitmap: (Bitmap?) -> Unit
) {
    val isCustom = profile.configType.isComplexType()
    val (shareOptions, skip) = if (more) {
        val options = if (isCustom) shareMethodMoreEntries.takeLast(3) else shareMethodMoreEntries
        options to if (isCustom) 2 else 0
    } else {
        val options = if (isCustom) shareMethodEntries.takeLast(1) else shareMethodEntries
        options to if (isCustom) 2 else 0
    }
    SelectListDialog(
        options = shareOptions,
        onSelected = { index, _ ->
            onDismiss()
            when (index + skip) {
                0 -> showQRCodeBitmap(onShareQRCode(guid))
                1 -> onShareClipboard(guid)
                2 -> onShareFullContent(guid)
                3 -> onEditServer(guid, profile)
                4 -> onRemoveServer(guid)
            }
        },
        onDismiss = onDismiss
    )
}
