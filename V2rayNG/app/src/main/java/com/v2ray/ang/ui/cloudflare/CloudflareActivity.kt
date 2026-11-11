package com.v2ray.ang.ui.cloudflare

import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.PreferenceGroupHeader
import com.v2ray.ang.ui.compose.RxGlassButton
import com.v2ray.ang.ui.compose.RxGlassPanel
import com.v2ray.ang.ui.compose.SettingsMenuItem
import com.v2ray.ang.ui.compose.SettingsSwitchItem

/**
 * Cloudflare tools: makes a blocked or slow Cloudflare-fronted config usable again.
 *
 * The screen only drives [CloudflareViewModel]; every change lands in the existing settings
 * (fragment/mux) or in the selected profile, so nothing here is a parallel configuration system.
 */
class CloudflareActivity : BaseComponentActivity() {

    private val viewModel: CloudflareViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.autoCheckIfNeeded()
        }

        CloudflareScreen(
            state = state,
            isLoading = isLoading || state.scanning,
            onBack = { finish() },
            onToggleAntiBlock = viewModel::setAntiBlock,
            onToggleAuto = viewModel::setAutoEnabled,
            onScan = viewModel::scan,
            onChoose = viewModel::choose,
            onApply = viewModel::applyChosen,
            onProbe = viewModel::probeSelected
        )
    }
}

@Composable
fun CloudflareScreen(
    state: CloudflareViewModel.UiState,
    isLoading: Boolean,
    onBack: () -> Unit,
    onToggleAntiBlock: (Boolean) -> Unit,
    onToggleAuto: (Boolean) -> Unit,
    onScan: () -> Unit,
    onChoose: (String) -> Unit,
    onApply: () -> Unit,
    onProbe: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.cloudflare_title),
                onBackClick = onBack,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            PreferenceGroupHeader(title = stringResource(R.string.cloudflare_active_config))
            ActiveConfigCard(state)

            PreferenceGroupHeader(title = stringResource(R.string.cloudflare_group_unblock))
            SettingsSwitchItem(
                title = stringResource(R.string.cloudflare_auto_title),
                summary = stringResource(R.string.cloudflare_auto_summary),
                checked = state.autoEnabled,
                onCheckedChange = onToggleAuto
            )
            when (state.autoOutcome) {
                CloudflareViewModel.AutoOutcome.Healthy -> AutoOutcomeText(R.string.cloudflare_auto_healthy)
                CloudflareViewModel.AutoOutcome.Unblocked -> AutoOutcomeText(R.string.cloudflare_auto_unblocked)
                CloudflareViewModel.AutoOutcome.SwitchedEdge -> AutoOutcomeText(R.string.cloudflare_auto_switched)
                CloudflareViewModel.AutoOutcome.None -> Unit
            }
            SettingsSwitchItem(
                title = stringResource(R.string.cloudflare_antibloc_title),
                summary = stringResource(R.string.cloudflare_antibloc_summary),
                checked = state.antiBlock,
                onCheckedChange = onToggleAntiBlock
            )

            PreferenceGroupHeader(title = stringResource(R.string.cloudflare_group_best_ip))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RxGlassButton(onClick = onScan, enabled = !state.scanning) {
                    Text(stringResource(R.string.cloudflare_scan))
                }
            }

            state.results.forEach { result ->
                SettingsMenuItem(
                    title = result.address,
                    subtitle = stringResource(R.string.cloudflare_latency, result.latencyMs),
                    onClick = { onChoose(result.address) }
                )
            }

            if (state.chosenAddress != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    RxGlassButton(onClick = onApply, accent = true) {
                        Text(stringResource(R.string.cloudflare_apply))
                    }
                }
            }

            PreferenceGroupHeader(title = stringResource(R.string.cloudflare_group_test))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                RxGlassButton(onClick = onProbe) {
                    Text(stringResource(R.string.cloudflare_test))
                }
            }
            if (state.probeFinished) {
                Text(
                    text = state.probeLatencyMs?.let {
                        stringResource(R.string.cloudflare_test_ok, it)
                    } ?: stringResource(R.string.cloudflare_test_failed),
                    color = if (state.probeLatencyMs != null) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            NavigationBarsSpacer()
        }
    }
}

@Composable
private fun AutoOutcomeText(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        color = MaterialTheme.colorScheme.tertiary,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun ActiveConfigCard(state: CloudflareViewModel.UiState) {
    RxGlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = state.selectedName ?: stringResource(R.string.cloudflare_no_config),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (state.selectedAddress != null) {
                Text(
                    text = state.selectedAddress,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            if (state.isCloudflare) {
                Text(
                    text = stringResource(R.string.cloudflare_detected),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
