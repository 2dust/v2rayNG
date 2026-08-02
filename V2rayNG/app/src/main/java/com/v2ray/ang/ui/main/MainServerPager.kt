package com.v2ray.ang.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed

@Composable
fun ProfileCard(
    subscription: SubscriptionCache,
    servers: List<ServersCache>,
    selectedGuid: String?,
    onPingProfile: (String) -> Unit,
    onUpdateSubscription: (String) -> Unit,
    onSelectServer: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Шапка профиля: Название + Кнопки Пинг и Обновить
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = subscription.subscription.remarks ?: "Без названия", 
                    modifier = Modifier.weight(1f), 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = { onPingProfile(subscription.guid) }, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(id = android.R.drawable.ic_menu_send), contentDescription = "Пинг")
                }
                
                IconButton(onClick = { onUpdateSubscription(subscription.guid) }, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(id = android.R.drawable.ic_menu_revert), contentDescription = "Обновить")
                }
            }
            
            // ВАЖНО: В оригинальном v2rayNG нет полей expiryDate, usedGb, totalGb!
            // Заглушки для компиляции:
            val expiry = "Неизвестно"
            val used = "0 ГБ"
            val total = "∞"
            
            Text(
                text = "До $expiry — $used / $total", 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Список серверов этого профиля
            servers.forEach { serverCache ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectServer(serverCache.guid) }
                        .padding(vertical = 6.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text = serverCache.profile.remarks ?: "", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = serverCache.profile.server ?: "", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    val delay = serverCache.testDelayMillis
                    val delayText = serverCache.testDelayString.ifEmpty { if (delay < 0) "таймаут" else "" }
                    val pingColor = when {
                        delay < 0L -> colorPingRed
                        delay <= 300L -> colorPing
                        else -> Color(0xFFFF9800)
                    }
                    
                    Text(text = delayText, style = MaterialTheme.typography.bodySmall, color = pingColor)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            }
        }
    }
}

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    doubleColumnDisplay: Boolean,
    confirmRemove: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, androidx.compose.foundation.lazy.LazyListState>,
    lazyGridStates: MutableMap<String, androidx.compose.foundation.lazy.grid.LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    contentPadding: PaddingValues
) {
    val subscriptions = mainViewModel.getSubscriptions()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subscriptions, key = { it.guid }) { subCache ->
            val serverFlow = remember(subCache.guid) {
                mainViewModel.serversForGroup(subCache.guid)
            }
            val servers by serverFlow.collectAsStateWithLifecycle(initialValue = emptyList())

            ProfileCard(
                subscription = subCache,
                servers = servers,
                selectedGuid = selectedGuid,
                onPingProfile = { guid -> mainViewModel.onAction(MainAction.TestProfileTcpPing(guid)) },
                onUpdateSubscription = { mainViewModel.onAction(MainAction.UpdateSubscriptions) },
                onSelectServer = onSelectServer
            )
        }
    }
}
