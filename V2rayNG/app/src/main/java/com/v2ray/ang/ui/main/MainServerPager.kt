package com.v2ray.ang.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.ui.subscription.SubEditActivity

// --- УТИЛИТЫ И ИКОНКИ ---

fun splitEmojiAndName(fullName: String): Pair<String?, String> {
    val regex = Regex("^([\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\u2B50\\u2B55]+)\\s*(.*)")
    val match = regex.find(fullName)
    return if (match != null) {
        Pair(match.groupValues[1], match.groupValues[2])
    } else {
        Pair(null, fullName)
    }
}

fun getProtocolDescription(profile: ProfileItem): String {
    val configType = profile.configType.name.uppercase().let { if (it == "CUSTOM") "AUTO" else it }
    val parts = mutableListOf(configType)
    
    val network = profile.network?.uppercase()
    if (!network.isNullOrBlank() && network != "TCP") {
        parts.add(network)
    } else if (configType != "HYSTERIA2") {
        parts.add("TCP")
    }
    
    val security = profile.security?.uppercase()
    if (!security.isNullOrBlank() && security != "NONE") {
        parts.add(security)
    }
    
    return parts.joinToString(" / ")
}

@Composable
fun ChevronDown(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 4f
        drawLine(color, Offset(size.width * 0.2f, size.height * 0.3f), Offset(size.width * 0.5f, size.height * 0.7f), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.7f), Offset(size.width * 0.8f, size.height * 0.3f), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

@Composable
fun ChevronRight(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 4f
        drawLine(color, Offset(size.width * 0.3f, size.height * 0.2f), Offset(size.width * 0.7f, size.height * 0.5f), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.7f, size.height * 0.5f), Offset(size.width * 0.3f, size.height * 0.8f), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

@Composable
fun ClockIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 4f
        drawCircle(color, style = Stroke(width = strokeW))
        drawLine(color, center, center.copy(y = center.y - size.width * 0.25f), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(color, center, center.copy(x = center.x + size.width * 0.2f, y = center.y + size.width * 0.2f), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

@Composable
fun WireframeGlobe(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 4f
        drawCircle(color, style = Stroke(width = strokeW))
        drawOval(color, topLeft = Offset(size.width * 0.25f, 0f), size = Size(size.width * 0.5f, size.height), style = Stroke(width = strokeW))
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = strokeW)
    }
}

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

// --- КАРТОЧКА ПОДПИСКИ ---

@Composable
fun ProfileCard(
    subscription: SubscriptionCache,
    servers: List<ServersCache>,
    selectedGuid: String?,
    onAction: (MainAction) -> Unit,
    onPingProfile: (String) -> Unit,
    onUpdateSubscription: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showMenu by remember { mutableStateOf(false) }
    
    val subInfo = subscription.subscription

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
            
            // Первая строка (Шапка)
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    ChevronDown(color = Color(0xFF5C6BC0), modifier = Modifier.size(12.dp))
                }
                Spacer(Modifier.width(8.dp))
                
                Column(Modifier.weight(1f)) {
                    Text(
                        text = subInfo.remarks ?: "Без названия", 
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    val lastUpdatedText = if (subInfo.lastUpdated > 0) {
                        java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(subInfo.lastUpdated))
                    } else {
                        "Никогда"
                    }
                    val updateIntervalHours = subInfo.updateInterval / 60
                    Text(
                        text = "Автообновление - $updateIntervalHours ч. | $lastUpdatedText", 
                        fontSize = 9.sp, 
                        color = Color.Gray, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                IconButton(onClick = { onUpdateSubscription(subscription.guid) }, modifier = Modifier.size(28.dp)) {
                    Icon(painterResource(id = R.drawable.ic_restore_24dp), contentDescription = "Обновить", tint = Color(0xFF5C6BC0), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { onPingProfile(subscription.guid) }, modifier = Modifier.size(28.dp)) {
                    ClockIcon(color = Color(0xFF5C6BC0), modifier = Modifier.size(16.dp))
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(painterResource(id = R.drawable.ic_more_vert_24dp), contentDescription = "Меню", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Редактировать профиль") },
                            onClick = { 
                                showMenu = false
                                context.startActivity(Intent(context, SubEditActivity::class.java).putExtra("subId", subscription.guid))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить профиль", color = Color.Red) },
                            onClick = { 
                                showMenu = false
                                onDeleteSubscription(subscription.guid)
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))

            // Вторая строка (Инфо, Трафик, Дата, Telegram)
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                IconButton(
                    onClick = {
                        val subUrl = subInfo.url
                        if (!subUrl.isNullOrBlank()) {
                            try { uriHandler.openUri(subUrl) } catch(e: Exception) { Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show() }
                        } else {
                            Toast.makeText(context, "В подписке нет URL", Toast.LENGTH_SHORT).show()
                        }
                    }, 
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(painterResource(id = R.drawable.ic_about_24dp), contentDescription = "Info", tint = Color(0xFF5C6BC0), modifier = Modifier.size(18.dp))
                }
                
                Spacer(Modifier.width(8.dp))
                
                val usedTrafficBytes = subInfo.trafficUpload + subInfo.trafficDownload
                val gbDivider = 1024.0 * 1024.0 * 1024.0
                val usedGb = usedTrafficBytes / gbDivider
                
                val totalStr = if (subInfo.trafficTotal == 0L) "∞" else {
                    String.format(java.util.Locale.US, "%.1fGB", subInfo.trafficTotal / gbDivider)
                }
                val usedStr = String.format(java.util.Locale.US, "%.1fGB", usedGb)
                
                Box(modifier = Modifier
                    .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$usedStr / $totalStr", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                }
                
                Spacer(Modifier.weight(1f))
                
                val expireText = if (subInfo.trafficExpire > 0L) {
                    val date = java.util.Date(subInfo.trafficExpire * 1000L)
                    val format = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                    "Истекает: ${format.format(date)}"
                } else {
                    "Без лимита"
                }
                Text(expireText, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
                
                Spacer(Modifier.width(8.dp))
                
                IconButton(
                    onClick = { 
                        val targetUrl = if (subInfo.supportUrl.isNotBlank()) subInfo.supportUrl else "https://t.me/shashachkaaa"
                        try { uriHandler.openUri(targetUrl) } catch(e: Exception){} 
                    }, 
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(painterResource(id = R.drawable.ic_telegram_24dp), contentDescription = "Telegram", tint = Color(0xFF5C6BC0), modifier = Modifier.size(18.dp))
                }
            }
            
            // Третий блок (Анонс)
            if (subInfo.announce.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subInfo.announce, 
                    fontSize = 11.sp, 
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center, 
                    fontWeight = FontWeight.Bold, 
                    color = Color(0xFF334155), 
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Список серверов
            servers.forEach { serverCache ->
                val isSelected = serverCache.guid == selectedGuid
                val rawName = serverCache.profile.remarks ?: "Без названия"
                val (emoji, cleanName) = splitEmojiAndName(rawName)
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectServer(serverCache.guid) }
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                ) {
                    if (isSelected) {
                        Box(modifier = Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(50)).background(Color(0xFF5C6BC0)))
                    } else {
                        Spacer(Modifier.width(4.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    
                    if (emoji != null) {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(36.dp).background(Color(0xFFE2E8F0), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            WireframeGlobe(color = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    Spacer(Modifier.width(14.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = cleanName, 
                            fontWeight = FontWeight.ExtraBold, 
                            fontSize = 15.sp, 
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val finalDesc = getProtocolDescription(serverCache.profile) + " | JSON"

                        Text(
                            text = finalDesc, 
                            fontSize = 9.sp, 
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    val delay = serverCache.testDelayMillis
                    if (delay > 0L) {
                        val pingColor = if (delay <= 300L) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        Text(text = "${delay}ms", style = MaterialTheme.typography.bodySmall, color = pingColor)
                        Spacer(Modifier.width(4.dp))
                    } else if (delay < 0L) {
                        Text(text = "таймаут", fontSize = 10.sp, color = Color(0xFFF44336))
                        Spacer(Modifier.width(4.dp))
                    }
                    
                    IconButton(
                        onClick = { onEditServer(serverCache.guid, serverCache.profile) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        ChevronRight(color = Color.LightGray, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// --- ГЛАВНЫЙ ЭКРАН ---

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
                        .padding(horizontal = 16.dp, vertical = 8.dp), 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onNavigate("settings") }) {
                        Icon(painterResource(id = R.drawable.ic_settings_24dp), contentDescription = "Настройки", modifier = Modifier.size(32.dp))
                    }
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(painterResource(id = R.drawable.ic_add_24dp), contentDescription = "Добавить", modifier = Modifier.size(32.dp))
                        }
                        DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                            DropdownMenuItem(text = { Text("Импорт из буфера") }, onClick = { showImportMenu = false; onAction(MainAction.ImportClipboard) })
                            DropdownMenuItem(text = { Text("Сканировать QR") }, onClick = { showImportMenu = false; onAction(MainAction.ImportQRcode) })
                            DropdownMenuItem(text = { Text("Импорт из файла") }, onClick = { showImportMenu = false; onAction(MainAction.ImportConfigLocal) })
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(colors = listOf(Color(0xFFE0E0E0), Color(0xFFFFCDD2), Color(0xFFE0E0E0))),
                                shape = CircleShape
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .shadow(
                                elevation = 24.dp,
                                shape = CircleShape,
                                ambientColor = Color(0xFF5C6BC0).copy(alpha = 0.5f),
                                spotColor = Color(0xFF5C6BC0).copy(alpha = 0.5f)
                            )
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(8.dp, Color(0xFFF4F6F9), CircleShape)
                            .clickable { onAction(MainAction.ToggleService) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PowerIcon(
                                color = if (uiState.isRunning) Color(0xFF5C6BC0) else Color.LightGray,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = if (uiState.isRunning) "ПОДКЛЮЧЕН" else "ОТКЛЮЧЕН",
                                color = Color(0xFF2C3E50),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            
                            if (uiState.isRunning) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = timeString,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "Проверить текущее\nподключение",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onAction(MainAction.TestCurrentServer) }
                    )
                    Text(
                        text = "Скрыть все",
                        color = Color.Gray,
                        fontSize = 13.sp,
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                onUpdateSubscription = { 
                                    mainViewModel.updateSubscription(it)
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
        
        // Плавная выезжающая плашка загрузки сверху
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

        // Всплывающая плашка с ошибкой снизу
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

// 100% Оригинальная сигнатура для MainActivity
@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    doubleColumnDisplay: Boolean,
    confirmRemove: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String) -> Unit,
    contentPadding: PaddingValues
) {
    // Архитектурная заглушка
}
