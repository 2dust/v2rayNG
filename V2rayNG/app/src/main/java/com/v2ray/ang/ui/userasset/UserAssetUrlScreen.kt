package com.v2ray.ang.ui.userasset

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.DeleteConfirmDialog
import com.v2ray.ang.ui.compose.FormTextField

private val FormVerticalPad = 8.dp

/**
 * Asset URL editor.
 */
@Composable
fun UserAssetUrlScreen(viewModel: UserAssetUrlViewModel) {
    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack = remember(onAction) { { onAction(UserAssetUrlAction.Back) } }

    BackHandler(onBack = onBack)

    BaseScreen(
        viewModel = viewModel,
        topBar = {
            val isEdit by viewModel.isEdit.collectAsStateWithLifecycle()
            UserAssetUrlTopBar(isEdit = isEdit, onBack = onBack, onAction = onAction)
        }
    ) { state, _ ->
        UserAssetUrlForm(remarks = state.remarks, url = state.url, onAction = onAction)

        if (state.showDeleteDialog) {
            DeleteConfirmDialog(
                message = stringResource(R.string.confirm_delete_asset_source),
                onConfirm = { onAction(UserAssetUrlAction.DialogConfirm) },
                onDismiss = { onAction(UserAssetUrlAction.DialogDismiss) }
            )
        }
    }
}

@Composable
private fun UserAssetUrlTopBar(
    isEdit: Boolean,
    onBack: () -> Unit,
    onAction: (UserAssetUrlAction) -> Unit,
    modifier: Modifier = Modifier
) {
    AppTopBar(
        title = stringResource(R.string.title_user_asset_add_url),
        onBackClick = onBack,
        modifier = modifier,
        actions = {
            if (isEdit) {
                IconButton(onClick = { onAction(UserAssetUrlAction.DeleteClicked) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.acc_delete)
                    )
                }
            }
            IconButton(onClick = { onAction(UserAssetUrlAction.Save) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_fab_check),
                    contentDescription = stringResource(R.string.acc_save)
                )
            }
        }
    )
}

@Composable
private fun UserAssetUrlForm(
    remarks: String,
    url: String,
    onAction: (UserAssetUrlAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val onRemarksChange = remember(onAction) {
        { value: String -> onAction(UserAssetUrlAction.RemarksChanged(value)) }
    }
    val onUrlChange = remember(onAction) {
        { value: String -> onAction(UserAssetUrlAction.UrlChanged(value)) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
            .padding(vertical = FormVerticalPad)
    ) {
        FormTextField(
            label = stringResource(R.string.sub_setting_remarks),
            value = remarks,
            onValueChange = onRemarksChange
        )
        FormTextField(
            label = stringResource(R.string.title_url),
            value = url,
            onValueChange = onUrlChange
        )
    }
}

// ===== previews =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UserAssetUrlFormPreview() = AppTheme {
    UserAssetUrlForm(
        remarks = "geosite-cn.dat",
        url = "https://example.com/geosite-cn.dat",
        onAction = {}
    )
}
