package com.v2ray.ang.ui

import android.os.Bundle
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.ConfirmDialog
import com.v2ray.ang.compose.FormTextField
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.File

class UserAssetUrlActivity : ComponentActivity() {
    companion object {
        const val ASSET_URL_QRCODE = "ASSET_URL_QRCODE"
    }

    private val extDir by lazy { File(Utils.userAssetPath(this)) }
    private val editAssetId by lazy { intent.getStringExtra("assetId").orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val assetItem = MmkvManager.decodeAsset(editAssetId)
        val assetUrlQrcode = intent.getStringExtra(ASSET_URL_QRCODE)
        val assetNameQrcode = File(assetUrlQrcode.toString()).name

        val initialRemarks: String
        val initialUrl: String
        when {
            assetItem != null -> { initialRemarks = assetItem.remarks; initialUrl = assetItem.url }
            assetUrlQrcode != null -> { initialRemarks = assetNameQrcode; initialUrl = assetUrlQrcode }
            else -> { initialRemarks = ""; initialUrl = "" }
        }

        setContent {
            AppTheme {
                UserAssetUrlScreen(
                    editAssetId = editAssetId,
                    initialRemarks = initialRemarks,
                    initialUrl = initialUrl,
                    onBackClick = { finish() },
                    onSave = { r, u -> saveServer(r, u) },
                    onDelete = { deleteServer() }
                )
            }
        }
    }

    private fun saveServer(remarks: String, url: String): Boolean {
        var assetItem = MmkvManager.decodeAsset(editAssetId)
        var assetId = editAssetId
        if (assetItem != null) {
            val file = extDir.resolve(assetItem.remarks)
            if (file.exists()) {
                try {
                    file.delete()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "Failed to delete asset file: ${file.path}", e)
                }
            }
        } else {
            assetId = Utils.getUuid()
            assetItem = AssetUrlItem()
        }

        assetItem.remarks = remarks
        assetItem.url = url

        val assetList = MmkvManager.decodeAssetUrls()
        if (assetList.any { it.assetUrl.remarks == assetItem.remarks && it.guid != assetId }) {
            toast(R.string.msg_remark_is_duplicate)
            return false
        }
        if (TextUtils.isEmpty(assetItem.remarks)) {
            toast(R.string.sub_setting_remarks)
            return false
        }
        if (TextUtils.isEmpty(assetItem.url)) {
            toast(R.string.title_url)
            return false
        }

        MmkvManager.encodeAsset(assetId, assetItem)
        toastSuccess(R.string.toast_success)
        finish()
        return true
    }

    private fun deleteServer(): Boolean {
        if (editAssetId.isNotEmpty()) {
            MmkvManager.removeAssetUrl(editAssetId)
            finish()
        }
        return true
    }
}

@Composable
fun UserAssetUrlScreen(
    editAssetId: String,
    initialRemarks: String,
    initialUrl: String,
    onBackClick: () -> Unit,
    onSave: (String, String) -> Boolean,
    onDelete: () -> Unit
) {
    var remarks by remember { mutableStateOf(initialRemarks) }
    var url by remember { mutableStateOf(initialUrl) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_user_asset_add_url),
                onBackClick = onBackClick,
                actions = {
                    if (editAssetId.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(painterResource(R.drawable.ic_delete_24dp), contentDescription = stringResource(R.string.menu_item_del_config))
                        }
                    }
                    IconButton(onClick = { onSave(remarks, url) }) {
                        Icon(painterResource(R.drawable.ic_fab_check), contentDescription = stringResource(R.string.menu_item_save_config))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(vertical = 8.dp)
        ) {
            FormTextField(stringResource(R.string.sub_setting_remarks), remarks, { remarks = it })
            FormTextField(stringResource(R.string.title_url), url, { url = it }, singleLine = false)
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            message = stringResource(R.string.del_config_comfirm),
            confirmText = stringResource(android.R.string.ok),
            dismissText = stringResource(android.R.string.cancel),
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
