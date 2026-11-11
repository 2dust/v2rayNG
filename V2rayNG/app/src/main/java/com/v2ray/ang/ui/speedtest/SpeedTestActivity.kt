package com.v2ray.ang.ui.speedtest

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.PreferenceGroupHeader
import com.v2ray.ang.ui.compose.RxGlassButton
import com.v2ray.ang.ui.compose.SettingsMenuItem

/**
 * Ranks the configs by what was actually measured: real ping for all of them, upload/download for
 * the connected one, and the best few for the network the phone is on.
 */
class SpeedTestActivity : BaseComponentActivity() {

    private val viewModel: SpeedTestViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()

        SpeedTestScreen(
            state = state,
            isLoading = isLoading || state.testing || state.throughputBusy,
            onBack = { finish() },
            onPingAll = viewModel::pingAll,
            onTestThroughput = viewModel::testThroughput
        )
    }
}

@Composable
fun SpeedTestScreen(
    state: SpeedTestViewModel.UiState,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPingAll: () -> Unit,
    onTestThroughput: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.speedtest_title),
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
            Text(
                text = stringResource(R.string.speedtest_network, state.operator),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RxGlassButton(onClick = onPingAll, enabled = !state.testing) {
                    Text(stringResource(R.string.speedtest_ping_all))
                }
                RxGlassButton(onClick = onTestThroughput, enabled = !state.throughputBusy) {
                    Text(stringResource(R.string.speedtest_speed))
                }
            }
            state.progress?.let { progress ->
                Text(
                    text = progress,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Text(
                text = stringResource(R.string.speedtest_speed_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            PreferenceGroupHeader(title = stringResource(R.string.speedtest_suggestions))
            if (state.suggestions.isEmpty()) {
                Text(
                    text = stringResource(R.string.speedtest_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                state.suggestions.forEach { row -> ConfigRow(row) }
            }

            PreferenceGroupHeader(title = stringResource(R.string.speedtest_all_configs))
            if (state.rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.speedtest_no_configs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                state.rows.forEach { row -> ConfigRow(row) }
            }

            Spacer(modifier = Modifier.height(24.dp))
            NavigationBarsSpacer()
        }
    }
}

@Composable
private fun ConfigRow(row: SpeedTestViewModel.Row) {
    val ping = if (row.pingMs > 0) {
        stringResource(R.string.speedtest_ping_value, row.pingMs)
    } else {
        stringResource(R.string.speedtest_not_measured)
    }
    val download = row.downloadMbps?.let { value ->
        stringResource(R.string.speedtest_mbps_down, formatMbps(value))
    } ?: stringResource(R.string.speedtest_no_speed)
    val upload = row.uploadMbps?.let { value ->
        stringResource(R.string.speedtest_mbps_up, formatMbps(value))
    } ?: ""

    SettingsMenuItem(
        title = row.name,
        subtitle = listOf(ping, download, upload).filter { it.isNotBlank() }.joinToString("  ·  "),
        onClick = {}
    )
}

/** One decimal is enough and keeps the row readable. */
private fun formatMbps(value: Double): String =
    String.format(java.util.Locale.US, "%.1f", value)
