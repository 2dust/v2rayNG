package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTheme
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.compose.SettingsMenuItem
import com.v2ray.ang.compose.SettingsSwitchItem
import com.v2ray.ang.compose.VersionInfoBlock
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.UpdateCheckerManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

class CheckUpdateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                CheckUpdateScreen(onBackClick = { finish() })
            }
        }
    }
}

@Composable
fun CheckUpdateScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var checkPreRelease by remember {
        mutableStateOf(MmkvManager.decodeSettingsBool(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, false))
    }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<CheckUpdateResult?>(null) }

    val versionText = "v${BuildConfig.VERSION_NAME} (${CoreNativeManager.getLibVersion()})"

    fun checkForUpdates(includePreRelease: Boolean) {
        context.toast(context.getString(R.string.update_checking_for_update))
        isLoading = true
        scope.launch {
            try {
                val result = UpdateCheckerManager.checkForUpdate(includePreRelease)
                if (result.hasUpdate) {
                    updateResult = result
                    showUpdateDialog = true
                } else {
                    context.toastSuccess(R.string.update_already_latest_version)
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Failed to check for updates: ${e.message}")
                context.toastError(e.message ?: context.getString(R.string.toast_failure))
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { checkForUpdates(checkPreRelease) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.update_check_for_update),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSwitchItem(
                icon = painterResource(R.drawable.ic_source_code_24dp),
                title = stringResource(R.string.update_check_pre_release),
                checked = checkPreRelease,
                onCheckedChange = { checked ->
                    checkPreRelease = checked
                    MmkvManager.encodeSettings(AppConfig.PREF_CHECK_UPDATE_PRE_RELEASE, checked)
                }
            )
            SettingsMenuItem(
                icon = painterResource(R.drawable.ic_check_update_24dp),
                title = stringResource(R.string.update_check_for_update),
                onClick = { checkForUpdates(checkPreRelease) }
            )
            VersionInfoBlock(versionText = versionText)
        }
    }

    if (showUpdateDialog && updateResult != null) {
        val result = updateResult!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(stringResource(R.string.update_new_version_found, result.latestVersion ?: "")) },
            text = { Text(result.releaseNotes ?: "") },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    result.downloadUrl?.let { Utils.openUri(context, it) }
                }) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
