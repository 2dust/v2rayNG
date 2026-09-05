package com.v2ray.ang.ui.checkupdate

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.VersionInfoBlock
import com.v2ray.ang.ui.compose.verticalScrollbar

private val ReleaseNotesMaxHeight = 500.dp

@Stable
private class UpdateDialogHost {

    var current by mutableStateOf<UpdateInfo?>(null)
        private set

    val show: (UpdateInfo) -> Unit = { current = it }

    val dismiss: () -> Unit = { current = null }
}

@Composable
fun CheckUpdateScreen(viewModel: CheckUpdateViewModel) {
    val dispatch = remember(viewModel) { viewModel::onAction }
    val onBack = remember(dispatch) { { dispatch(CheckUpdateAction.Back) } }
    val dialog = remember { UpdateDialogHost() }

    BackHandler(onBack = onBack)

    BaseScreen(
        viewModel = viewModel,
        onEvent = { event ->
            when (event) {
                is CheckUpdateEvent.UpdateAvailable -> {
                    dialog.show(event.update)
                    true
                }
                else -> false
            }
        },
        topBar = {
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            AppTopBar(
                title = stringResource(R.string.update_check_for_update),
                onBackClick = onBack,
                isLoading = isLoading
            )
        }
    ) { uiState, onAction ->
        CheckUpdateContent(
            checkPreRelease = uiState.checkPreRelease,
            versionText = uiState.versionText,
            onAction = onAction
        )
        dialog.current?.let { update ->
            val onConfirm = remember(update, onAction, dialog) {
                {
                    onAction(CheckUpdateAction.DownloadConfirmed(update.downloadUrl))
                    dialog.dismiss()
                }
            }
            UpdateDialog(
                update = update,
                onConfirm = onConfirm,
                onDismiss = dialog.dismiss
            )
        }
    }
}

@Composable
private fun CheckUpdateContent(
    checkPreRelease: Boolean,
    versionText: String,
    onAction: (CheckUpdateAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val onPreReleaseChange = remember(onAction) {
        { enabled: Boolean -> onAction(CheckUpdateAction.TogglePreRelease(enabled)) }
    }
    val onCheckNow = remember(onAction) { { onAction(CheckUpdateAction.CheckNow) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
    ) {
        SettingsSwitchItem(
            icon = painterResource(R.drawable.ic_source_code_24dp),
            title = stringResource(R.string.update_check_pre_release),
            checked = checkPreRelease,
            onCheckedChange = onPreReleaseChange
        )
        SettingsMenuItem(
            icon = painterResource(R.drawable.ic_check_update_24dp),
            title = stringResource(R.string.update_check_for_update),
            onClick = onCheckNow
        )
        VersionInfoBlock(versionText = versionText)
        NavigationBarsSpacer()
    }
}

@Composable
private fun UpdateDialog(
    update: UpdateInfo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notesScrollState = rememberScrollState()
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_new_version_found, update.version)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ReleaseNotesMaxHeight)
                    .verticalScrollbar(notesScrollState)
                    .verticalScroll(notesScrollState)
            ) {
                Text(
                    text = update.releaseNotes,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// ===== previews =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CheckUpdateContentPreview() = AppTheme {
    CheckUpdateContent(
        checkPreRelease = true,
        versionText = "v2.3.3 (26.2.6)",
        onAction = {}
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UpdateDialogPreview() = AppTheme {
    UpdateDialog(
        update = UpdateInfo(
            version = "2.4.0",
            releaseNotes = "- Fixed a crash on startup\n".repeat(20),
            downloadUrl = "https://example.com/app.apk"
        ),
        onConfirm = {},
        onDismiss = {}
    )
}
