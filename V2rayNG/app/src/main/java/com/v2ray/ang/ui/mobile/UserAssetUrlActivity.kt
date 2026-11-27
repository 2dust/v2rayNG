package com.v2ray.ang.ui.mobile

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.mobile.base.BaseComponentActivity
import com.v2ray.ang.ui.mobile.compose.AppTopBar
import com.v2ray.ang.ui.mobile.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.mobile.compose.FormTextField
import com.v2ray.ang.ui.mobile.compose.NavigationBarsSpacer
import com.v2ray.ang.util.Utils
import com.v2ray.ang.vm.UserAssetUrlViewModel
import kotlinx.coroutines.launch
import java.io.File

class UserAssetUrlActivity : BaseComponentActivity() {
    companion object {
        const val ASSET_URL_QRCODE = "ASSET_URL_QRCODE"
    }

    private val viewModel: UserAssetUrlViewModel by viewModels()

    private val extDir by lazy { File(Utils.userAssetPath(this)) }
    private val editAssetId by lazy { intent.getStringExtra("assetId").orEmpty() }
    private lateinit var initialRemarks: String
    private lateinit var initialUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeViewModel()

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

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewModelEvent.collect { event ->
                    when (event) {
                        is UserAssetUrlViewModel.UserAssetUrlViewModelEvent.SaveSuccess,
                        is UserAssetUrlViewModel.UserAssetUrlViewModelEvent.DeleteSuccess -> {
                            finish()
                        }
                    }
                }
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
            onSave = { r, u -> viewModel.saveAssetUrl(editAssetId, r, u, extDir) },
            onDelete = { viewModel.deleteAssetUrl(editAssetId) }
        )
    }
}

@Composable
fun UserAssetUrlScreen(
    editAssetId: String,
    initialRemarks: String,
    initialUrl: String,
    onBackClick: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var remarks by rememberSaveable(editAssetId, initialRemarks) { mutableStateOf(initialRemarks) }
    var isRemarksError by rememberSaveable(editAssetId) { mutableStateOf(false) }
    var url by rememberSaveable(editAssetId, initialUrl) { mutableStateOf(initialUrl) }
    var isUrlError by rememberSaveable(editAssetId) { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable(editAssetId) { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_user_asset_add_url),
                onBackClick = onBackClick,
                actions = {
                    if (editAssetId.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                painterResource(R.drawable.ic_delete_24dp),
                                contentDescription = stringResource(R.string.acc_delete)
                            )
                        }
                    }
                    IconButton(onClick = {
                        val remarksErr = remarks.isBlank()
                        val urlErr = url.isBlank()

                        isRemarksError = remarksErr
                        isUrlError = urlErr

                        val hasError = remarksErr || urlErr
                        if (!hasError) {
                            onSave(remarks, url)
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_fab_check),
                            contentDescription = stringResource(R.string.acc_save)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            FormTextField(
                label = stringResource(R.string.sub_setting_remarks),
                value = remarks,
                onValueChange = { remarks = it },
                isError = isRemarksError
            )
            FormTextField(
                label = stringResource(R.string.title_url),
                value = url,
                onValueChange = { url = it },
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
