package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.DEFAULT_PORT
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ServerActivity : BaseActivity() {

    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val createConfigType by lazy {
        EConfigType.fromInt(intent.getIntExtra("createConfigType", EConfigType.VMESS.value))
            ?: EConfigType.VMESS
    }
    private val subscriptionId by lazy {
        intent.getStringExtra("subscriptionId")
    }

    private var profileState = mutableStateOf(ProfileItem.create(createConfigType))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(createConfigType)
        if (config.serverPort.isNullOrEmpty()) config.serverPort = DEFAULT_PORT.toString()
        profileState.value = config

        setContent {
            MaterialTheme {
                val p = profileState.value
                ServerEditScreen(
                    config = p,
                    canDelete = editGuid.isNotEmpty() && !isRunning,
                    onBack = { finish() },
                    onSave = { saveServer() },
                    onDelete = { deleteServer() },
                    onConfigChange = { profileState.value = it }
                ) {
                    CommonServerSettings(config = p, onConfigChange = { profileState.value = it })
                    
                    // Protocol Specific Settings
                    ProtocolSpecificSettings(config = p, onConfigChange = { profileState.value = it })
                    
                    // Transport Settings
                    TransportSettings(config = p, onConfigChange = { profileState.value = it })
                    
                    // TLS Settings
                    TlsSettings(config = p, onConfigChange = { profileState.value = it })
                }
            }
        }
    }

    @Composable
    fun ProtocolSpecificSettings(config: ProfileItem, onConfigChange: (ProfileItem) -> Unit) {
        val securitys = resources.getStringArray(R.array.securitys)
        val ssSecuritys = resources.getStringArray(R.array.ss_securitys)
        val flows = resources.getStringArray(R.array.flows)

        when (config.configType) {
            EConfigType.VMESS -> {
                ConfigSpinner(
                    label = "Security",
                    options = securitys,
                    selectedOption = config.method ?: securitys[0],
                    onOptionSelected = { onConfigChange(config.copy(method = it)) }
                )
            }
            EConfigType.SHADOWSOCKS -> {
                ConfigSpinner(
                    label = "Security",
                    options = ssSecuritys,
                    selectedOption = config.method ?: ssSecuritys[0],
                    onOptionSelected = { onConfigChange(config.copy(method = it)) }
                )
            }
            EConfigType.VLESS -> {
                OutlinedTextField(
                    value = config.method.orEmpty(),
                    onValueChange = { onConfigChange(config.copy(method = it)) },
                    label = { Text("Encryption") },
                    modifier = Modifier.fillMaxWidth()
                )
                ConfigSpinner(
                    label = "Flow",
                    options = flows,
                    selectedOption = config.flow ?: flows[0],
                    onOptionSelected = { onConfigChange(config.copy(flow = it)) }
                )
            }
            EConfigType.SOCKS, EConfigType.HTTP -> {
                OutlinedTextField(
                    value = config.username.orEmpty(),
                    onValueChange = { onConfigChange(config.copy(username = it)) },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            EConfigType.HYSTERIA2 -> {
                OutlinedTextField(value = config.obfsPassword.orEmpty(), onValueChange = { onConfigChange(config.copy(obfsPassword = it)) }, label = { Text("Obfs Password") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = config.portHopping.orEmpty(), onValueChange = { onConfigChange(config.copy(portHopping = it)) }, label = { Text("Port Hopping") }, modifier = Modifier.fillMaxWidth())
            }
            else -> {}
        }
    }

    @Composable
    fun TransportSettings(config: ProfileItem, onConfigChange: (ProfileItem) -> Unit) {
        val networks = resources.getStringArray(R.array.networks)
        val tcpTypes = resources.getStringArray(R.array.header_type_tcp)
        val kcpTypes = resources.getStringArray(R.array.header_type_kcp_and_quic)
        val grpcModes = resources.getStringArray(R.array.mode_type_grpc)

        ConfigSpinner(
            label = "Network",
            options = networks,
            selectedOption = config.network ?: networks[0],
            onOptionSelected = { onConfigChange(config.copy(network = it)) }
        )

        val transportTypes = when (config.network) {
            NetworkType.TCP.type -> tcpTypes
            NetworkType.KCP.type -> kcpTypes
            NetworkType.GRPC.type -> grpcModes
            else -> emptyArray()
        }

        if (transportTypes.isNotEmpty()) {
            ConfigSpinner(
                label = "Header Type / Mode",
                options = transportTypes,
                selectedOption = config.headerType ?: transportTypes[0],
                onOptionSelected = { onConfigChange(config.copy(headerType = it)) }
            )
        }

        OutlinedTextField(
            value = config.host.orEmpty(),
            onValueChange = { onConfigChange(config.copy(host = it)) },
            label = { Text("Request Host / Authority") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = config.path.orEmpty(),
            onValueChange = { onConfigChange(config.copy(path = it)) },
            label = { Text("Path / Service Name") },
            modifier = Modifier.fillMaxWidth()
        )
    }

    @Composable
    fun TlsSettings(config: ProfileItem, onConfigChange: (ProfileItem) -> Unit) {
        val streamSecuritys = resources.getStringArray(R.array.streamsecurityxs)
        val uTlsItems = resources.getStringArray(R.array.streamsecurity_utls)

        ConfigSpinner(
            label = "Stream Security",
            options = streamSecuritys,
            selectedOption = config.security ?: streamSecuritys[0],
            onOptionSelected = { onConfigChange(config.copy(security = it)) }
        )

        if (!config.security.isNullOrBlank()) {
            OutlinedTextField(
                value = config.sni.orEmpty(),
                onValueChange = { onConfigChange(config.copy(sni = it)) },
                label = { Text("SNI") },
                modifier = Modifier.fillMaxWidth()
            )
            ConfigSpinner(
                label = "Fingerprint (uTLS)",
                options = uTlsItems,
                selectedOption = config.fingerPrint ?: uTlsItems[0],
                onOptionSelected = { onConfigChange(config.copy(fingerPrint = it)) }
            )
        }
    }

    private fun saveServer() {
        val config = profileState.value
        if (config.remarks.isEmpty()) {
            toast(R.string.server_lab_remarks)
            return
        }
        if (config.server.isNullOrEmpty()) {
            toast(R.string.server_lab_address)
            return
        }
        
        config.description = AngConfigManager.generateDescription(config)
        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }
        
        MmkvManager.encodeServerConfig(editGuid, config)
        if (isRunning) {
            SettingsChangeManager.makeRestartService()
        }
        toastSuccess(R.string.toast_success)
        finish()
    }

    private fun deleteServer() {
        if (editGuid.isNotEmpty() && editGuid != MmkvManager.getSelectServer()) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        MmkvManager.removeServer(editGuid)
                        finish()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                MmkvManager.removeServer(editGuid)
                finish()
            }
        }
    }
}
