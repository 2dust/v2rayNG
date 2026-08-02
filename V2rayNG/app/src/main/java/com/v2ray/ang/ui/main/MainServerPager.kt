package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.colorConfigType
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
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
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
            
            // Кнопка Пинг профиля
            IconButton(onClick = { onPingProfile(subscription.guid) }, modifier = Modifier.size(36.dp)) {
                Icon(painterResource(id = android.R.drawable.ic_menu_send), contentDescription = "Пинг")
            }
            
            // Кнопка Обновить подписку
            IconButton(onClick = { onUpdateSubscription(subscription.guid) }, modifier = Modifier.size(36.dp)) {
                Icon(painterResource(id = android.R.drawable.ic_menu_revert), contentDescription = "Обновить подписку")
            }
        }
        
        // Дата окончания и потраченный / доступный трафик
        val expiry = subscription.subscription.expiryDate ?: "Без срока"
        val used = subscription.subscription.usedGb ?: "0 ГБ"
        val total = subscription.subscription.totalGb ?: "∞"
        
        Text(
            text = "До $expiry — $used / $total", 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Список серверов этого профиля ниже
        servers.forEach { serverCache ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectServer(serverCache.guid) }
                    .padding(vertical = 6.dp, horizontal = 4.dp)
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
            HorizontalDivider()
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
    // Получаем список всех подписок, отсортированных по дате импорта
    val subscriptions = mainViewModel.getSubscriptions()
        .sortedBy { it.subscription.importDate ?: it.subscription.createdAt ?: 0L }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primaryContainer)
        }
    }
}
