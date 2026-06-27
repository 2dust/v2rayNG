package com.v2ray.ang.ui

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
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.enums.NetworkType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    config: ProfileItem,
    canDelete: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onConfigChange: (ProfileItem) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(config.configType.toString()) },
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
            content()
        }
    }
}

@Composable
fun CommonServerSettings(
    config: ProfileItem,
    onConfigChange: (ProfileItem) -> Unit
) {
    OutlinedTextField(
        value = config.remarks,
        onValueChange = { onConfigChange(config.copy(remarks = it)) },
        label = { Text(stringResource(R.string.server_lab_remarks)) },
        modifier = Modifier.fillMaxWidth()
    )
    
    OutlinedTextField(
        value = config.server.orEmpty(),
        onValueChange = { onConfigChange(config.copy(server = it)) },
        label = { Text(stringResource(R.string.server_lab_address)) },
        modifier = Modifier.fillMaxWidth()
    )

    if (config.configType != EConfigType.HYSTERIA2) {
        OutlinedTextField(
            value = config.serverPort.orEmpty(),
            onValueChange = { onConfigChange(config.copy(serverPort = it)) },
            label = { Text(stringResource(R.string.server_lab_port)) },
            modifier = Modifier.fillMaxWidth()
        )
    }

    OutlinedTextField(
        value = config.password.orEmpty(),
        onValueChange = { onConfigChange(config.copy(password = it)) },
        label = { 
            Text(stringResource(if (config.configType == EConfigType.TROJAN || config.configType == EConfigType.SHADOWSOCKS || config.configType == EConfigType.HYSTERIA2) 
                R.string.server_lab_id3 else R.string.server_lab_id)) 
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSpinner(
    label: String,
    options: Array<out String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
