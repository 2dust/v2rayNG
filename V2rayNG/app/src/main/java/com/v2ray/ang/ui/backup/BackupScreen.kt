package com.v2ray.ang.ui.backup

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsMenuItem

private val SectionGap = 16.dp

@Stable
class BackupMenuCallbacks(onAction: (BackupAction) -> Unit) {
    val onBackup: () -> Unit = { onAction(BackupAction.BackupClicked) }
    val onShare: () -> Unit = { onAction(BackupAction.ShareClicked) }
    val onRestore: () -> Unit = { onAction(BackupAction.RestoreClicked) }
    val onWebDav: () -> Unit = { onAction(BackupAction.WebDavClicked) }
    val onCleanup: () -> Unit = { onAction(BackupAction.CleanupClicked) }
}

@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onPlatformEvent: (BackupEvent) -> Boolean
) {
    val dispatch = remember(viewModel) { viewModel::onAction }
    val dialogs = remember { BackupDialogHost() }
    val callbacks = remember(dispatch) { BackupMenuCallbacks(dispatch) }
    val onBack = remember(dispatch) { { dispatch(BackupAction.Back) } }

    BackHandler(onBack = onBack)

    BaseScreen(
        viewModel = viewModel,
        onEvent = { event ->
            when (event) {
                is BackupEvent.ShowChannelPicker -> {
                    dialogs.show(BackupDialog.PickChannel(event.restoring))
                    true
                }

                BackupEvent.ShowWebDavEditor -> {
                    dialogs.show(BackupDialog.WebDav)
                    true
                }

                BackupEvent.ShowCleanupConfirmation -> {
                    dialogs.show(BackupDialog.CleanupConfirmation)
                    true
                }

                is BackupEvent -> onPlatformEvent(event)
                else -> false
            }
        },
        topBar = {
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            AppTopBar(
                title = stringResource(R.string.title_configuration_backup_restore),
                onBackClick = onBack,
                isLoading = isLoading
            )
        }
    ) { state, _ ->
        BackupContent(webDavSummary = state.webDav.summary, callbacks = callbacks)
        BackupDialogs(
            dialog = dialogs.current,
            draft = state.draft,
            onDismiss = dialogs.dismiss,
            onAction = dispatch
        )
    }
}

@Composable
private fun BackupContent(
    webDavSummary: String?,
    callbacks: BackupMenuCallbacks,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsMenuItem(
            icon = painterResource(R.drawable.ic_backup_24dp),
            title = stringResource(R.string.title_configuration_backup),
            onClick = callbacks.onBackup
        )
        SettingsMenuItem(
            icon = painterResource(R.drawable.ic_share_24dp),
            title = stringResource(R.string.title_configuration_share),
            onClick = callbacks.onShare
        )
        SettingsMenuItem(
            icon = painterResource(R.drawable.ic_restore_24dp),
            title = stringResource(R.string.title_configuration_restore),
            onClick = callbacks.onRestore
        )
        Spacer(modifier = Modifier.height(SectionGap))
        SettingsMenuItem(
            icon = painterResource(R.drawable.ic_settings_24dp),
            title = stringResource(R.string.title_webdav_config_setting),
            onClick = callbacks.onWebDav,
            subtitle = webDavSummary
        )
        Spacer(modifier = Modifier.height(SectionGap))
        SettingsMenuItem(
            icon = painterResource(R.drawable.ic_delete_24dp),
            title = stringResource(R.string.title_profile_storage_cleanup),
            onClick = callbacks.onCleanup,
            subtitle = stringResource(R.string.summary_profile_storage_cleanup)
        )
        NavigationBarsSpacer()
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupContentPreview() = AppTheme {
    BackupContent(
        webDavSummary = "https://dav.example.com/remote.php/dav",
        callbacks = BackupMenuCallbacks {}
    )
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BackupContentDarkPreview() = AppTheme {
    BackupContent(webDavSummary = null, callbacks = BackupMenuCallbacks {})
}
