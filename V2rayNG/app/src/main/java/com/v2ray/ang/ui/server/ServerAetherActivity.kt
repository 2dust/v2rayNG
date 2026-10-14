package com.v2ray.ang.ui.server

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.FormTextField

class ServerAetherActivity : BaseComponentActivity() {
    private val viewModel: AetherEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initialize(intent.getStringExtra("guid").orEmpty(), intent.getStringExtra("subscriptionId").orEmpty())
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val settings = state.settings
        val editable = !state.loading && !state.saving
        LaunchedEffect(state.savedGuid) {
            state.savedGuid?.let { with(ProfileEditorResult) { finishSaved(it, restartService = false) } }
        }
        Scaffold { padding ->
            Column(
                modifier = Modifier.padding(padding).consumeWindowInsets(padding)
                    .verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.aether_title), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.aether_apply_notice), modifier = Modifier.padding(horizontal = 16.dp))
                if (state.loading) Text(stringResource(R.string.aether_loading), modifier = Modifier.padding(16.dp))
                FormTextField(stringResource(R.string.server_lab_remarks), state.remarks, viewModel::changeRemarks, enabled = editable)
                FormTextField(stringResource(R.string.aether_protocol), settings.protocol, { value -> viewModel.changeSettings { current -> current.copy(protocol = value) } }, enabled = editable, supportingText = stringResource(R.string.aether_protocol_values))
                FormTextField(stringResource(R.string.aether_carrier), settings.carrier, { value -> viewModel.changeSettings { current -> current.copy(carrier = value) } }, enabled = editable, supportingText = stringResource(R.string.aether_carrier_values))
                FormTextField(stringResource(R.string.aether_scan), settings.scanMode, { value -> viewModel.changeSettings { current -> current.copy(scanMode = value) } }, enabled = editable, supportingText = stringResource(R.string.aether_scan_values))
                FormTextField(stringResource(R.string.aether_ip), settings.ipVersion, { value -> viewModel.changeSettings { current -> current.copy(ipVersion = value) } }, enabled = editable, supportingText = stringResource(R.string.aether_ip_values))
                FormTextField(stringResource(R.string.aether_noise), settings.noise, { value -> viewModel.changeSettings { current -> current.copy(noise = value) } }, enabled = editable, supportingText = stringResource(R.string.aether_noise_values))
                FormTextField(stringResource(R.string.aether_endpoint), settings.endpoint, { value -> viewModel.changeSettings { current -> current.copy(endpoint = value) } }, enabled = editable)
                FormTextField(stringResource(R.string.aether_dns), settings.dns, { value -> viewModel.changeSettings { current -> current.copy(dns = value) } }, enabled = editable)
                Row(modifier = Modifier.fillMaxWidth().toggleable(settings.fragment, enabled = editable, role = Role.Switch,
                    onValueChange = { value -> viewModel.changeSettings { current -> current.copy(fragment = value) } }).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.aether_fragment))
                    Switch(checked = settings.fragment, onCheckedChange = null)
                }
                FormTextField(stringResource(R.string.aether_fragment_size), settings.fragmentSize, { value -> viewModel.changeSettings { current -> current.copy(fragmentSize = value) } }, enabled = editable)
                FormTextField(stringResource(R.string.aether_fragment_delay), settings.fragmentDelay, { value -> viewModel.changeSettings { current -> current.copy(fragmentDelay = value) } }, enabled = editable)
                FormTextField(stringResource(R.string.aether_socks_port), settings.socksPort, { value -> viewModel.changeSettings { current -> current.copy(socksPort = value) } }, enabled = editable)
                state.errorRes?.let { Text(stringResource(it), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { finish() }) { Text(stringResource(R.string.aether_cancel)) }
                    Button(onClick = viewModel::save, enabled = editable) { Text(stringResource(R.string.aether_save)) }
                }
            }
        }
    }
}
