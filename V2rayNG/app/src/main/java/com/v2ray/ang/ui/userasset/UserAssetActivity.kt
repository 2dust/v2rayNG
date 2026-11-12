package com.v2ray.ang.ui.userasset

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.text.format.Formatter
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.dpadMovePreviousNavigation
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.AppIconButton
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.AppSelectionMenuAction
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.NavigationBarsBottomPadding
import com.v2ray.ang.ui.compose.SettingsListItem
import com.v2ray.ang.ui.compose.dpadRowActionNavigation
import com.v2ray.ang.ui.compose.dpadVerticalFocusNavigation
import com.v2ray.ang.ui.compose.isTelevisionDevice
import com.v2ray.ang.ui.compose.rememberLazyDpadFocus
import com.v2ray.ang.ui.compose.rememberDpadFocusTargets
import com.v2ray.ang.ui.compose.rememberDpadFocusRequester
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

private enum class AddAssetMenuAction(@StringRes val labelRes: Int) {
    File(R.string.menu_item_add_file),
    Url(R.string.menu_item_add_url),
    QRCode(R.string.menu_item_scan_qrcode)
}

private data class AssetDeleteTarget(val guid: String, val name: String)

private data class UserAssetRowFocusTargets(val edit: FocusRequester = FocusRequester(), val delete: FocusRequester = FocusRequester())

private fun AssetUrlCache.canEdit() = assetUrl.locked != true && assetUrl.url != "file"

class UserAssetActivity : HelperBaseComponentActivity() {

    private val viewModel: UserAssetViewModel by viewModels()
    private val extDir by lazy { File(Utils.userAssetPath(this)) }
    private val isLoadingState = MutableStateFlow(false)
    private val geoFilesSourceState = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geoFilesSourceState.value = getGeoFilesSources()
    }

    @Composable
    override fun ScreenContent() {
        val isLoading by isLoadingState.collectAsStateWithLifecycle()
        val geoFilesSource by geoFilesSourceState.collectAsStateWithLifecycle()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        UserAssetScreen(
            uiState = uiState,
            isLoading = isLoading,
            geoFilesSource = geoFilesSource,
            geoFilesSourcesList = AppConfig.GEO_FILES_SOURCES.toList(),
            onBackClick = { finish() },
            onGeoSourceSelected = { value ->
                MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, value)
                geoFilesSourceState.value = value
                refreshData()
            },
            onAddFileClick = { showFileChooser() },
            onAddUrlClick = { startActivity(Intent(this, UserAssetUrlActivity::class.java)) },
            onAddQrcodeClick = { importAssetFromQRcode() },
            onDownloadClick = { downloadGeoFiles() },
            onEditAsset = { guid ->
                startActivity(Intent(this, UserAssetUrlActivity::class.java).putExtra("assetId", guid))
            },
            onRemoveAsset = { guid, name ->
                extDir.listFiles()?.find { it.name == name }?.delete()
                MmkvManager.removeAssetUrl(guid)
                initAssets()
            }
        )
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun getGeoFilesSources(): String {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) ?: AppConfig.GEO_FILES_SOURCES.first()
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) {
                return@launchFileChooser
            }

            val assetId = Utils.getUuid()
            runCatching {
                val assetItem = AssetUrlItem(
                    getCursorName(uri) ?: uri.toString(),
                    "file"
                )

                val assetList = MmkvManager.decodeAssetUrls()
                if (assetList.any { it.assetUrl.remarks == assetItem.remarks && it.guid != assetId }) {
                    toast(R.string.msg_remark_is_duplicate)
                } else {
                    MmkvManager.encodeAsset(assetId, assetItem)
                    copyFile(uri)
                }
            }.onFailure {
                toastError(R.string.toast_asset_copy_failed)
                MmkvManager.removeAssetUrl(assetId)
            }
        }
    }

    private fun copyFile(uri: Uri): String {
        val targetFile = File(extDir, getCursorName(uri) ?: uri.toString())
        contentResolver.openInputStream(uri).use { inputStream ->
            targetFile.outputStream().use { fileOut ->
                inputStream?.copyTo(fileOut)
                toastSuccess(R.string.toast_success)
                refreshData()
            }
        }
        return targetFile.path
    }

    private fun getCursorName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.let { cursor ->
            cursor.run {
                if (moveToFirst()) getString(getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else null
            }.also { cursor.close() }
        }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to get cursor name", e)
        null
    }

    private fun importAssetFromQRcode(): Boolean {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null) {
                importAsset(scanResult)
            }
        }
        return true
    }

    private fun importAsset(url: String?): Boolean {
        try {
            if (!Utils.isValidUrl(url)) {
                toast(R.string.toast_invalid_url)
                return false
            }
            startActivity(
                Intent(this, UserAssetUrlActivity::class.java)
                    .putExtra(UserAssetUrlActivity.ASSET_URL_QRCODE, url)
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to import asset from URL", e)
            return false
        }
        return true
    }

    private fun downloadGeoFiles() {
        isLoadingState.value = true
        toast(R.string.msg_downloading_content)

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        lifecycleScope.launch {
            refreshData().join()
            val result = withContext(Dispatchers.IO) {
                viewModel.downloadGeoFiles(extDir, httpPort, proxyUsername, proxyPassword)
            }
            if (result.successCount > 0) {
                toast(
                    resources.getQuantityString(
                        R.plurals.title_update_asset_count,
                        result.successCount,
                        result.successCount,
                    )
                )
            } else {
                toast(getString(R.string.toast_failure))
            }
            refreshData().join()
            isLoadingState.value = false
        }
    }

    private fun initAssets() {
        lifecycleScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(this@UserAssetActivity, assets)
            withContext(Dispatchers.Main) {
                refreshData()
            }
        }
    }

    private fun refreshData() = viewModel.reload(getGeoFilesSources(), extDir)
}

