package com.v2ray.ang.ui.userasset

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.base.LocalPlatformActions
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.StringOptions
import com.v2ray.ang.ui.compose.verticalScrollbar

private val RowPad = 8.dp
private val HeaderPad = 16.dp
private val TitleGap = 4.dp
private val IconSize = 24.dp

private const val KEY_GEO_SOURCE = "geo_source"
private const val KEY_ASSET_TITLE = "asset_title"
private const val CONTENT_TYPE_GEO_SOURCE = "geo_source"
private const val CONTENT_TYPE_HEADER = "header"
private const val CONTENT_TYPE_ASSET = "asset"

@Composable
fun UserAssetScreen(viewModel: UserAssetViewModel) {
    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack = remember(onAction) { { onAction(UserAssetAction.Back) } }
    val platform = LocalPlatformActions.current
    val onEvent = remember(platform, onAction) {
        { event: BaseEvent ->
            when (event) {
                UserAssetEvent.PickFile -> {
                    platform.pickFile { uri -> onAction(UserAssetAction.FileSelected(uri)) }
                    true
                }

                UserAssetEvent.ScanQrCode -> {
                    platform.scanQrCode { text -> onAction(UserAssetAction.QrCodeScanned(text)) }
                    true
                }

                else -> false
            }
        }
    }

    BackHandler(onBack = onBack)

    BaseScreen(
        viewModel = viewModel,
        onEvent = onEvent,
        onResult = { result -> onAction(UserAssetAction.ResultReceived(result)) },
        topBar = {
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            val progress by viewModel.downloadProgress.collectAsStateWithLifecycle()
            UserAssetTopBar(
                isLoading = isLoading,
                progress = progress?.fraction,
                onAction = onAction
            )
        }
    ) { state, _ ->
        UserAssetList(
            assets = state.assets,
            geoSources = state.geoSources,
            geoSource = state.geoSource,
            onAction = onAction
        )
        UserAssetDialogs(dialog = state.dialog, onAction = onAction)
    }
}

@Composable
private fun UserAssetTopBar(
    isLoading: Boolean,
    progress: Float?,
    onAction: (UserAssetAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddMenu by remember { mutableStateOf(false) }

    AppTopBar(
        title = stringResource(R.string.title_user_asset_setting),
        onBackClick = { onAction(UserAssetAction.Back) },
        isLoading = isLoading,
        progress = progress,
        modifier = modifier,
        actions = {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { showAddMenu = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add_24dp),
                        contentDescription = stringResource(R.string.acc_add_asset)
                    )
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false },
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.wrapContentWidth(Alignment.End)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_item_add_file)) },
                        onClick = {
                            showAddMenu = false
                            onAction(UserAssetAction.AddFileClicked)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_item_add_url)) },
                        onClick = {
                            showAddMenu = false
                            onAction(UserAssetAction.AddUrlClicked)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_item_scan_qrcode)) },
                        onClick = {
                            showAddMenu = false
                            onAction(UserAssetAction.ScanQrCodeClicked)
                        }
                    )
                }
            }
            IconButton(onClick = { onAction(UserAssetAction.DownloadClicked) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_cloud_download_24dp),
                    contentDescription = stringResource(R.string.acc_download_file)
                )
            }
        }
    )
}

@Composable
private fun UserAssetList(
    assets: List<AssetRow>,
    geoSources: List<String>,
    geoSource: String,
    onAction: (UserAssetAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val geoOptions = remember(geoSources) { StringOptions(geoSources) }
    val onGeoSelected = remember(onAction) {
        { value: String -> onAction(UserAssetAction.GeoSourceSelected(value)) }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .verticalScrollbar(listState),
        contentPadding = NavigationBarsBottomPadding()
    ) {
        item(key = KEY_GEO_SOURCE, contentType = CONTENT_TYPE_GEO_SOURCE) {
            SettingsListItem(
                title = stringResource(R.string.asset_geo_files_sources),
                entries = geoOptions,
                values = geoOptions,
                selectedValue = geoSource,
                onSelected = onGeoSelected
            )
        }
        item(key = KEY_ASSET_TITLE, contentType = CONTENT_TYPE_HEADER) {
            Text(
                text = stringResource(R.string.title_user_asset_setting),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(HeaderPad)
            )
        }
        items(
            items = assets,
            key = { assetRow -> assetRow.guid },
            contentType = { CONTENT_TYPE_ASSET }
        ) { assetRow ->
            UserAssetRow(assetRow = assetRow, onAction = onAction)
            ItemDivider()
        }
    }
}

@Composable
private fun UserAssetRow(
    assetRow: AssetRow,
    onAction: (UserAssetAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val guid = assetRow.guid

    // Remembered per row: rebuilding these on every list emission would change each row's
    // parameters even when its data did not, and nothing could be skipped.
    val onEdit = remember(guid, onAction) { { onAction(UserAssetAction.Edit(guid)) } }
    val onRemove = remember(guid, onAction) { { onAction(UserAssetAction.RemoveClicked(guid)) } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(RowPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(RowPad)
        ) {
            Text(
                text = assetRow.remarks,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(TitleGap))
            // Formatting already done by the repository; the Composable only renders it.
            Text(
                text = assetRow.properties,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (assetRow.editable) {
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_24dp),
                    contentDescription = stringResource(R.string.acc_edit),
                    modifier = Modifier.size(IconSize)
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_24dp),
                contentDescription = stringResource(R.string.acc_delete),
                modifier = Modifier.size(IconSize)
            )
        }
    }
}

@Composable
private fun UserAssetDialogs(
    dialog: UserAssetDialog?,
    onAction: (UserAssetAction) -> Unit
) {
    when (dialog) {
        is UserAssetDialog.ConfirmRemove -> DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_asset_file, dialog.remarks),
            onConfirm = { onAction(UserAssetAction.DialogConfirm) },
            onDismiss = { onAction(UserAssetAction.DialogDismiss) }
        )

        null -> Unit
    }
}

// ===== previews =====

private val previewAssets = listOf(
    AssetRow("builtin:geosite.dat", "geosite.dat", "1.2 MB    2025-08-12 10:00", editable = false),
    AssetRow("builtin:geoip.dat", "geoip.dat", "file not found", editable = false),
    AssetRow(
        "a1",
        "a very long custom asset remark that has to be truncated politely",
        "42 B    2025-08-12 10:00",
        editable = true
    )
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UserAssetListPreview() = AppTheme {
    UserAssetList(
        assets = previewAssets,
        geoSources = listOf("Loyalsoldier", "v2fly"),
        geoSource = "Loyalsoldier",
        onAction = {}
    )
}
