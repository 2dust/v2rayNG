package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager

class ServerProxyChainActivity : BaseActivity() {
    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private var remarks by mutableStateOf("")
    private val chainMembers = mutableStateListOf<String>()
    private val allRemarks = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        loadAvailableRemarks()
        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.PROXYCHAIN)
        remarks = config.remarks
        val members = config.proxyChainProfiles?.split(",")?.filter { it.isNotEmpty() } ?: listOf("", "")
        chainMembers.addAll(members)

        setContent {
            MaterialTheme {
                ServerEditScreen(
                    config = ProfileItem.create(EConfigType.PROXYCHAIN).copy(remarks = remarks),
                    canDelete = editGuid.isNotEmpty() && !isRunning,
                    onBack = { finish() },
                    onSave = { saveServer() },
                    onDelete = { deleteServer() },
                    onConfigChange = { remarks = it.remarks }
                ) {
                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("Remarks") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Text("Proxy Chain Members", style = MaterialTheme.typography.titleMedium)
                    
                    chainMembers.forEachIndexed { index, member ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                ProfileRemarkSelector(
                                    label = "Member ${index + 1}",
                                    selected = member,
                                    suggestions = allRemarks,
                                    onSelected = { chainMembers[index] = it }
                                )
                            }
                            IconButton(onClick = { if (chainMembers.size > 2) chainMembers.removeAt(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    }
                    
                    Button(onClick = { chainMembers.add("") }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add Member")
                    }
                }
            }
        }
    }

    private fun loadAvailableRemarks() {
        val list = SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)
        )
        allRemarks.addAll(list)
    }

    private fun saveServer() {
        if (remarks.isEmpty()) {
            toast(R.string.server_lab_remarks)
            return
        }
        val members = chainMembers.filter { it.isNotEmpty() }
        if (members.size < 2) {
            toast(R.string.server_proxy_chain_members_insufficient)
            return
        }

        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.PROXYCHAIN)
        config.remarks = remarks
        config.proxyChainProfiles = members.joinToString(",")
        config.description = members.joinToString(" -> ")

        if (config.subscriptionId.isEmpty() && !subscriptionId.isNullOrEmpty()) {
            config.subscriptionId = subscriptionId.orEmpty()
        }

        MmkvManager.encodeServerConfig(editGuid, config)
        if (isRunning) SettingsChangeManager.makeRestartService()
        toastSuccess(R.string.toast_success)
        finish()
    }

    private fun deleteServer() {
        if (editGuid.isNotEmpty() && editGuid != MmkvManager.getSelectServer()) {
            AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    MmkvManager.removeServer(editGuid)
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
