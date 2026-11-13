package com.v2ray.ang.ui.update

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.AppUpdateManager
import com.v2ray.ang.util.LogUtil
import java.util.Locale

/**
 * Renders the in-app update flow for [controller]: the "new version" sheet with Update / Later, the
 * download progress, and the install step. Starting the installer needs an Activity, so that part
 * lives here: when the permission to install apps is missing it opens the system screen for it, and
 * if the user still does not allow it, tells them the file is waiting in the Downloads folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateSheetHost(controller: AppUpdateController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun locationOf(ready: UpdateUiState.ReadyToInstall) = ready.savedTo ?: ready.file.absolutePath

    fun startInstall(ready: UpdateUiState.ReadyToInstall) {
        try {
            context.startActivity(AppUpdateManager.installIntent(context, ready.file))
            controller.finish()
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to start the installer", e)
            controller.installBlocked(locationOf(ready))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val ready = controller.state.value as? UpdateUiState.ReadyToInstall
            ?: return@rememberLauncherForActivityResult
        if (AppUpdateManager.canInstallPackages(context)) {
            startInstall(ready)
        } else {
            controller.installBlocked(locationOf(ready))
        }
    }

    // Downloaded: install right away if allowed, otherwise ask for the permission first.
    LaunchedEffect(state) {
        val ready = state as? UpdateUiState.ReadyToInstall ?: return@LaunchedEffect
        if (AppUpdateManager.canInstallPackages(context)) {
            startInstall(ready)
        } else {
            try {
                permissionLauncher.launch(AppUpdateManager.installPermissionIntent(context))
            } catch (e: ActivityNotFoundException) {
                controller.installBlocked(locationOf(ready))
            }
        }
    }

    val current = state
    if (current is UpdateUiState.Available || current is UpdateUiState.Downloading) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            // A running download cannot be swiped away; it has its own Cancel button.
            confirmValueChange = { value ->
                value != SheetValue.Hidden || controller.state.value !is UpdateUiState.Downloading
            }
        )
        ModalBottomSheet(
            onDismissRequest = {
                if (controller.state.value is UpdateUiState.Available) controller.later()
            },
            sheetState = sheetState
        ) {
            when (current) {
                is UpdateUiState.Available -> AvailableContent(
                    state = current,
                    onLater = controller::later,
                    onUpdate = controller::startDownload
                )

                is UpdateUiState.Downloading -> DownloadingContent(
                    state = current,
                    onCancel = controller::cancelDownload
                )

                else -> Unit
            }
        }
    }

    (current as? UpdateUiState.SavedForManualInstall)?.let { saved ->
        AlertDialog(
            onDismissRequest = controller::finish,
            text = { Text(stringResource(R.string.update_saved_in_downloads, saved.location)) },
            confirmButton = {
                TextButton(onClick = controller::finish) { Text(stringResource(R.string.action_ok)) }
            }
        )
    }
}

@Composable
private fun AvailableContent(
    state: UpdateUiState.Available,
    onLater: () -> Unit,
    onUpdate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.update_new_version_found, state.result.latestVersion.orEmpty()),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        val notes = state.result.releaseNotes.orEmpty().trim()
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.result.assetSize > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.result.assetSize.toTrafficString(),
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(onClick = onLater, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.update_later))
            }
            Button(onClick = onUpdate, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.update_now))
            }
        }
    }
}

@Composable
private fun DownloadingContent(
    state: UpdateUiState.Downloading,
    onCancel: () -> Unit
) {
    // Sizes and percentages are figures: keep them left-to-right in right-to-left languages.
    val figures = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.update_downloading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = state.result.latestVersion.orEmpty(),
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val sizeText = if (state.total > 0) {
                "${state.downloaded.toTrafficString()} / ${state.total.toTrafficString()}"
            } else {
                state.downloaded.toTrafficString()
            }
            Text(text = sizeText, style = figures, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.total > 0) {
                val percent = (state.downloaded * 100 / state.total).toInt().coerceIn(0, 100)
                Text(
                    text = String.format(Locale.getDefault(), "%d%%", percent),
                    style = figures,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
