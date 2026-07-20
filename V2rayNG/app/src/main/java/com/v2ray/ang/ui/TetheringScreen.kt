package com.v2ray.ang.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.compose.AppTopBar
import com.v2ray.ang.shizuku.ShizukuTetheringService

internal enum class ShizukuStatus(
    val statusRes: Int,
    val detailsRes: Int?,
    val canRequestPermission: Boolean = false,
) {
    CHECKING(R.string.shizuku_status_checking, R.string.shizuku_status_checking),
    NOT_INSTALLED(R.string.shizuku_status_not_installed, R.string.shizuku_status_open_manager),
    NOT_RUNNING(R.string.shizuku_status_not_running, R.string.shizuku_status_open_manager),
    UNSUPPORTED(R.string.shizuku_status_unsupported, R.string.shizuku_status_update_required),
    PERMISSION_REQUIRED(
        R.string.shizuku_status_permission_required,
        R.string.shizuku_status_permission_hint,
        true,
    ),
    PERMISSION_DENIED(
        R.string.shizuku_status_permission_denied,
        R.string.shizuku_status_permission_hint,
    ),
    READY(R.string.shizuku_status_ready, null),
}

internal enum class TetheringOperation {
    NONE,
    CONNECTING,
    CHECKING,
    STARTING_ROUTING,
    STOPPING_ROUTING,
    STARTING_HOTSPOT,
    STOPPING_HOTSPOT;

    val isLoading: Boolean
        get() = this == STARTING_ROUTING || this == STOPPING_ROUTING ||
            this == STARTING_HOTSPOT || this == STOPPING_HOTSPOT
}

internal data class TetheringUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.CHECKING,
    val operation: TetheringOperation = TetheringOperation.NONE,
    val routingState: Int = ShizukuTetheringService.ROUTING_STATE_DISABLED,
    val routingDetail: String = "",
    val activeTetheringTypes: Int = ShizukuTetheringService.TETHERING_TYPES_UNKNOWN,
    val coreRunning: Boolean = false,
)

internal val TetheringUiState.routingActive: Boolean
    get() = routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_HEV ||
        routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_NATIVE

internal val TetheringUiState.routingSessionEnabled: Boolean
    get() = routingActive || routingState == ShizukuTetheringService.ROUTING_STATE_WAITING

internal val TetheringUiState.hotspotEnabled: Boolean
    get() = activeTetheringTypes >= 0 &&
        activeTetheringTypes and (1 shl ShizukuTetheringService.TETHERING_TYPE_WIFI) != 0

internal val TetheringUiState.tetheringStateKnown: Boolean
    get() = activeTetheringTypes >= 0

private data class TetheringAction(
    val statusRes: Int,
    val buttonRes: Int,
    val enabled: Boolean,
)

private fun routingAction(
    state: TetheringUiState,
    serviceConnected: Boolean,
    platformSupported: Boolean,
): TetheringAction {
    val statusRes = if (!platformSupported) {
        R.string.shizuku_routing_status_unavailable
    } else when (state.operation) {
        TetheringOperation.CONNECTING -> R.string.shizuku_routing_status_connecting
        TetheringOperation.CHECKING -> R.string.shizuku_routing_status_checking
        TetheringOperation.STARTING_ROUTING -> R.string.shizuku_routing_status_starting
        TetheringOperation.STOPPING_ROUTING -> R.string.shizuku_routing_status_stopping
        else -> when {
            !serviceConnected -> R.string.shizuku_routing_status_unavailable
            state.routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_HEV ->
                R.string.shizuku_routing_status_hev
            state.routingState == ShizukuTetheringService.ROUTING_STATE_ACTIVE_NATIVE ->
                R.string.shizuku_routing_status_native
            state.routingState == ShizukuTetheringService.ROUTING_STATE_STARTING ->
                R.string.shizuku_routing_status_starting
            state.routingState == ShizukuTetheringService.ROUTING_STATE_STOPPING ->
                R.string.shizuku_routing_status_stopping
            state.routingState == ShizukuTetheringService.ROUTING_STATE_WAITING ->
                R.string.shizuku_routing_status_waiting
            state.routingState == ShizukuTetheringService.ROUTING_STATE_ERROR ->
                R.string.shizuku_routing_status_error
            state.coreRunning -> R.string.shizuku_routing_status_disabled
            else -> R.string.shizuku_routing_status_start_v2ray
        }
    }
    val enabled = platformSupported && serviceConnected &&
        state.operation == TetheringOperation.NONE &&
        when (state.routingState) {
            ShizukuTetheringService.ROUTING_STATE_ACTIVE_HEV,
            ShizukuTetheringService.ROUTING_STATE_ACTIVE_NATIVE,
            ShizukuTetheringService.ROUTING_STATE_WAITING -> true
            ShizukuTetheringService.ROUTING_STATE_ERROR,
            ShizukuTetheringService.ROUTING_STATE_DISABLED -> state.coreRunning
            else -> false
        }
    return TetheringAction(
        statusRes = statusRes,
        buttonRes = if (state.routingSessionEnabled) {
            R.string.shizuku_routing_disable
        } else {
            R.string.shizuku_routing_enable
        },
        enabled = enabled,
    )
}

