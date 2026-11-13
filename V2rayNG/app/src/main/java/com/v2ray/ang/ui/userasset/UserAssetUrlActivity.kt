package com.v2ray.ang.ui.userasset

import android.os.Bundle
import android.text.TextUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBarAction
import com.v2ray.ang.ui.compose.TvTextFieldNavigation
import com.v2ray.ang.ui.compose.tvAwareImePadding
import com.v2ray.ang.ui.compose.tvSafeAreaPadding
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import java.io.File

class UserAssetUrlActivity : BaseComponentActivity() {
    companion object {
        const val ASSET_URL_QRCODE = "ASSET_URL_QRCODE"
    }

    private val extDir by lazy { File(Utils.userAssetPath(this)) }
    private val editAssetId by lazy { intent.getStringExtra("assetId").orEmpty() }
    private lateinit var initialRemarks: String
    private lateinit var initialUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val assetItem = MmkvManager.decodeAsset(editAssetId)
        val assetUrlQrcode = intent.getStringExtra(ASSET_URL_QRCODE)
        val assetNameQrcode = File(assetUrlQrcode.toString()).name

        when {
            assetItem != null -> {
                initialRemarks = assetItem.remarks
                initialUrl = assetItem.url
            }

            assetUrlQrcode != null -> {
                initialRemarks = assetNameQrcode
                initialUrl = assetUrlQrcode
            }

            else -> {
                initialRemarks = ""
                initialUrl = ""
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        UserAssetUrlScreen(
            editAssetId = editAssetId,
            initialRemarks = initialRemarks,
            initialUrl = initialUrl,
            onBackClick = { finish() },
            onSave = { r, u -> saveServer(r, u) },
            onDelete = { deleteServer() }
        )
    }

    private fun saveServer(remarks: String, url: String): Boolean {
        val assetList = MmkvManager.decodeAssetUrls()
        if (assetList.any { it.assetUrl.remarks == remarks && it.guid != editAssetId }) {
            toast(R.string.msg_remark_is_duplicate)
            return false
        }
        if (TextUtils.isEmpty(remarks) || TextUtils.isEmpty(url)) {
            return false
        }

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
    var remarks by rememberSaveable(editAssetId, initialRemarks) { mutableStateOf(initialRemarks) }
    var isRemarksError by rememberSaveable(editAssetId) { mutableStateOf(false) }
    var url by rememberSaveable(editAssetId, initialUrl) { mutableStateOf(initialUrl) }
    var isUrlError by rememberSaveable(editAssetId) { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable(editAssetId) { mutableStateOf(false) }

    val remarksFocusRequester = remember { FocusRequester() }
    val urlFocusRequester = remember { FocusRequester() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_user_asset_add_url),
                onBackClick = onBackClick,
                onMoveDown = remarksFocusRequester::requestFocus,
                actionItems = buildList {
                    if (editAssetId.isNotEmpty()) add(
                        AppTopBarAction(
                            icon = painterResource(R.drawable.ic_delete_24dp),
                            label = stringResource(R.string.menu_item_del_asset),
                            onClick = { showDeleteConfirm = true }
                        )
                    )
                    add(AppTopBarAction(
                        icon = painterResource(R.drawable.ic_fab_check),
                        label = stringResource(R.string.action_save),
                        contentDescription = stringResource(R.string.acc_save),
                        onClick = {
                            isRemarksError = remarks.isBlank()
                            isUrlError = url.isBlank()
                            if (!isRemarksError && !isUrlError) onSave(remarks, url)
                        }
                    ))
                }
            )
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .tvSafeAreaPadding()
                .tvAwareImePadding()
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState)
                .padding(vertical = 8.dp)
        ) {
            FormTextField(
                label = stringResource(R.string.sub_setting_remarks),
                value = remarks,
                onValueChange = { remarks = it },
                tvNavigation = TvTextFieldNavigation(
                    focusRequester = remarksFocusRequester,
                    onMoveUp = { false },
                    onMoveDown = urlFocusRequester::requestFocus
                ),
                isError = isRemarksError
            )
            FormTextField(
                label = stringResource(R.string.title_url),
                value = url,
                onValueChange = { url = it },
                tvNavigation = TvTextFieldNavigation(
                    focusRequester = urlFocusRequester,
                    onMoveUp = remarksFocusRequester::requestFocus,
                    onMoveDown = { true }
                ),
                isError = isUrlError
            )
            NavigationBarsSpacer()
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            message = stringResource(R.string.confirm_delete_asset_source),
            itemName = initialRemarks,
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirm = false }
        )
    }
}
