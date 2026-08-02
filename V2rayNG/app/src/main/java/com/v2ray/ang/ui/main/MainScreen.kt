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
import com.v2ray.ang.dto.entities.ProfileItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAddServer: () -> Unit,
    onScanQR: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    // Получаем подписки, где default уже отфильтрован в ViewModel
    val subscriptions = mainViewModel.getSubscriptions()

    var showImportMenu by remember { mutableStateOf(false) }

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
            Box {
                FloatingActionButton(onClick = { showImportMenu = true }) {
                    Icon(painterResource(id = R.drawable.ic_add_24dp), contentDescription = "Добавить")
                }
                
                DropdownMenu(
                    expanded = showImportMenu,
                    onDismissRequest = { showImportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Импорт из буфера обмена") },
                        onClick = {
                            showImportMenu = false
                            mainViewModel.onAction(MainAction.ImportClipboard)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Сканировать QR-код") },
                        onClick = {
                            showImportMenu = false
                            onScanQR()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Импорт из файла") },
                        onClick = {
                            showImportMenu = false
                            mainViewModel.onAction(MainAction.ImportConfigLocal)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Добавить вручную") },
                        onClick = {
                            showImportMenu = false
                            onAddServer()
                        }
                    )
                }
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
                onClick = { mainViewModel.onAction(MainAction.ToggleService) },
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
                        onPingProfile = { guid -> mainViewModel.onAction(MainAction.TestProfileTcpPing(guid)) },
                        onUpdateSubscription = { mainViewModel.onAction(MainAction.UpdateSubscriptions) },
                        onSelectServer = { guid -> mainViewModel.onAction(MainAction.SelectServer(guid)) }
                    )
                }
            }
        }
    }
}
