package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.File

class UserAssetUrlActivity : BaseActivity() {
    companion object {
        const val ASSET_URL_QRCODE = "ASSET_URL_QRCODE"
    }

    private val editAssetId by lazy { intent.getStringExtra("assetId").orEmpty() }
    private var assetState = mutableStateOf(AssetUrlItem())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val assetItem = MmkvManager.decodeAsset(editAssetId)
        val assetUrlQrcode = intent.getStringExtra(ASSET_URL_QRCODE)
        
        if (assetItem != null) {
            assetState.value = assetItem
        } else if (assetUrlQrcode != null) {
            assetState.value = AssetUrlItem(File(assetUrlQrcode).name, assetUrlQrcode)
        }

        setContent {
            MaterialTheme {
                UserAssetUrlScreen(
                    asset = assetState.value,
                    canDelete = editAssetId.isNotEmpty(),
                    onBack = { finish() },
                    onDelete = { deleteServer() },
                    onSave = { saveServer() },
                    onAssetChange = { assetState.value = it }
                )
            }
        }
    }

    private fun saveServer() {
        val assetItem = assetState.value
        val assetId = if (editAssetId.isNotEmpty()) editAssetId else Utils.getUuid()

        if (assetItem.remarks.isEmpty()) {
            toast(R.string.sub_setting_remarks)
            return
        }
        if (assetItem.url.isEmpty()) {
            toast(R.string.title_url)
            return
        }

        val assetList = MmkvManager.decodeAssetUrls()
        if (assetList.any { it.assetUrl.remarks == assetItem.remarks && it.guid != assetId }) {
            toast(R.string.msg_remark_is_duplicate)
            return
        }

        if (editAssetId.isNotEmpty()) {
            val oldAsset = MmkvManager.decodeAsset(editAssetId)
            if (oldAsset != null) {
                File(Utils.userAssetPath(this), oldAsset.remarks).delete()
            }
        }

        MmkvManager.encodeAsset(assetId, assetItem)
        toastSuccess(R.string.toast_success)
        finish()
    }

    private fun deleteServer() {
        if (editAssetId.isNotEmpty()) {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeAssetUrl(editAssetId)
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAssetUrlScreen(
    asset: AssetUrlItem,
    canDelete: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onAssetChange: (AssetUrlItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_user_asset_add_url)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = asset.remarks,
                onValueChange = { onAssetChange(asset.copy(remarks = it)) },
                label = { Text(stringResource(R.string.sub_setting_remarks)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = asset.url,
                onValueChange = { onAssetChange(asset.copy(url = it)) },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
