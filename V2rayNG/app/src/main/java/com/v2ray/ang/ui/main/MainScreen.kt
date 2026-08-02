package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    // Получаем подписки (без сортировки по несуществующей дате, чтобы не было ошибок)
    val subscriptions = mainViewModel.getSubscriptions()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "VanguardClient") },
                actions = {
                    IconButton(onClick = { onNavigate("subscriptions") }) {
                        Icon(painterResource(id = R.drawable.ic_subscriptions_24dp), contentDescription = "Подписки")
                    }
                    IconButton(onClick = { onNavigate("settings") }) {
                        Icon(painterResource(id = R.drawable.ic_settings_24dp), contentDescription = "Настройки")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAction(MainAction.ImportClipboard) }) {
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
            Spacer(modifier = Modifier.height(16.dp))

            // Кнопка подключения вверху по центру
            Button(
                onClick = { onAction(MainAction.ToggleService) },
                modifier = Modifier.width(200.dp)
            ) {
                Text(text = if (uiState.isRunning) "Отключить" else "Подключить")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Список всех профилей (подписок) друг за другом
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(subscriptions, key = { it.guid }) { subCache ->
                    val serversFlow = remember(subCache.guid) { mainViewModel.serversForGroup(subCache.guid) }
                    val servers by serversFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                    ProfileCard(
                        subscription = subCache,
                        servers = servers,
                        selectedGuid = uiState.selectedGuid,
                        onPingProfile = { guid -> onAction(MainAction.TestProfileTcpPing(guid)) },
                        onUpdateSubscription = { onAction(MainAction.UpdateSubscriptions) },
                        onSelectServer = { guid -> onAction(MainAction.SelectServer(guid)) }
                    )
                }
            }
        }
    }
}
