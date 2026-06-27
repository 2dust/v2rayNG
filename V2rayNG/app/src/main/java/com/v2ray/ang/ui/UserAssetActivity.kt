package com.v2ray.ang.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.UserAssetViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UserAssetActivity : HelperBaseActivity() {
    private val viewModel: UserAssetViewModel by viewModels()
    val extDir by lazy { File(Utils.userAssetPath(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                UserAssetScreen(
                    viewModel = viewModel,
                    isLoadingFlow = isLoadingFlow,
                    getGeoFilesSources = { getGeoFilesSources() },
                    onBack = { finish() },
                    onAddFile = { showFileChooser() },
                    onAddUrl = { startActivity(Intent(this, UserAssetUrlActivity::class.java)) },
                    onImportQRcode = { importAssetFromQRcode() },
                    onDownloadGeoFiles = { downloadGeoFiles() },
                    onSetGeoFilesSources = { setGeoFilesSources() },
                    onEditAsset = { guid ->
                        startActivity(Intent(this, UserAssetUrlActivity::class.java).putExtra("assetId", guid))
                    },
                    onRemoveAsset = { guid, remarks -> removeAsset(guid, remarks) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload(getGeoFilesSources())
    }

    private fun getGeoFilesSources(): String {
        return MmkvManager.decodeSettingsString(AppConfig.PREF_GEO_FILES_SOURCES) ?: AppConfig.GEO_FILES_SOURCES.first()
    }

    private fun setGeoFilesSources() {
        AlertDialog.Builder(this).setItems(AppConfig.GEO_FILES_SOURCES.toTypedArray()) { _, i ->
            try {
                val value = AppConfig.GEO_FILES_SOURCES[i]
                MmkvManager.encodeSettings(AppConfig.PREF_GEO_FILES_SOURCES, value)
                viewModel.reload(value)
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to set geo files sources", e)
            }
        }.show()
    }

    private fun showFileChooser() {
        launchFileChooser { uri ->
            if (uri == null) return@launchFileChooser
            val assetId = Utils.getUuid()
            runCatching {
                val assetItem = AssetUrlItem(getCursorName(uri) ?: uri.toString(), "file")
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

    private fun copyFile(uri: Uri) {
        val targetFile = File(extDir, getCursorName(uri) ?: uri.toString())
        contentResolver.openInputStream(uri).use { inputStream ->
            targetFile.outputStream().use { fileOut ->
                inputStream?.copyTo(fileOut)
                toastSuccess(R.string.toast_success)
                viewModel.reload(getGeoFilesSources())
            }
        }
    }

    private fun getCursorName(uri: Uri): String? = try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    } catch (e: Exception) {
        LogUtil.e(AppConfig.TAG, "Failed to get cursor name", e)
        null
    }

    private fun importAssetFromQRcode() {
        launchQRCodeScanner { scanResult ->
            if (scanResult != null && Utils.isValidUrl(scanResult)) {
                startActivity(Intent(this, UserAssetUrlActivity::class.java).putExtra(UserAssetUrlActivity.ASSET_URL_QRCODE, scanResult))
            } else if (scanResult != null) {
                toast(R.string.toast_invalid_url)
            }
        }
    }

    private fun downloadGeoFiles() {
        showLoading()
        toast(R.string.msg_downloading_content)
        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = viewModel.downloadGeoFiles(extDir, httpPort, proxyUsername, proxyPassword)
            withContext(Dispatchers.Main) {
                if (result.successCount > 0) toast(getString(R.string.title_update_config_count, result.successCount))
                else toast(getString(R.string.toast_failure))
                viewModel.reload(getGeoFilesSources())
                hideLoading()
            }
        }
    }

    private fun removeAsset(guid: String, remarks: String) {
        val file = extDir.listFiles()?.find { it.name == remarks }
        AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                file?.delete()
                MmkvManager.removeAssetUrl(guid)
                lifecycleScope.launch(Dispatchers.Default) {
                    SettingsManager.initAssets(this@UserAssetActivity, assets)
                    withContext(Dispatchers.Main) { viewModel.reload(getGeoFilesSources()) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAssetScreen(
    viewModel: UserAssetViewModel,
    isLoadingFlow: kotlinx.coroutines.flow.StateFlow<Boolean>,
    getGeoFilesSources: () -> String,
    onBack: () -> Unit,
    onAddFile: () -> Unit,
    onAddUrl: () -> Unit,
    onImportQRcode: () -> Unit,
    onDownloadGeoFiles: () -> Unit,
    onSetGeoFilesSources: () -> Unit,
    onEditAsset: (String) -> Unit,
    onRemoveAsset: (String, String) -> Unit
) {
    val assets by viewModel.assetsFlow.collectAsStateWithLifecycle()
    val isLoading by isLoadingFlow.collectAsStateWithLifecycle()
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_user_asset_setting)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_add_file)) }, onClick = { showMenu = false; onAddFile() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_add_url)) }, onClick = { showMenu = false; onAddUrl() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_scan_qrcode)) }, onClick = { showMenu = false; onImportQRcode() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.menu_item_download_file)) }, onClick = { showMenu = false; onDownloadGeoFiles() })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.asset_geo_files_sources)) },
                        supportingContent = { Text(getGeoFilesSources()) },
                        modifier = Modifier.clickable(onClick = onSetGeoFilesSources)
                    )
                    HorizontalDivider()
                }
                
                items(assets, key = { it.guid }) { asset ->
                    AssetItem(
                        asset = asset,
                        onEdit = { onEditAsset(asset.guid) },
                        onRemove = { onRemoveAsset(asset.guid, asset.assetUrl.remarks) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun AssetItem(asset: AssetUrlCache, onEdit: () -> Unit, onRemove: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        headlineContent = { Text(asset.assetUrl.remarks) },
        supportingContent = { Text(asset.assetUrl.url, maxLines = 1) },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = { showMenu = false; onRemove() })
                }
            }
        }
    )
}