private fun hotspotAction(
    state: TetheringUiState,
    serviceConnected: Boolean,
    platformSupported: Boolean,
): TetheringAction {
    val statusRes = if (!platformSupported) {
        R.string.shizuku_hotspot_status_unsupported
    } else when (state.operation) {
        TetheringOperation.CONNECTING -> R.string.shizuku_hotspot_status_connecting
        TetheringOperation.CHECKING -> R.string.shizuku_hotspot_status_checking
        TetheringOperation.STARTING_HOTSPOT -> R.string.shizuku_hotspot_status_starting
        TetheringOperation.STOPPING_HOTSPOT -> R.string.shizuku_hotspot_status_stopping
        else -> when {
            !serviceConnected -> R.string.shizuku_hotspot_status_unavailable
            state.hotspotEnabled && state.routingActive -> R.string.shizuku_hotspot_status_enabled
            state.hotspotEnabled &&
                state.routingState == ShizukuTetheringService.ROUTING_STATE_WAITING ->
                R.string.shizuku_hotspot_status_waiting
            state.hotspotEnabled -> R.string.shizuku_hotspot_status_enabled_direct
            state.tetheringStateKnown ->
                R.string.shizuku_hotspot_status_disabled
            else -> R.string.shizuku_hotspot_status_unavailable
        }
    }
    return TetheringAction(
        statusRes = statusRes,
        buttonRes = if (state.hotspotEnabled) {
            R.string.shizuku_hotspot_disable
        } else {
            R.string.shizuku_hotspot_enable
        },
        enabled = platformSupported && serviceConnected &&
            state.operation == TetheringOperation.NONE &&
            (state.hotspotEnabled ||
                state.tetheringStateKnown &&
                (state.routingActive || state.coreRunning)),
    )
}

@Composable
internal fun TetheringScreen(
    state: TetheringUiState,
    serviceConnected: Boolean,
    platformSupported: Boolean,
    onBackClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onToggleRouting: () -> Unit,
    onToggleHotspot: () -> Unit,
) {
    val routingAction = routingAction(state, serviceConnected, platformSupported)
    val hotspotAction = hotspotAction(state, serviceConnected, platformSupported)
    val routingSummary = stringResource(R.string.shizuku_routing_summary)
    val usbStatus = stringResource(R.string.shizuku_usb_status_enabled)
    val usbActive = state.activeTetheringTypes >= 0 &&
        state.activeTetheringTypes and (1 shl ShizukuTetheringService.TETHERING_TYPE_USB) != 0
    val routingDetails = buildString {
        append(state.routingDetail.ifBlank { routingSummary })
        if (usbActive) append("\n").append(usbStatus)
    }

    Scaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_tethering),
                onBackClick = onBackClick,
                isLoading = state.operation.isLoading,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            TetheringStatusSection(
                iconRes = R.drawable.ic_device_hub_24dp,
                title = stringResource(R.string.shizuku_status_title),
                status = stringResource(state.shizukuStatus.statusRes),
                details = state.shizukuStatus.detailsRes
                    ?.let { listOf(stringResource(it)) }
                    .orEmpty(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TetheringActionButton(
                    text = stringResource(R.string.shizuku_request_permission),
                    enabled = state.shizukuStatus.canRequestPermission &&
                        !state.operation.isLoading,
                    onClick = onRequestPermission,
                    modifier = Modifier.weight(1f),
                )
                TetheringActionButton(
                    text = stringResource(R.string.shizuku_refresh_permission),
                    enabled = !state.operation.isLoading,
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            TetheringStatusSection(
                iconRes = R.drawable.ic_routing_24dp,
                title = stringResource(R.string.shizuku_routing_title),
                status = stringResource(routingAction.statusRes),
                details = listOf(
                    routingDetails.ifBlank { stringResource(R.string.shizuku_routing_summary) },
                    stringResource(R.string.shizuku_routing_rules_disclaimer),
                ),
            )
            TetheringActionButton(
                text = stringResource(routingAction.buttonRes),
                enabled = routingAction.enabled,
                onClick = onToggleRouting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))
            TetheringStatusSection(
                iconRes = R.drawable.ic_wifi_tethering_24dp,
                title = stringResource(R.string.shizuku_hotspot_title),
                status = stringResource(hotspotAction.statusRes),
                details = listOf(stringResource(R.string.shizuku_hotspot_summary)),
            )
            TetheringActionButton(
                text = stringResource(hotspotAction.buttonRes),
                enabled = hotspotAction.enabled,
                onClick = onToggleHotspot,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TetheringStatusSection(
    iconRes: Int,
    title: String,
    status: String,
    details: List<String>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            details.filter { it.isNotBlank() }.forEach { detail ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TetheringActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
