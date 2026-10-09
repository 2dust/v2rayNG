package com.v2ray.ang.ui.server

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.v2ray.ang.R
import com.v2ray.ang.core.AetherKeys
import com.v2ray.ang.core.AetherKeysSettings
import com.v2ray.ang.enums.AetherFingerprint
import com.v2ray.ang.enums.AetherKeyKind
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.PreferenceGroupHeader
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import kotlinx.coroutines.launch

/**
 * The page that gets new WARP keys, opened from the Aether editor. Its settings are its own: no profile's settings
 * reach them, and they reach no profile. Each key in use stays until its new key is ready to take its place.
 */
class ServerAetherKeysActivity : BaseComponentActivity() {

    private val viewModel: AetherKeysViewModel by viewModels {
        viewModelFactory {
            initializer { AetherKeysViewModel(application, AetherKeysRepository(application)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A session can start or stop while this page is away, e.g. from the notification.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshSession()
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val isCoreAvailable by viewModel.isCoreAvailable.collectAsStateWithLifecycle()
        val isRenewing by viewModel.isRenewing.collectAsStateWithLifecycle()
        val session by viewModel.session.collectAsStateWithLifecycle()
        val log by viewModel.log.collectAsStateWithLifecycle()
        val notice by viewModel.notice.collectAsStateWithLifecycle()
        val settings = viewModel.settings
        val scrollState = rememberScrollState()

        LaunchedEffect(notice) {
            when (val shown = notice) {
                AetherKeysNotice.Renewed -> toastSuccess(R.string.aether_log_key_renewed)
                is AetherKeysNotice.Invalid -> toastError(shown.message)
                null -> return@LaunchedEffect
            }
            viewModel.onNoticeShown()
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                AppTopBar(
                    title = stringResource(R.string.aether_action_get_keys),
                    onBackClick = { finish() }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
                    .verticalScroll(scrollState)
                    .verticalScrollbar(scrollState)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Shown once the settings are read, so that nothing typed before is overwritten by them.
                if (settings != null) {
                    KeysForm(settings, isCoreAvailable, isRenewing, session)
                }
                AetherLogPanel(entries = log, emptyText = R.string.aether_keys_log_empty)
                NavigationBarsSpacer()
            }
        }
    }

    @Composable
    private fun KeysForm(settings: AetherKeysSettings, isCoreAvailable: Boolean, isRenewing: Boolean, session: AetherSession?) {
        val enabled = !isRenewing
        PreferenceGroupHeader(title = stringResource(R.string.aether_keys_lab_kind))
        // The protocols by the names the editor gives them, matched by the word the core takes for each.
        val all = stringResource(R.string.aether_keys_kind_all)
        val protocolLabels = stringArrayResource(R.array.aether_protocol_entries)
        val protocolValues = stringArrayResource(R.array.aether_protocol_values)
        Column(modifier = Modifier.selectableGroup()) {
            AetherKeyKind.entries.forEach { kind ->
                val selected = settings.kind == kind
                // One node per row: the row selects, the button only shows.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { viewModel.setKind(kind) })
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = null, enabled = enabled)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (kind == AetherKeyKind.ALL) all else protocolLabels.getOrElse(protocolValues.indexOf(kind.type)) { kind.type },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        FormTextField(
            stringResource(R.string.aether_keys_lab_enroll_address),
            settings.enrollAddress,
            viewModel::setEnrollAddress,
            enabled = enabled,
            keyboardType = KeyboardType.Uri
        )
        SettingsSwitchItem(
            title = stringResource(R.string.aether_lab_ech),
            summary = stringResource(R.string.aether_keys_hint_ech),
            checked = settings.ech,
            onCheckedChange = viewModel::setEch,
            enabled = enabled
        )
        // Where the key comes from: the HTTPS record of the ECH domain, asked of the ECH DNS. Lists to pick from, as in
        // the Aether editor, which take any other value the core does as well.
        if (settings.ech) {
            FormDropdownField(
                label = stringResource(R.string.aether_lab_ech_dns),
                value = settings.echDns,
                options = stringArrayResource(R.array.aether_ech_dns_options).toList(),
                onValueChange = viewModel::setEchDns,
                editable = true,
                enabled = enabled,
                keyboardType = KeyboardType.Uri
            )
            FormDropdownField(
                label = stringResource(R.string.aether_lab_ech_domain),
                value = settings.echDomain,
                options = stringArrayResource(R.array.aether_ech_domain_options).toList(),
                onValueChange = viewModel::setEchDomain,
                editable = true,
                enabled = enabled,
                keyboardType = KeyboardType.Uri
            )
        }
        val fingerprintLabels = stringArrayResource(R.array.aether_fingerprint_entries)
        FormDropdownField(
            label = stringResource(R.string.aether_lab_fingerprint),
            value = fingerprintLabels.getOrElse(settings.fingerprint.ordinal) { settings.fingerprint.type },
            options = fingerprintLabels.toList(),
            onValueChange = { picked -> AetherFingerprint.entries.getOrNull(fingerprintLabels.indexOf(picked))?.let(viewModel::setFingerprint) },
            enabled = enabled
        )
        // Set on the exit-node, the Xray outbound by which what the core sends leaves, as the editor sets a profile's.
        FinalMaskField(
            stringResource(R.string.aether_lab_exit_final_mask),
            settings.finalMask,
            viewModel::setFinalMask,
            enabled = enabled
        )
        FormTextField(
            stringResource(R.string.aether_lab_exit_dial_mode),
            settings.dialMode,
            viewModel::setDialMode,
            enabled = enabled
        )
        // A session keeps the keys it uses; the keys of the other protocols can change under it.
        val blocked = AetherKeys.kindOf(AetherKeys.runArguments(settings))?.let { session?.usesKeysOf(it) } == true
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = viewModel::getKeys,
                enabled = isCoreAvailable && !isRenewing && !blocked
            ) {
                if (isRenewing) {
                    ProgressMark()
                }
                Text(stringResource(if (isRenewing) R.string.aether_action_renewing_key else R.string.aether_action_get_keys))
            }
            if (isRenewing) {
                TextButton(onClick = viewModel::cancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
        if (blocked) {
            Text(
                text = stringResource(R.string.aether_renew_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (!isCoreAvailable) {
            Text(
                text = stringResource(R.string.aether_unsupported_abi),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        // The command the core is started with, built from the settings above and open to a hand that needs an
        // option the page has no field for.
        val builtCommand = AetherKeys.builtCommand(settings)
        val customCommand = AetherKeys.isCustom(settings)
        FormTextField(
            stringResource(R.string.aether_lab_command),
            settings.command.ifBlank { builtCommand },
            viewModel::setCommand,
            enabled = enabled,
            maxLines = 8,
            supportingText = if (customCommand) stringResource(R.string.aether_command_custom) else null
        )
        if (customCommand) {
            TextButton(
                onClick = viewModel::useSettings,
                enabled = enabled,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.aether_action_use_settings))
            }
        }
    }
}
