package com.v2ray.ang.ui.server

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.v2ray.ang.R
import com.v2ray.ang.core.AetherCore
import com.v2ray.ang.core.AetherCoreManager
import com.v2ray.ang.core.AetherExit
import com.v2ray.ang.core.AetherScanResult
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.AetherProtocol
import com.v2ray.ang.enums.AetherPsiphon
import com.v2ray.ang.enums.AetherPsiphonCdnSet
import com.v2ray.ang.enums.AetherPsiphonMode
import com.v2ray.ang.enums.AetherTor
import com.v2ray.ang.enums.AetherTorBridges
import com.v2ray.ang.enums.AetherTorRelays
import com.v2ray.ang.enums.AetherTransport
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.fmt.AetherFmt
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.compose.CollapsiblePreferenceGroupHeader
import com.v2ray.ang.ui.compose.FormDropdownField
import com.v2ray.ang.ui.compose.FormTextField
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import com.v2ray.ang.ui.compose.verticalScrollbar
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch
import java.util.Locale

class ServerAetherActivity : BaseServerActivity() {

    override val serverConfigType: EConfigType = EConfigType.AETHER

    private val viewModel: ServerAetherViewModel by viewModels {
        viewModelFactory {
            initializer { ServerAetherViewModel(application, AetherEditorRepository(application)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A session can start or stop while this screen is away, e.g. from the notification.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshSession()
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val uiState = rememberSaveable(saver = ServerUiState.Saver) {
            ServerUiState.from(initialConfig = initialConfig)
        }.apply {
            configType = serverConfigType
        }
        val isCoreAvailable by viewModel.isCoreAvailable.collectAsStateWithLifecycle()
        val isPsiphonAvailable by viewModel.isPsiphonAvailable.collectAsStateWithLifecycle()
        val psiphonRegions by viewModel.psiphonRegions.collectAsStateWithLifecycle()
        val isTorTransportsAvailable by viewModel.isTorTransportsAvailable.collectAsStateWithLifecycle()
        val scanState by viewModel.scanState.collectAsStateWithLifecycle()
        val session by viewModel.session.collectAsStateWithLifecycle()
        val log by viewModel.log.collectAsStateWithLifecycle()
        val listenPort by viewModel.listenPort.collectAsStateWithLifecycle()
        val keysCheck by viewModel.keysCheck.collectAsStateWithLifecycle()
        // Folded away unless one of its settings holds a value, so a profile that set one shows it at once.
        var showOther by rememberSaveable { mutableStateOf(uiState.hasOtherAetherSettings) }
        val isScanning = scanState == AetherScanState.Scanning
        val isBusy = isScanning
        // What Psiphon has learned is shared by every Aether profile, so a live session on any of them keeps it.
        // Only the daemon-side evidence counts: a running non-Aether profile leaves it open.
        val sessionLive = session != null

        val protocol = AetherProtocol.fromString(uiState.aetherProtocol)
        val psiphon = AetherPsiphon.fromString(uiState.aetherPsiphon)
        val psiphonMode = AetherPsiphonMode.fromString(uiState.aetherPsiphonMode)
        val tor = AetherTor.fromString(uiState.aetherTor)
        val torBridges = AetherTorBridges.fromString(uiState.aetherTorBridges)
        // With Psiphon or Tor alone there is no WARP tunnel, and nothing about one to set.
        val warpUsed = psiphon != AetherPsiphon.ONLY && tor != AetherTor.ONLY
        // Obfuscation shapes the UDP of WireGuard and HTTP/3 alone; MASQUE over HTTP/2, chosen or forced by a carrier
        // around the tunnel, takes none.
        val overHttp2 = AetherCoreManager.masqueOverHttp2(protocol, AetherTransport.fromString(uiState.aetherTransport), tor, psiphon)
        val usesHttp2 = protocol.overMasque &&
            AetherTransport.fromString(uiState.aetherTransport) == AetherTransport.HTTP2
        // A scan opens a second tunnel on this protocol's key; a live session on that key must not be disturbed.
        val scanBlocked = session?.disturbedByScanOf(protocol) == true

        LaunchedEffect(protocol) {
            viewModel.showIdentity(protocol)
        }

        LaunchedEffect(scanState) {
            when (val state = scanState) {
                is AetherScanState.Found -> {
                    applyScanResult(uiState, state.result)
                    toastSuccess(R.string.aether_scan_success)
                    viewModel.onScanHandled()
                }

                AetherScanState.NotFound -> {
                    toastError(R.string.aether_scan_failed)
                    viewModel.onScanHandled()
                }

                AetherScanState.Idle, AetherScanState.Scanning -> Unit
            }
        }

        LaunchedEffect(keysCheck) {
            if (keysCheck == AetherKeysCheck.SaveReady) {
                viewModel.onKeysCheckHandled()
                saveChecked(uiState)
            }
        }

        ServerEditorScaffold(
            title = serverConfigType.toString(),
            onSaveClick = { requestSave(uiState, listenPort) }
        ) {
            // The WARP keys are shared by every Aether profile and got on a page of their own, with settings of its own.
            // A scan holds the exit a run of that page dials out through, so the page opens once it has ended.
            OutlinedButton(
                onClick = { startActivity(Intent(this@ServerAetherActivity, ServerAetherKeysActivity::class.java)) },
                enabled = !isScanning,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.aether_action_renew_key))
            }
            FormTextField(
                stringResource(R.string.server_lab_remarks),
                uiState.remarks,
                { uiState.remarks = it }
            )
            if (warpUsed) {
                AetherDropdownField(
                    label = R.string.aether_lab_protocol,
                    value = uiState.aetherProtocol,
                    entries = R.array.aether_protocol_entries,
                    values = R.array.aether_protocol_values,
                    enabled = !isBusy,
                    onValueChange = { uiState.aetherProtocol = it }
                )
                if (protocol.overMasque) {
                    AetherDropdownField(
                        label = R.string.aether_lab_transport,
                        value = uiState.aetherTransport,
                        entries = R.array.aether_transport_entries,
                        values = R.array.aether_transport_values,
                        onValueChange = { uiState.aetherTransport = it }
                    )
                }
                if (usesHttp2) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.aether_lab_fragment),
                        checked = uiState.aetherFragment,
                        onCheckedChange = { uiState.aetherFragment = it }
                    )
                    if (uiState.aetherFragment) {
                        FormTextField(
                            stringResource(R.string.aether_lab_fragment_size),
                            uiState.aetherFragmentSize,
                            { uiState.aetherFragmentSize = it },
                            placeholder = stringResource(R.string.aether_hint_fragment_size)
                        )
                        FormTextField(
                            stringResource(R.string.aether_lab_fragment_delay),
                            uiState.aetherFragmentDelay,
                            { uiState.aetherFragmentDelay = it },
                            placeholder = stringResource(R.string.aether_hint_fragment_delay)
                        )
                    }
                }
                if (protocol.overMasque) {
                    SettingsSwitchItem(
                        title = stringResource(R.string.aether_lab_ech),
                        summary = stringResource(R.string.aether_hint_ech),
                        checked = uiState.aetherEch,
                        onCheckedChange = { uiState.aetherEch = it }
                    )
                    // Where the key comes from: the HTTPS record of the ECH domain, asked of the ECH DNS.
                    // Lists to pick from, which take any other value the core does as well.
                    if (uiState.aetherEch) {
                        FormDropdownField(
                            label = stringResource(R.string.aether_lab_ech_dns),
                            value = uiState.aetherEchDns,
                            options = stringArrayResource(R.array.aether_ech_dns_options).toList(),
                            onValueChange = { uiState.aetherEchDns = it },
                            editable = true,
                            keyboardType = KeyboardType.Uri
                        )
                        FormDropdownField(
                            label = stringResource(R.string.aether_lab_ech_domain),
                            value = uiState.aetherEchDomain,
                            options = stringArrayResource(R.array.aether_ech_domain_options).toList(),
                            onValueChange = { uiState.aetherEchDomain = it },
                            editable = true,
                            keyboardType = KeyboardType.Uri
                        )
                    }
                }
                AetherDropdownField(
                    label = R.string.aether_lab_scan_mode,
                    value = uiState.aetherScanMode,
                    entries = R.array.aether_scan_entries,
                    values = R.array.aether_scan_values,
                    onValueChange = { uiState.aetherScanMode = it }
                )
                if (!overHttp2) {
                    AetherDropdownField(
                        label = R.string.aether_lab_obfuscation,
                        value = uiState.aetherObfuscation,
                        entries = R.array.aether_obfuscation_entries,
                        values = R.array.aether_obfuscation_values,
                        onValueChange = { uiState.aetherObfuscation = it }
                    )
                }
                // The ClientHello of the MASQUE handshakes: over HTTP/3, which carries TLS 1.3 alone, only its GREASE shows.
                if (protocol.overMasque) {
                    AetherDropdownField(
                        label = R.string.aether_lab_fingerprint,
                        value = uiState.aetherFingerprint,
                        entries = R.array.aether_fingerprint_entries,
                        values = R.array.aether_fingerprint_values,
                        onValueChange = { uiState.aetherFingerprint = it }
                    )
                }
            }
            // Set on the exit-node, where what the core sends leaves Xray, as an ordinary profile sets them on its outbound.
            FinalMaskField(
                stringResource(R.string.aether_lab_exit_final_mask),
                uiState.finalMask,
                { uiState.finalMask = it }
            )
            FormTextField(
                stringResource(R.string.aether_lab_exit_dial_mode),
                uiState.dialMode,
                { uiState.dialMode = it }
            )
            AetherDropdownField(
                label = R.string.aether_lab_psiphon,
                value = uiState.aetherPsiphon,
                entries = R.array.aether_psiphon_entries,
                values = R.array.aether_psiphon_values,
                enabled = !isBusy,
                onValueChange = { uiState.aetherPsiphon = it }
            )
            if (psiphon != AetherPsiphon.OFF) {
                if (!isPsiphonAvailable) {
                    Text(
                        text = stringResource(R.string.aether_psiphon_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                AetherDropdownField(
                    label = R.string.aether_lab_psiphon_mode,
                    value = uiState.aetherPsiphonMode,
                    entries = R.array.aether_psiphon_mode_entries,
                    values = R.array.aether_psiphon_mode_values,
                    onValueChange = { uiState.aetherPsiphonMode = it }
                )
                // The CDN lists feed the fronted transports alone, which the direct shape never uses; the
                // server names count only beside an IP list of one's own, since the built-in list comes whole.
                if (psiphonMode != AetherPsiphonMode.DIRECT) {
                    FormTextField(
                        stringResource(R.string.aether_lab_psiphon_cdn_ips),
                        uiState.aetherPsiphonCdnIps,
                        { uiState.aetherPsiphonCdnIps = it },
                        placeholder = stringResource(R.string.aether_hint_psiphon_list)
                    )
                    if (uiState.aetherPsiphonCdnIps.isNotBlank()) {
                        FormTextField(
                            stringResource(R.string.aether_lab_psiphon_cdn_sni),
                            uiState.aetherPsiphonCdnSni,
                            { uiState.aetherPsiphonCdnSni = it },
                            placeholder = stringResource(R.string.aether_hint_psiphon_list)
                        )
                    }
                    // Which of the edge lists built into Psiphon the fronting scan tries. Nine of them, so they stay
                    // folded behind a line that names the choice, and open by themselves only when a choice was made.
                    val cdnSetLabels = stringArrayResource(R.array.aether_psiphon_cdn_set_entries)
                    val chosenSets = uiState.aetherPsiphonCdnSetChoice
                    var showCdnSets by rememberSaveable { mutableStateOf(chosenSets.isNotEmpty()) }
                    CollapsiblePreferenceGroupHeader(
                        title = stringResource(R.string.aether_lab_psiphon_cdn_sets),
                        expanded = showCdnSets,
                        onExpandedChange = { showCdnSets = it }
                    )
                    Text(
                        text = if (chosenSets.isEmpty()) {
                            stringResource(R.string.aether_psiphon_cdn_sets_all)
                        } else {
                            AetherPsiphonCdnSet.entries.filter { it in chosenSets }.joinToString(", ") { set -> cdnSetLabels.getOrElse(set.ordinal) { _ -> set.type } }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    if (showCdnSets) {
                        Text(
                            text = stringResource(R.string.aether_hint_psiphon_cdn_sets),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
                        )
                        AetherPsiphonCdnSet.entries.forEach { set ->
                            val chosen = set in chosenSets
                            // One node per row: the row toggles, the box only shows.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(value = chosen, role = Role.Checkbox, onValueChange = { uiState.setPsiphonCdnSet(set, it) })
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = chosen, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = cdnSetLabels.getOrElse(set.ordinal) { _ -> set.type }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                AetherRegionField(
                    value = uiState.aetherPsiphonRegion,
                    regions = psiphonRegions,
                    onValueChange = { uiState.aetherPsiphonRegion = it }
                )
                SettingsSwitchItem(
                    title = stringResource(R.string.aether_lab_psiphon_bundled_list),
                    summary = stringResource(R.string.aether_hint_psiphon_bundled_list),
                    checked = uiState.aetherPsiphonBundledList,
                    onCheckedChange = { uiState.aetherPsiphonBundledList = it }
                )
                // What Psiphon has learned is shared by every profile, like the WARP key, and goes only while no session runs on it.
                OutlinedButton(
                    onClick = viewModel::clearPsiphonData,
                    enabled = isCoreAvailable && !isBusy && !sessionLive,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.aether_action_clear_psiphon))
                }
                Text(
                    text = stringResource(R.string.aether_hint_clear_psiphon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            AetherDropdownField(
                label = R.string.aether_lab_tor,
                value = uiState.aetherTor,
                entries = R.array.aether_tor_entries,
                values = R.array.aether_tor_values,
                enabled = !isBusy,
                onValueChange = { uiState.aetherTor = it }
            )
            if (tor != AetherTor.OFF) {
                // Inside the tunnel Tor is never blocked and asks for no bridges unless told to; around it or
                // alone it has to reach Tor first, and where Tor is blocked that takes the transport program.
                val bridgesUsed = torBridges != AetherTorBridges.NEVER && !(tor == AetherTor.CHAIN && torBridges == AetherTorBridges.AUTO)
                if (bridgesUsed && !isTorTransportsAvailable) {
                    Text(
                        text = stringResource(R.string.aether_tor_transports_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                AetherDropdownField(
                    label = R.string.aether_lab_tor_bridges,
                    value = uiState.aetherTorBridges,
                    entries = R.array.aether_tor_bridges_entries,
                    values = R.array.aether_tor_bridges_values,
                    onValueChange = { uiState.aetherTorBridges = it }
                )
                if (torBridges == AetherTorBridges.AUTO || torBridges == AetherTorBridges.FIRST) {
                    AetherDropdownField(
                        label = R.string.aether_lab_tor_relays,
                        value = uiState.aetherTorRelays,
                        entries = R.array.aether_tor_relays_entries,
                        values = R.array.aether_tor_relays_values,
                        onValueChange = { uiState.aetherTorRelays = it }
                    )
                }
                if (torBridges == AetherTorBridges.OWN) {
                    FormTextField(
                        stringResource(R.string.aether_lab_tor_bridge_lines),
                        uiState.aetherTorBridgeLines,
                        { uiState.aetherTorBridgeLines = it },
                        placeholder = stringResource(R.string.aether_hint_tor_bridge_lines),
                        maxLines = 6
                    )
                }
            }
            if (warpUsed) {
                if (protocol.twoHops) {
                    FormTextField(
                        stringResource(R.string.aether_lab_wiw_outer),
                        uiState.aetherWiwOuter,
                        { uiState.aetherWiwOuter = it },
                        placeholder = stringResource(R.string.aether_hint_endpoint)
                    )
                    FormTextField(
                        stringResource(R.string.aether_lab_wiw_inner),
                        uiState.aetherWiwInner,
                        { uiState.aetherWiwInner = it },
                        placeholder = stringResource(R.string.aether_hint_endpoint)
                    )
                } else {
                    FormTextField(
                        stringResource(R.string.server_lab_address),
                        uiState.address,
                        { uiState.address = it },
                        placeholder = stringResource(R.string.aether_hint_endpoint)
                    )
                    FormTextField(
                        stringResource(R.string.server_lab_port),
                        uiState.port,
                        { uiState.port = it },
                        keyboardType = KeyboardType.Number
                    )
                }
                AetherDropdownField(
                    label = R.string.aether_lab_ip_version,
                    value = uiState.aetherIpVersion,
                    entries = R.array.aether_ip_entries,
                    values = R.array.aether_ip_values,
                    onValueChange = { uiState.aetherIpVersion = it }
                )
            }
            CollapsiblePreferenceGroupHeader(
                title = stringResource(R.string.aether_lab_other_settings),
                expanded = showOther,
                onExpandedChange = { showOther = it }
            )
            if (showOther) {
                if (warpUsed) {
                    FormTextField(
                        stringResource(R.string.aether_lab_dns),
                        uiState.aetherDns,
                        { uiState.aetherDns = it },
                        placeholder = stringResource(R.string.aether_hint_dns)
                    )
                    FormTextField(
                        stringResource(R.string.aether_lab_exit_loc),
                        uiState.aetherExitLoc,
                        { uiState.aetherExitLoc = it },
                        placeholder = stringResource(R.string.aether_hint_exit_loc)
                    )
                }
                CommonTargetStrategyField(uiState)
            }
            // After every setting it runs on, the DNS and the exit rule of Other settings included.
            if (warpUsed) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.scan(uiState.toProfileItem(initialConfig, listenPort)) },
                        enabled = isCoreAvailable && !isBusy && !scanBlocked
                    ) {
                        if (isScanning) {
                            ProgressMark()
                        }
                        Text(stringResource(if (isScanning) R.string.aether_action_scanning else R.string.aether_action_scan))
                    }
                    if (isScanning) {
                        TextButton(onClick = viewModel::cancelScan) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
                if (scanBlocked) {
                    Text(
                        text = stringResource(R.string.aether_scan_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            if (!isCoreAvailable) {
                Text(
                    text = stringResource(R.string.aether_unsupported_abi),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            // The command the core is started with, built from the settings above and open to a hand
            // that needs an option the settings have no field for.
            val builtCommand = AetherCore.of(uiState.toProfileItem(initialConfig, listenPort).copy(aetherCommand = null), listenPort).command
            val customCommand = uiState.aetherCommand.isNotBlank() && uiState.aetherCommand.trim() != builtCommand
            FormTextField(
                stringResource(R.string.aether_lab_command),
                uiState.aetherCommand.ifBlank { builtCommand },
                { uiState.aetherCommand = it },
                maxLines = 8,
                supportingText = if (customCommand) stringResource(R.string.aether_command_custom) else null
            )
            if (customCommand) {
                TextButton(
                    onClick = { uiState.aetherCommand = "" },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(stringResource(R.string.aether_action_use_settings))
                }
            }
            AetherLogPanel(entries = log)
        }

        // A key the profile, or its scan, needs is missing: get it first on the WARP keys page, or go on, and the core
        // registers what it lacks on its own.
        (keysCheck as? AetherKeysCheck.Missing)?.let { missing ->
            AetherKeysMissingDialog(
                anywayText = stringResource(if (missing.scan) R.string.aether_action_scan_anyway else R.string.aether_action_save_anyway),
                onGetKeys = {
                    viewModel.onKeysCheckHandled()
                    startActivity(Intent(this@ServerAetherActivity, ServerAetherKeysActivity::class.java))
                },
                onAnyway = {
                    viewModel.onKeysCheckHandled()
                    if (missing.scan) viewModel.scan(uiState.toProfileItem(initialConfig, listenPort), anyway = true) else saveChecked(uiState)
                },
                onDismiss = viewModel::onKeysCheckHandled
            )
        }
    }

    /**
     * Saves the profile once it passes the editor's checks and the WARP keys it needs are there: the screen saves on
     * [AetherKeysCheck.SaveReady], and asks first when a key is missing. Once a save has closed the editor, a tap on
     * Save it still takes does nothing.
     */
    private fun requestSave(state: ServerUiState, listenPort: Int) {
        if (isFinishing) return
        if (!validateBasicConfig(state)) return
        val config = state.toProfileItem(initialConfig, listenPort)
        if (!validateCommonConfig(state, config)) return
        if (!validateProtocolConfig(config)) return
        viewModel.checkKeysBeforeSave(config)
    }

    /**
     * Saves the profile after the check of its keys, unless a save has closed the editor already: the check of a
     * second tap on Save, made while the first one was checked, can end after that save, and would save a new
     * profile twice.
     */
    private fun saveChecked(state: ServerUiState) {
        if (!isFinishing) saveServer(state)
    }

    override fun validateBasicConfig(state: ServerUiState): Boolean {
        if (state.remarks.isBlank()) {
            toast(R.string.server_lab_remarks)
            return false
        }
        return true
    }

    // The finalMask of an Aether profile is that of its exit-node: a bad one is named by its own label and checked as
    // on the WARP keys page, before the check every profile has would name it the outbound's.
    override fun validateCommonConfig(state: ServerUiState, config: ProfileItem): Boolean {
        if (!AetherExit.takesFinalMask(config.finalMask)) {
            toast(R.string.aether_lab_exit_final_mask)
            return false
        }
        return super.validateCommonConfig(state, config)
    }

    override fun validateProtocolConfig(config: ProfileItem): Boolean {
        // The core cannot listen where the local proxy of the app does, nor where the inbound it dials out
        // through does; Xray would get the port first.
        val takenPorts = SettingsManager.getLocalProxyPorts() + AetherCoreManager.secondarySocksPort
        val problem = AetherFmt.normalize(config, takenPorts) ?: return true
        toast(
            when (problem) {
                AetherFmt.Problem.INVALID_PEER -> R.string.aether_invalid_endpoint
                AetherFmt.Problem.INVALID_HOP -> R.string.aether_invalid_hop
                AetherFmt.Problem.SHARED_HOP -> R.string.aether_same_hop
                AetherFmt.Problem.INVALID_FRAGMENT -> R.string.aether_invalid_fragment
                AetherFmt.Problem.INVALID_DNS -> R.string.aether_invalid_dns
                AetherFmt.Problem.INVALID_EXIT_LOC -> R.string.aether_invalid_exit_loc
                AetherFmt.Problem.INVALID_ECH_DNS -> R.string.aether_invalid_ech_dns
                AetherFmt.Problem.INVALID_ECH_DOMAIN -> R.string.aether_invalid_ech_domain
                AetherFmt.Problem.LISTEN_PORT_TAKEN -> R.string.aether_listen_port_taken
                AetherFmt.Problem.PSIPHON_NEEDS_MASQUE -> R.string.aether_psiphon_needs_masque
                AetherFmt.Problem.NEXT_PORT_TAKEN -> R.string.aether_next_port_taken
                AetherFmt.Problem.TOR_NEEDS_MASQUE -> R.string.aether_tor_needs_masque
                AetherFmt.Problem.TOR_PSIPHON_CONFLICT -> R.string.aether_tor_psiphon_conflict
                AetherFmt.Problem.TOR_BRIDGES_MISSING -> R.string.aether_tor_bridges_missing
                AetherFmt.Problem.INVALID_COMMAND -> R.string.aether_invalid_command
            }
        )
        return false
    }

    private fun applyScanResult(state: ServerUiState, result: AetherScanResult) {
        if (AetherProtocol.fromString(state.aetherProtocol).twoHops) {
            state.aetherWiwOuter = result.endpoint.toString()
            state.aetherWiwInner = result.innerHop?.toString().orEmpty()
        } else {
            state.address = result.endpoint.host
            state.port = result.endpoint.port.toString()
        }
    }
}

@Composable
private fun AetherDropdownField(
    @StringRes label: Int,
    value: String,
    @ArrayRes entries: Int,
    @ArrayRes values: Int,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    val labels = stringArrayResource(entries)
    val options = stringArrayResource(values)
    FormDropdownField(
        label = stringResource(label),
        value = labels.getOrElse(options.indexOf(value).coerceAtLeast(0)) { "" },
        options = labels.toList(),
        onValueChange = { picked ->
            val index = labels.indexOf(picked)
            if (index >= 0) onValueChange(options[index])
        },
        enabled = enabled
    )
}

/**
 * The exit country Psiphon is asked for: any, or one of those the app's server list offers, named
 * in the app's language. A country the profile holds that the list does not offer stays on offer
 * too, so that a shared profile keeps its choice.
 */
@Composable
private fun AetherRegionField(value: String, regions: List<String>, onValueChange: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val any = stringResource(R.string.aether_psiphon_region_any)
    val current = value.trim().uppercase(Locale.ROOT)
    fun nameOf(code: String): String =
        runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(locale) }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
    val codes = (regions + listOfNotNull(current.takeIf { it.isNotEmpty() })).distinct().sortedBy { nameOf(it) }
    val labels = codes.map { code -> "${nameOf(code)} ($code)" }
    FormDropdownField(
        label = stringResource(R.string.aether_lab_psiphon_region),
        value = codes.indexOf(current).takeIf { it >= 0 }?.let { labels[it] } ?: any,
        options = listOf(any) + labels,
        onValueChange = { picked -> onValueChange(if (picked == any) "" else codes.getOrNull(labels.indexOf(picked)).orEmpty()) }
    )
}

/**
 * Says that a WARP key is missing and offers to get it first, on the WARP keys page, or to go on as [anywayText] says.
 * Dismissed, it does neither.
 */
@Composable
private fun AetherKeysMissingDialog(anywayText: String, onGetKeys: () -> Unit, onAnyway: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                text = stringResource(R.string.aether_keys_missing),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onAnyway) {
                Text(anywayText)
            }
        },
        dismissButton = {
            TextButton(onClick = onGetKeys) {
                Text(stringResource(R.string.aether_action_renew_key))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
internal fun ProgressMark() {
    CircularProgressIndicator(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(18.dp),
        strokeWidth = 2.dp
    )
}

/** The log of an Aether page: [entries], newest last, or [emptyText] while there are none, with a button that copies them. */
@Composable
internal fun AetherLogPanel(entries: List<AetherLogEntry>, @StringRes emptyText: Int = R.string.aether_log_empty) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(entries.lastOrNull()?.id) {
        if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.aether_log_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    Utils.setClipboard(context, entries.joinToString("\n") { it.text.resolve(context) })
                    context.toastSuccess(R.string.toast_success)
                },
                enabled = entries.isNotEmpty()
            ) {
                Text(stringResource(R.string.logcat_copy))
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(emptyText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollbar(listState),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(items = entries, key = { it.id }) { entry ->
                        Text(
                            text = entry.text.asString(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = logColor(entry)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AetherLogText.asString(): String = when (this) {
    is AetherLogText.Raw -> value
    is AetherLogText.Resource -> stringResource(id, *args.toTypedArray())
}

private fun AetherLogText.resolve(context: Context): String = when (this) {
    is AetherLogText.Raw -> value
    is AetherLogText.Resource -> context.getString(id, *args.toTypedArray())
}

@Composable
private fun logColor(entry: AetherLogEntry): Color = when {
    entry.priority >= Log.ERROR -> MaterialTheme.colorScheme.error
    entry.priority == Log.WARN -> MaterialTheme.colorScheme.tertiary
    entry.text is AetherLogText.Resource -> MaterialTheme.colorScheme.onSurface
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
