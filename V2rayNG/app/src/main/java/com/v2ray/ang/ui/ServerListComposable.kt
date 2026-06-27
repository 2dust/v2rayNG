package com.v2ray.ang.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.viewmodel.MainViewModel

@Composable
fun ServerListScreen(
    mainViewModel: MainViewModel,
    onSelect: (String) -> Unit,
    onMoreClick: (String, ProfileItem, Int) -> Unit
) {
    val servers by mainViewModel.serversCacheFlow.collectAsStateWithLifecycle()
    val selectedGuid by mainViewModel.selectedGuidFlow.collectAsStateWithLifecycle()
    val testResults by mainViewModel.testResultsFlow.collectAsStateWithLifecycle()

    if (servers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No servers found",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(servers, key = { it.guid }) { server ->
                ServerItem(
                    server = server,
                    isSelected = server.guid == selectedGuid,
                    onSelect = { onSelect(server.guid) },
                    onMoreClick = { onMoreClick(server.guid, server.profile, servers.indexOf(server)) },
                    subscriptionRemarks = getSubscriptionRemarks(server.profile, mainViewModel.subscriptionId),
                    testResult = testResults[server.guid].orEmpty()
                )
            }
            
            item {
                // Footer (item_recycler_footer.xml equivalent)
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun ServerItem(
    server: ServersCache,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMoreClick: () -> Unit,
    subscriptionRemarks: String,
    testResult: String
) {
    val profile = server.profile
    val guid = server.guid
    val isTimeout = testResult == "timeout"

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onSelect() },
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.outlinedCardElevation(
            defaultElevation = if (isSelected) 8.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.remarks,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = getAddress(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
                
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Protocol Label
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = getProtocolDescription(profile),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    
                    if (subscriptionRemarks.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = subscriptionRemarks,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = testResult,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isTimeout) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            
            IconButton(onClick = onMoreClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_more_vert_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Logic Migration
private fun getAddress(profile: ProfileItem): String {
    return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
}

private fun getSubscriptionRemarks(profile: ProfileItem, subscriptionId: String): String {
    val subRemarks =
        if (subscriptionId.isEmpty())
            MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
        else
            null
    return subRemarks?.toString() ?: ""
}

private fun getProtocolDescription(profile: ProfileItem): String {
    if (profile.configType.isComplexType()) {
        return profile.configType.name
    }

    val parts = mutableListOf<String>()
    parts.add(profile.configType.name)

    profile.network?.let { net ->
        if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) {
            parts.add(net)
        }
    }

    profile.security?.let { sec ->
        if (sec.isNotBlank()) {
            if (profile.insecure == true && sec.equals("tls", ignoreCase = true)) {
                parts.add("$sec insecure")
            } else {
                parts.add(sec)
            }
        }
    }

    return parts.joinToString(" / ")
}
