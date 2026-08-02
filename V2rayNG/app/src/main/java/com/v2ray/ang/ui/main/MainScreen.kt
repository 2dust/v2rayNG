package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAddServer: () -> Unit,
    onScanQR: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onEditServer: (String, com.v2ray.ang.dto.entities.ProfileItem) -> Unit,
    onShareServer: (String, com.v2ray.ang.dto.entities.ProfileItem) -> Unit,
    onMoreServer: (String, com.v2ray.ang.dto.entities.ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val subscriptions = mainViewModel.getSubscriptions()
        .sortedBy { it.subscription.importDate ?: it.subscription.createdAt ?: 0L }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "VanguardClient") },
                actions = {
                    IconButton(onClick = onOpenSubscriptions) {
                        Icon(painterResource(id = R.drawable.ic_subscriptions_24dp), contentDescription = "Подписки")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(id = R.drawable.ic_settings_24dp), contentDescription = "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddServer) {
                Icon(painterResource(id = R.drawable.ic_add_24dp), contentDescription = "Добавить")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка подключения вверху по центру
            Button(
                onClick = { mainViewModel.onAction(MainAction.ToggleService) },
                modifier = Modifier.width(200.dp)
            ) {
                Text(text = if (uiState.isRunning) "Отключить" else "Подключить")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Список всех профилей (подписок) друг за другом по порядку импорта
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(subscriptions, key = { it.guid }) { subCache ->
                    val serversFlow = mainViewModel.serversForGroup(subCache.guid)
                    val servers by serversFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                    ProfileCard(
                        subscription = subCache,
                        servers = servers,
                        selectedGuid = uiState.selectedGuid,
                        onPingProfile = { guid -> mainViewModel.onAction(MainAction.TestProfileTcpPing(guid)) },
                        onUpdateSubscription = { mainViewModel.onAction(MainAction.UpdateSubscriptions) },
                        onSelectServer = { guid -> mainViewModel.onAction(MainAction.SelectServer(guid)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
