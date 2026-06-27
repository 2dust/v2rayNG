package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager

class ServerGroupActivity : BaseActivity() {
    private val editGuid by lazy { intent.getStringExtra("guid").orEmpty() }
    private val isRunning by lazy {
        intent.getBooleanExtra("isRunning", false)
                && editGuid.isNotEmpty()
                && editGuid == MmkvManager.getSelectServer()
    }
    private val subscriptionId by lazy { intent.getStringExtra("subscriptionId") }

    private var profileState = mutableStateOf(ProfileItem.create(EConfigType.POLICYGROUP))
    private val subDisplayList = mutableListOf<String>()
    private val subIds = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        populateSubscriptionData()
        val config = MmkvManager.decodeServerConfig(editGuid) ?: ProfileItem.create(EConfigType.POLICYGROUP)
        profileState.value = config

        setContent {
            MaterialTheme {
                val p = profileState.value
                val groupTypes = resources.getStringArray(R.array.policy_group_type)
                val selectedSubIndex = subIds.indexOf(p.policyGroupSubscriptionId ?: "").let { if (it >= 0) it else 0 }
                val selectedTypeIndex = p.policyGroupType?.toIntOrNull() ?: 0

                ServerEditScreen(
                    config = p,
                    canDelete = editGuid.isNotEmpty() && !isRunning,
                    onBack = { finish() },
                    onSave = { saveServer() },
                    onDelete = { deleteServer() },
                    onConfigChange = { profileState.value = it }
                ) {
                    OutlinedTextField(
                        value = p.remarks,
                        onValueChange = { profileState.value = p.copy(remarks = it) },
                        label = { Text("Remarks") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    ConfigSpinner(
                        label = "Type",
                        options = groupTypes,
                        selectedOption = groupTypes.getOrElse(selectedTypeIndex) { "Selector" },
                        onOptionSelected = { type -> 
                            val idx = groupTypes.indexOf(type)
                            profileState.value = p.copy(policyGroupType = idx.toString())
                        }
                    )
                    
                    ConfigSpinner(
                        label = "Subscription",
                        options = subDisplayList.toTypedArray(),
                        selectedOption = subDisplayList.getOrElse(selectedSubIndex) { "All" },
                        onOptionSelected = { subName ->
                            val idx = subDisplayList.indexOf(subName)
                            profileState.value = p.copy(policyGroupSubscriptionId = subIds[idx])
                        }
                    )
                    
                    OutlinedTextField(
                        value = p.policyGroupFilter.orEmpty(),
                        onValueChange = { profileState.value = p.copy(policyGroupFilter = it) },
                        label = { Text("Filter (Regex)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    private fun populateSubscriptionData() {
        val subs = MmkvManager.decodeSubscriptions()
        subDisplayList.add(getString(R.string.filter_config_all))
        subIds.add("")
        subs.forEach { sub ->
            subDisplayList.add(sub.subscription.remarks.ifBlank { sub.guid })
            subIds.add(sub.guid)
        }
    }

    private fun saveServer() {
        val config = profileState.value
        if (config.remarks.isEmpty()) {
            toast(R.string.server_lab_remarks)
            return
        }
        
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
