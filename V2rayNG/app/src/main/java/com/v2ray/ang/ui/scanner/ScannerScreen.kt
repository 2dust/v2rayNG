package com.v2ray.ang.ui.scanner

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseEvent
import com.v2ray.ang.ui.base.BaseScreen
import com.v2ray.ang.ui.compose.AppTheme
import com.v2ray.ang.ui.compose.AppTopBar
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val PlaceholderIconSize = 80.dp
private val PlaceholderTitleSpacing = 12.dp
private val PlaceholderSummarySpacing = 4.dp

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onPlatformEvent: (ScannerEvent) -> Boolean
) {
    val onAction = remember(viewModel) { viewModel::onAction }
    val onEvent = remember(onPlatformEvent) {
        { event: BaseEvent -> event is ScannerEvent && onPlatformEvent(event) }
    }

    BackHandler { onAction(ScannerAction.Back) }

    BaseScreen(
        viewModel = viewModel,
        onEvent = onEvent,
        topBar = {
            val bar by rememberScannerBarState(viewModel)
            ScannerTopBar(bar = bar, onAction = onAction)
        }
    ) { state, _ ->
        ScannerCameraArea(
            scanning = state.scanning,
            torchEnabled = state.torchEnabled,
            onAction = onAction
        )
    }
}

/** The only three facts the top bar reads; everything else must not wake it up. */
@Immutable
private data class ScannerBarState(
    val scanning: Boolean = false,
    val hasTorch: Boolean = false,
    val torchEnabled: Boolean = false
)

private fun ScannerUiState.toBarState() = ScannerBarState(scanning, hasTorch, torchEnabled)

@Composable
private fun rememberScannerBarState(viewModel: ScannerViewModel): State<ScannerBarState> {
    val flow = remember(viewModel) {
        viewModel.uiState.map { it.toBarState() }.distinctUntilChanged()
    }
    val initial = remember(viewModel) { viewModel.uiState.value.toBarState() }
    return flow.collectAsStateWithLifecycle(initialValue = initial)
}

@Composable
private fun ScannerTopBar(
    bar: ScannerBarState,
    onAction: (ScannerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    AppTopBar(
        title = stringResource(R.string.menu_item_import_config_qrcode),
        onBackClick = { onAction(ScannerAction.Back) },
        modifier = modifier,
        actions = {
            IconButton(
                onClick = {
                    onAction(
                        if (bar.scanning) ScannerAction.StopScanClicked
                        else ScannerAction.StartScanClicked
                    )
                }
            ) {
                Icon(
                    painter = painterResource(
                        if (bar.scanning) R.drawable.ic_stop_24dp else R.drawable.ic_scan_24dp
                    ),
                    contentDescription = stringResource(
                        if (bar.scanning) R.string.acc_stop_scanner
                        else R.string.acc_start_scanner
                    )
                )
            }
            if (bar.scanning && bar.hasTorch) {
                IconButton(onClick = { onAction(ScannerAction.ToggleTorch) }) {
                    Icon(
                        painter = painterResource(
                            if (bar.torchEnabled) R.drawable.ic_flash_on_24dp
                            else R.drawable.ic_flash_off_24dp
                        ),
                        contentDescription = stringResource(
                            if (bar.torchEnabled) R.string.acc_turn_torch_off
                            else R.string.acc_turn_torch_on
                        )
                    )
                }
            }
            IconButton(onClick = { onAction(ScannerAction.PickImageClicked) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_image_24dp),
                    contentDescription = stringResource(R.string.acc_select_image)
                )
            }
        }
    )
}

@Composable
private fun ScannerCameraArea(
    scanning: Boolean,
    torchEnabled: Boolean,
    onAction: (ScannerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (scanning) {
            ScannerCameraPreview(
                torchEnabled = torchEnabled,
                onDecoded = { text -> onAction(ScannerAction.Decoded(text)) },
                onCameraReady = { hasTorch -> onAction(ScannerAction.CameraReady(hasTorch)) },
                onCameraFailed = { onAction(ScannerAction.CameraFailed) }
            )
            ScannerOverlay()
        } else {
            ScannerIdlePlaceholder(onStartClick = { onAction(ScannerAction.StartScanClicked) })
        }
    }
}

@Composable
private fun ScannerIdlePlaceholder(
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                onClickLabel = stringResource(R.string.acc_start_scanner),
                role = Role.Button,
                onClick = onStartClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_scan_24dp),
                contentDescription = null, // the clickable Box already carries the label
                modifier = Modifier.size(PlaceholderIconSize),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(PlaceholderTitleSpacing))
            Text(
                text = stringResource(R.string.menu_item_scan_qrcode),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(PlaceholderSummarySpacing))
            Text(
                text = stringResource(R.string.summary_scan_qrcode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ===== previews =====

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScannerIdlePlaceholderPreview() = AppTheme {
    ScannerIdlePlaceholder(onStartClick = {})
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScannerTopBarPreview() = AppTheme {
    Column {
        ScannerTopBar(bar = ScannerBarState(), onAction = {})
        ScannerTopBar(
            bar = ScannerBarState(scanning = true, hasTorch = true),
            onAction = {}
        )
        ScannerTopBar(
            bar = ScannerBarState(scanning = true, hasTorch = true, torchEnabled = true),
            onAction = {}
        )
    }
}
