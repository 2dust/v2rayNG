package com.v2ray.ang.ui.userasset

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.viewModels
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppDivider
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.SettingsListItem
import com.v2ray.ang.compose.verticalScrollbar
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class UserAssetActivity : HelperBaseComponentActivity() {

    private val viewModel: UserAssetViewModel by viewModels()
    val extDir by lazy { File(Utils.userAssetPath(this)) }
    private val isLoadingState = MutableStateFlow(false)
    private val geoFilesSourceState = MutableStateFlow("")
    private val refreshTrigger = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        geoFilesSourceState.value = getGeoFilesSources()
    }

    @Composable
    override fun ScreenContent() {
        UserAssetScreen(
            viewModel = viewModel,
            extDir = extDir,
            isLoadingState = isLoadingState,
            geoFilesSourceState = geoFilesSourceState,
            refreshTrigger = refreshTrigger,
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
            onRemoveAsset = { guid ->
                val asset = viewModel.getAssets().find { it.guid == guid }
                if (asset != null) {
                    extDir.listFiles()?.find { it.name == asset.assetUrl.remarks }?.delete()
                    MmkvManager.removeAssetUrl(guid)
                    initAssets()
                }
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
        refreshData()
        isLoadingState.value = true
        toast(R.string.msg_downloading_content)

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = viewModel.downloadGeoFiles(extDir, httpPort, proxyUsername, proxyPassword)
            withContext(Dispatchers.Main) {
                if (result.successCount > 0) {
                    toast(getString(R.string.title_update_config_count, result.successCount))
                } else {
                    toast(getString(R.string.toast_failure))
                }
                refreshData()
                isLoadingState.value = false
            }
        }
    }

    fun initAssets() {
        lifecycleScope.launch(Dispatchers.Default) {
            SettingsManager.initAssets(this@UserAssetActivity, assets)
            withContext(Dispatchers.Main) {
                refreshData()
            }
        }
    }

    fun refreshData() {
        viewModel.reload(getGeoFilesSources())
        refreshTrigger.value++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAssetScreen(
    viewModel: UserAssetViewModel,
    extDir: File,
    isLoadingState: MutableStateFlow<Boolean>,
    geoFilesSourceState: MutableStateFlow<String>,
    refreshTrigger: MutableStateFlow<Int>,
    geoFilesSourcesList: List<String>,
    onBackClick: () -> Unit,
    onGeoSourceSelected: (String) -> Unit,
    onAddFileClick: () -> Unit,
    onAddUrlClick: () -> Unit,
    onAddQrcodeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onEditAsset: (String) -> Unit,
    onRemoveAsset: (String) -> Unit
) {
    val isLoading by isLoadingState.collectAsState()
    val geoFilesSource by geoFilesSourceState.collectAsState()
    val assets by viewModel.assetsFlow.collectAsStateWithLifecycle()
    val trigger by refreshTrigger.collectAsState()

    var showAddMenu by remember { mutableStateOf(false) }
    var deleteTargetGuid by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_user_asset_setting),
                onBackClick = onBackClick,
                isLoading = isLoading,
                actions = {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(painterResource(R.drawable.ic_add_24dp), contentDescription = stringResource(R.string.menu_item_add_asset))
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface,
                            offset = DpOffset(x = 0.dp, y = 0.dp),
                            modifier = Modifier.wrapContentWidth(Alignment.End)
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_add_file)) },
                                onClick = { showAddMenu = false; onAddFileClick() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_add_url)) },
                                onClick = { showAddMenu = false; onAddUrlClick() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_item_scan_qrcode)) },
                                onClick = { showAddMenu = false; onAddQrcodeClick() }
                            )
                        }
                    }
                    IconButton(onClick = onDownloadClick) {
                        Icon(painterResource(R.drawable.ic_cloud_download_24dp), contentDescription = stringResource(R.string.menu_item_download_file))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScrollbar(listState)
        ) {
            item(key = "geo_source_$trigger") {
                SettingsListItem(
                    title = stringResource(R.string.asset_geo_files_sources),
                    entries = geoFilesSourcesList,
                    values = geoFilesSourcesList,
                    selectedValue = geoFilesSource,
                    onSelected = { onGeoSourceSelected(it) }
                )
            }
            item {
                Text(
                    text = stringResource(R.string.title_user_asset_setting),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
            itemsIndexed(items = assets, key = { _, item -> "${item.guid}_$trigger" }) { _, item ->
                UserAssetItem(
                    item = item,
                    extDir = extDir,
                    onEdit = { onEditAsset(item.guid) },
                    onDeleteClick = { deleteTargetGuid = item.guid }
                )
                AppDivider(modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
    }


    if (deleteTargetGuid != null) {
        val guid = deleteTargetGuid!!
        val assetName = assets.find { it.guid == guid }?.assetUrl?.remarks ?: ""
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm) + "\n$assetName",
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = { onRemoveAsset(guid) },
            onDismiss = { deleteTargetGuid = null }
        )
    }
}

@Composable
private fun UserAssetItem(
    item: AssetUrlCache,
    extDir: File,
    onEdit: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val file = remember(item.guid, item.assetUrl.remarks) {
        extDir.listFiles()?.find { it.name == item.assetUrl.remarks }
    }
    val propertiesText = if (file != null) {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
        "${file.length().toTrafficString()}  •  ${dateFormat.format(Date(file.lastModified()))}"
    } else {
        stringResource(R.string.msg_file_not_found)
    }
    val showEditButton = item.assetUrl.locked != true && item.assetUrl.url != "file"

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(8.dp)) {
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
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_24dp),
                    contentDescription = stringResource(R.string.menu_item_edit_config),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_24dp),
                contentDescription = stringResource(R.string.menu_item_del_config),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
