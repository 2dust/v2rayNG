package com.v2ray.ang.ui.main

import androidx.compose.animation.*
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

@Composable
fun PowerIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 5f
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
            end = center.copy(y = center.y + 2f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (String) -> Unit
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val subscriptions by mainViewModel.subscriptions.collectAsStateWithLifecycle()
    val isImporting by mainViewModel.isImporting.collectAsStateWithLifecycle()
    val importError by mainViewModel.importError.collectAsStateWithLifecycle()

    var showImportMenu by remember { mutableStateOf(false) }

    LaunchedEffect(importError) {
        if (importError != null) {
            delay(4000)
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
            containerColor = Color(0xFFF4F6FB)
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
                        .padding(horizontal = 16.dp, vertical = 4.dp), // Ужали верхний бар
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onNavigate("settings") }) {
                        Icon(painterResource(id = R.drawable.ic_settings_24dp), contentDescription = "Настройки", modifier = Modifier.size(28.dp))
                    }
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(painterResource(id = R.drawable.ic_add_24dp), contentDescription = "Добавить", modifier = Modifier.size(28.dp))
                        }
                        DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                            DropdownMenuItem(text = { Text("Импорт из буфера") }, onClick = { showImportMenu = false; onAction(MainAction.ImportClipboard) })
                            DropdownMenuItem(text = { Text("Сканировать QR") }, onClick = { showImportMenu = false; onAction(MainAction.ImportQRcode) })
                            DropdownMenuItem(text = { Text("Импорт из файла") }, onClick = { showImportMenu = false; onAction(MainAction.ImportConfigLocal) })
                        }
                    }
                }

                // Компактная центральная кнопка
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, bottom = 8.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    val isConnected = uiState.isRunning
                    val primaryColor = if (isConnected) Color(0xFF4A68FF) else Color(0xFFB0BEC5)
                    val glowColor = if (isConnected) Color(0xFF4A68FF).copy(alpha = 0.3f) else Color.Transparent
                    val textColor = if (isConnected) Color(0xFF8A93A6) else Color(0xFF1E293B)

                    Box(
                        modifier = Modifier
                            .size(170.dp) // Было 240
                            .background(
                                brush = Brush.radialGradient(colors = listOf(glowColor, Color.Transparent)),
                                shape = CircleShape
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(130.dp) // Было 190
                            .border(
                                width = 1.dp,
                                color = if (isConnected) primaryColor.copy(alpha = 0.3f) else Color(0xFFE0E0E0),
                                shape = CircleShape
                            )
                    )

                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(110.dp) // Было 150
                            .clickable { onAction(MainAction.ToggleService) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            PowerIcon(
                                color = primaryColor,
                                modifier = Modifier.size(28.dp) // Было 42
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isConnected) "ПОДКЛЮЧЕН" else "ОТКЛЮЧЕН",
                                color = textColor,
                                fontSize = 11.sp, // Было 14
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            if (isConnected) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = timeString,
                                    color = Color.Black,
                                    fontSize = 13.sp, // Было 16
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 0.dp, bottom = 4.dp), // Ужали отступы
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Проверить текущее\nподключение",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onAction(MainAction.TestCurrentServer) }
                    )
                    Text(
                        text = "Скрыть все",
                        color = Color.Gray,
                        fontSize = 12.sp,
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 16.dp) // Отступ снизу для красоты прокрутки
                    ) {
                        items(subscriptions, key = { it.guid + (it.subscription.remarks ?: "") }) { subCache ->
                            val serversFlow = remember(subCache.guid) { mainViewModel.serversForGroup(subCache.guid) }
                            val servers by serversFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                            ProfileCard(
                                subscription = subCache,
                                servers = servers,
                                selectedGuid = uiState.selectedGuid,
                                onAction = onAction,
                                onPingProfile = { guid -> 
                                    onAction(MainAction.SelectGroup(guid))
                                    onAction(MainAction.TestProfileTcpPing(guid)) 
                                },
                                onUpdateSubscription = { subId -> 
                                    mainViewModel.updateSubscription(subId)
                                },
                                onSelectServer = { guid -> 
                                    onAction(MainAction.SelectServer(guid)) 
                                },
                                onDeleteSubscription = { subId ->
                                    mainViewModel.removeSubscription(subId)
                                },
                                onEditServer = { guid, profile ->
                                    onAction(MainAction.EditServer(guid, profile))
                                }
                            )
                        }
                    }
                }
            }
        }
        
        AnimatedVisibility(
            visible = isImporting,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF5C6BC0))
                    Spacer(Modifier.width(12.dp))
                    Text("Обновление подписки...", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                }
            }
        }

        AnimatedVisibility(
            visible = importError != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336)),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Text(
                    text = importError ?: "", 
                    color = Color.White, 
                    fontWeight = FontWeight.Bold, 
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}