@Composable
internal fun UserAssetScreen(
    uiState: UserAssetUiState,
    isLoading: Boolean,
    geoFilesSource: String,
    geoFilesSourcesList: List<String>,
    onBackClick: () -> Unit,
    onGeoSourceSelected: (String) -> Unit,
    onAddFileClick: () -> Unit,
    onAddUrlClick: () -> Unit,
    onAddQrcodeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onEditAsset: (String) -> Unit,
    onRemoveAsset: (String, String) -> Unit
) {
    val assets = uiState.assets
    val fileMetadata = uiState.fileMetadata
    var deleteTarget by remember { mutableStateOf<AssetDeleteTarget?>(null) }
    val listState = rememberLazyListState()
    val isTelevision = isTelevisionDevice()
    val backFocusRequester = rememberDpadFocusRequester()
    val geoSourceFocusRequester = remember { FocusRequester() }
    val assetIds = assets.map { it.guid }
    val storedFocusTargets = rememberDpadFocusTargets(assetIds) { UserAssetRowFocusTargets() }
    val rowFocusTargets = assets.associate { asset ->
        val targets = storedFocusTargets.getValue(asset.guid)
        // A read-only asset has no Edit node; its corresponding action is Delete.
        asset.guid to if (asset.canEdit()) targets else targets.copy(edit = targets.delete)
    }
    val requestRowFocus = rememberLazyDpadFocus(
        listOf(listOf(geoSourceFocusRequester)) + assets.map {
            val targets = rowFocusTargets.getValue(it.guid)
            listOfNotNull(targets.edit.takeIf { _ -> it.canEdit() }, targets.delete)
        }
    ) { listState.scrollToItem(it) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_user_asset_setting),
                onBackClick = onBackClick,
                isLoading = isLoading,
                navigationFocusRequester = backFocusRequester,
                onMoveDown = { requestRowFocus(geoSourceFocusRequester) },
                actionItems = listOf(
                    AppTopBarAction(
                        painterResource(R.drawable.ic_add_24dp), stringResource(R.string.menu_item_add_asset),
                        contentDescription = stringResource(R.string.acc_add_asset),
                        menuActions = AddAssetMenuAction.entries.map { action ->
                            AppSelectionMenuAction(stringResource(action.labelRes)) {
                                when (action) {
                                    AddAssetMenuAction.File -> onAddFileClick()
                                    AddAssetMenuAction.Url -> onAddUrlClick()
                                    AddAssetMenuAction.QRCode -> onAddQrcodeClick()
                                }
                            }
                        }
                    ),
                    AppTopBarAction(painterResource(R.drawable.ic_cloud_download_24dp),
                        stringResource(R.string.menu_item_download_file), onDownloadClick,
                        contentDescription = stringResource(R.string.acc_download_file))
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .tvSafeAreaPadding()
                .dpadMovePreviousNavigation { backFocusRequester.requestFocus() }
                .verticalScrollbar(listState),
            contentPadding = NavigationBarsBottomPadding()
        ) {
            item(key = "geo_source") {
                SettingsListItem(
                    title = stringResource(R.string.asset_geo_files_sources),
                    entries = geoFilesSourcesList,
                    values = geoFilesSourcesList,
                    selectedValue = geoFilesSource,
                    onSelected = { onGeoSourceSelected(it) },
                    focusRequester = geoSourceFocusRequester,
                    modifier = Modifier.dpadVerticalFocusNavigation(
                        onMoveUp = { false },
                        onMoveDown = {
                            assets.firstOrNull()?.let { first ->
                                val targets = rowFocusTargets[first.guid] ?: return@let false
                                requestRowFocus(if (first.canEdit()) targets.edit else targets.delete)
                            } ?: true
                        }
                    )
                )
            }
            itemsIndexed(items = assets, key = { _, item -> item.guid }) { index, item ->
                UserAssetItem(
                    item = item,
                    fileMetadata = fileMetadata[item.guid],
                    focusTargets = rowFocusTargets.getValue(item.guid),
                    requestRowFocus = requestRowFocus,
                    previousFocusTargets = assets.getOrNull(index - 1)?.let { rowFocusTargets[it.guid] },
                    nextFocusTargets = assets.getOrNull(index + 1)?.let { rowFocusTargets[it.guid] },
                    firstRowPreviousFocusRequester = geoSourceFocusRequester.takeIf { index == 0 },
                    onEdit = { onEditAsset(item.guid) },
                    onDeleteClick = {
                        deleteTarget = AssetDeleteTarget(item.guid, item.assetUrl.remarks)
                    }
                )
                ItemDivider()
            }
        }
    }


    deleteTarget?.let { asset ->
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_asset_file),
            itemName = asset.name,
            onConfirm = {
                deleteTarget = null
                onRemoveAsset(asset.guid, asset.name)
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun UserAssetItem(
    item: AssetUrlCache,
    fileMetadata: AssetFileMetadata?,
    focusTargets: UserAssetRowFocusTargets,
    requestRowFocus: (FocusRequester) -> Boolean,
    previousFocusTargets: UserAssetRowFocusTargets?,
    nextFocusTargets: UserAssetRowFocusTargets?,
    firstRowPreviousFocusRequester: FocusRequester?,
    onEdit: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val propertiesText = if (fileMetadata != null) {
        val fileSize = Formatter.formatFileSize(context, fileMetadata.length)
        val skeleton = if (DateFormat.is24HourFormat(context)) "yMMMdHm" else "yMMMdhm"
        val formattedDate = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .format(Date(fileMetadata.lastModified))
        "$fileSize  •  $formattedDate"
    } else {
        stringResource(R.string.msg_file_not_found)
    }
    val showEditButton = item.canEdit()
    val actionFocusOrder = remember(showEditButton, focusTargets) {
        if (showEditButton) listOf(focusTargets.edit, focusTargets.delete) else listOf(focusTargets.delete)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(8.dp)
        ) {
            Text(
                text = item.assetUrl.remarks,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = propertiesText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showEditButton) {
            AppIconButton(
                icon = painterResource(R.drawable.ic_edit_24dp),
                label = stringResource(R.string.menu_item_edit_asset),
                focusRequester = focusTargets.edit,
                modifier = Modifier.dpadRowActionNavigation(
                    current = focusTargets.edit,
                    order = actionFocusOrder,
                    previousRow = previousFocusTargets?.edit ?: previousFocusTargets?.delete
                        ?: firstRowPreviousFocusRequester,
                    nextRow = nextFocusTargets?.edit ?: nextFocusTargets?.delete,
                    requestFocus = requestRowFocus
                ),
                onClick = onEdit
            )
        }
        AppIconButton(
            icon = painterResource(R.drawable.ic_delete_24dp),
            label = stringResource(R.string.menu_item_del_asset),
            focusRequester = focusTargets.delete,
            modifier = Modifier.dpadRowActionNavigation(
                current = focusTargets.delete,
                order = actionFocusOrder,
                previousRow = previousFocusTargets?.delete ?: firstRowPreviousFocusRequester,
                nextRow = nextFocusTargets?.delete,
                requestFocus = requestRowFocus
            ),
            onClick = onDeleteClick
        )
    }
}
