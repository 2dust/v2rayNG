package com.v2ray.ang.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastSuccess
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubEditActivity : BaseActivity() {
    private val editSubId by lazy { intent.getStringExtra("subId").orEmpty() }
    private var subItemState = mutableStateOf(SubscriptionItem())
    private val profileSuggestions = mutableStateListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsChangeManager.makeSetupGroupTab()
        
        val subItem = MmkvManager.decodeSubscription(editSubId) ?: SubscriptionItem()
        subItemState.value = subItem

        profileSuggestions.addAll(SettingsManager.getProfileRemarks(
            excludeConfigTypes = setOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)
        ))

        setContent {
            MaterialTheme {
                SubEditScreen(
                    subItem = subItemState.value,
                    suggestions = profileSuggestions,
                    canDelete = editSubId.isNotEmpty(),
                    onBack = { finish() },
                    onDelete = { deleteServer() },
                    onSave = { saveServer() },
                    onSubItemChange = { subItemState.value = it }
                )
            }
        }
    }

    private fun saveServer() {
        val subItem = subItemState.value
        if (subItem.remarks.isEmpty()) {
            toast(R.string.sub_setting_remarks)
            return
        }
        if (subItem.url.isNotEmpty()) {
            if (!Utils.isValidUrl(subItem.url)) {
                toast(R.string.toast_invalid_url)
                return
            }
            if (!Utils.isValidSubUrl(subItem.url) && !subItem.allowInsecureUrl) {
                toast(R.string.toast_insecure_url_protocol)
                return
            }
        }

        MmkvManager.encodeSubscription(editSubId, subItem)
        SubscriptionUpdater.syncOne(subId = editSubId)
        toastSuccess(R.string.toast_success)
        finish()
    }

    private fun deleteServer() {
        if (editSubId.isNotEmpty()) {
            if (MmkvManager.decodeSettingsBool(AppConfig.PREF_CONFIRM_REMOVE)) {
                AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            SettingsManager.removeSubscriptionWithDefault(editSubId)
                            launch(Dispatchers.Main) { finish() }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    SettingsManager.removeSubscriptionWithDefault(editSubId)
                    launch(Dispatchers.Main) { finish() }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubEditScreen(
    subItem: SubscriptionItem,
    suggestions: List<String>,
    canDelete: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onSubItemChange: (SubscriptionItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_sub_setting)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(onClick = onSave) {
                        Icon(Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = subItem.remarks,
                onValueChange = { onSubItemChange(subItem.copy(remarks = it)) },
                label = { Text(stringResource(R.string.sub_setting_remarks)) },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = subItem.url,
                onValueChange = { onSubItemChange(subItem.copy(url = it)) },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = subItem.enabled, onCheckedChange = { onSubItemChange(subItem.copy(enabled = it)) })
                Text("Enabled")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = subItem.autoUpdate, onCheckedChange = { onSubItemChange(subItem.copy(autoUpdate = it)) })
                Text("Auto update")
            }

            OutlinedTextField(
                value = subItem.updateInterval.toString(),
                onValueChange = { val v = it.toLongOrNull() ?: 0L; onSubItemChange(subItem.copy(updateInterval = v)) },
                label = { Text("Update interval (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = subItem.autoUpdate
            )

            OutlinedTextField(
                value = subItem.userAgent.orEmpty(),
                onValueChange = { onSubItemChange(subItem.copy(userAgent = it)) },
                label = { Text("User Agent") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = subItem.filter.orEmpty(),
                onValueChange = { onSubItemChange(subItem.copy(filter = it)) },
                label = { Text("Filter (Regex)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = subItem.allowInsecureUrl, onCheckedChange = { onSubItemChange(subItem.copy(allowInsecureUrl = it)) })
                Text("Allow insecure URL")
            }
            
            ProfileRemarkSelector(
                label = "Pre Profile",
                selected = subItem.prevProfile.orEmpty(),
                suggestions = suggestions,
                onSelected = { onSubItemChange(subItem.copy(prevProfile = it)) }
            )

            ProfileRemarkSelector(
                label = "Next Profile",
                selected = subItem.nextProfile.orEmpty(),
                suggestions = suggestions,
                onSelected = { onSubItemChange(subItem.copy(nextProfile = it)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRemarkSelector(
    label: String,
    selected: String,
    suggestions: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = onSelected,
            label = { Text(label) },
            modifier = Modifier.menuAnchor(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onSelected(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}
