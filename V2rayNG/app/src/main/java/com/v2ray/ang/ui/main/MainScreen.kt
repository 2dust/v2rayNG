package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem

@Composable
fun PowerIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 6f
        drawArc(
            color = color,
            startAngle = -240f,
            sweepAngle = 300f,
            useCenter = false,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = center.copy(y = center.y - size.height / 2),
            end = center.copy(y = center.y + 4f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: ((MainAction) -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null,
    onAddServer: (() -> Unit)? = null,
    onScanQR: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onOpenSubscriptions: (() -> Unit)? = null,
    onEditServer: ((String, ProfileItem) -> Unit)? = null,
    onShareServer: ((String, ProfileItem) -> Unit)? = null,
    onMoreServer: ((String, ProfileItem) -> Unit)? = null,
    onRemoveServer: ((String) -> Unit)? = null
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val subscriptions by mainViewModel.subscriptions.collectAsStateWithLifecycle()
    val isImporting by mainViewModel.isImporting.collectAsStateWithLifecycle()
    val importError by mainViewModel.importError.collectAsStateWithLifecycle()

    var showImportMenu by remember { mutableStateOf(false) }

    // Очистка ошибки через 3 секунды
    LaunchedEffect(importError) {
        if (importError != null) {
            delay(3000)
            mainViewModel.importError.value = null
        }
    }

    var uptime by remember { mutableLongStateOf(0L) }
    LaunchedEffect(uiState.isRunning, uiState.serviceStartTime) {
        if (uiState.isRunning && uiState.serviceStartTime != null) {
            while (true) {
                uptime = System.currentTimeMillis() - uiState.serviceStartTime!!
                delay(1000L)
            }
        } else {
            uptime = 0L
        }
    }
    val seconds = (uptime / 1000) % 60
    val minutes = (uptime / (1000 * 60)) % 60
    val hours = (uptime / (1000 * 60 * 60))
    val timeString = "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color(0xFFF3F4F6)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onOpenSettings?.invoke() ?: onNavigate?.invoke("settings") }) {
                        Icon(painterResource(id = R.drawable.ic_settings_24dp), contentDescription = "Настройки", modifier = Modifier.size(32.dp))
                    }
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(painterResource(id = R.drawable.ic_add_24dp), contentDescription = "Добавить", modifier = Modifier.size(32.dp))
                        }
                        DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                            DropdownMenuItem(text = { Text("Импорт из буфера") }, onClick = { showImportMenu = false; onAction?.invoke(MainAction.ImportClipboard) ?: mainViewModel.onAction(MainAction.ImportClipboard) })
                            DropdownMenuItem(text = { Text("Сканировать QR") }, onClick = { showImportMenu = false; onScanQR?.invoke() ?: onAction?.invoke(MainAction.ImportQRcode) })
                            DropdownMenuItem(text = { Text("Импорт из файла") }, onClick = { showImportMenu = false; onAction?.invoke(MainAction.ImportConfigLocal) ?: mainViewModel.onAction(MainAction.ImportConfigLocal) })
                            DropdownMenuItem(text = { Text("Добавить вручную") }, onClick = { showImportMenu = false; onAddServer?.invoke() })
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(colors = listOf(Color(0xFFE0E0E0), Color(0xFFFFCDD2), Color(0xFFE0E0E0))),
                                shape = CircleShape
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .shadow(
                                elevation = 32.dp,
                                shape = CircleShape,
                                ambientColor = Color(0xFF5C6BC0).copy(alpha = 0.5f),
                                spotColor = Color(0xFF5C6BC0).copy(alpha = 0.5f)
                            )
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(8.dp, Color(0xFFF4F6F9), CircleShape)
                            .clickable { onAction?.invoke(MainAction.ToggleService) ?: mainViewModel.onAction(MainAction.ToggleService) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PowerIcon(
                                color = if (uiState.isRunning) Color(0xFF5C6BC0) else Color.LightGray,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = if (uiState.isRunning) "ПОДКЛЮЧЕН" else "ОТКЛЮЧЕН",
                                color = Color(0xFF2C3E50),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            
                            if (uiState.isRunning) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = timeString,
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Проверить текущее\nподключение",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onAction?.invoke(MainAction.TestCurrentServer) ?: mainViewModel.onAction(MainAction.TestCurrentServer) }
                    )
                    Text(
                        text = "Скрыть все",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (subscriptions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(id = R.drawable.ic_cloud_download_24dp), contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Нет добавленных профилей.\nНажмите '+' чтобы импортировать подписку.",
                                textAlign = TextAlign.Center,
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(subscriptions, key = { it.guid }) { subCache ->
                            val serversFlow = remember(subCache.guid) { mainViewModel.serversForGroup(subCache.guid) }
                            val servers by serversFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                            ProfileCard(
                                subscription = subCache,
                                servers = servers,
                                selectedGuid = uiState.selectedGuid,
                                onAction = onAction,
                                onPingProfile = { guid -> 
                                    mainViewModel.onAction(MainAction.SelectGroup(guid))
                                    mainViewModel.onAction(MainAction.TestProfileTcpPing(guid)) 
                                },
                                onUpdateSubscription = { 
                                    mainViewModel.updateSubscription(it)
                                },
                                onSelectServer = { guid -> 
                                    onAction?.invoke(MainAction.SelectServer(guid)) ?: mainViewModel.onAction(MainAction.SelectServer(guid)) 
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Анимация импорта
        AnimatedVisibility(
            visible = isImporting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(enabled = false) {}, // Блокируем UI
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF5C6BC0), strokeWidth = 4.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Обновление подписки...", fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), fontSize = 16.sp)
                    }
                }
            }
        }

        // Вывод ошибки импорта
        AnimatedVisibility(
            visible = importError != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336))
            ) {
                Text(
                    text = importError ?: "", 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold, 
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
