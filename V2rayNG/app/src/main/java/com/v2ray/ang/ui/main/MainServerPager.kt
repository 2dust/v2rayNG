package com.v2ray.ang.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.ui.subscription.SubEditActivity

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

// Умный парсер протоколов (VLESS / TCP / REALITY | JSON)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)) // Чуть более светлый фон, как на оригинале
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
            // --- ПЕРВАЯ СТРОКА (Шапка) ---
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                // Иконка-галочка
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    ChevronDown(color = Color(0xFF5C6BC0), modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(8.dp))
                
                // Текст (Название и Обновление)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = subInfo.remarks ?: "Без названия", 
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
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
                        text = "Автообновление - $updateIntervalHours ч.  |  $lastUpdatedText", 
                        fontSize = 10.sp, 
                        color = Color.Gray, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Иконки управления
                IconButton(onClick = { onUpdateSubscription(subscription.guid) }, modifier = Modifier.size(32.dp)) {
                    Icon(painterResource(id = R.drawable.ic_restore_24dp), contentDescription = "Обновить", tint = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { onPingProfile(subscription.guid) }, modifier = Modifier.size(32.dp)) {
                    ClockIcon(color = Color(0xFF5C6BC0), modifier = Modifier.size(18.dp))
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(id = R.drawable.ic_more_vert_24dp), contentDescription = "Меню", tint = Color.Gray, modifier = Modifier.size(20.dp))
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
            
            Spacer(Modifier.height(14.dp))

            // --- ВТОРАЯ СТРОКА (Инфо, Трафик, Дата, Telegram) ---
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
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(painterResource(id = R.drawable.ic_about_24dp), contentDescription = "Info", tint = Color(0xFF5C6BC0), modifier = Modifier.size(22.dp))
                }
                
                Spacer(Modifier.width(8.dp))
                
                val usedTrafficBytes = subInfo.trafficUpload + subInfo.trafficDownload
                val gbDivider = 1024.0 * 1024.0 * 1024.0
                val usedGb = usedTrafficBytes / gbDivider
                
                val totalStr = if (subInfo.trafficTotal == 0L) "∞" else {
                    String.format(java.util.Locale.US, "%.1fGB", subInfo.trafficTotal / gbDivider)
                }
                val usedStr = String.format(java.util.Locale.US, "%.1fGB", usedGb)
                
                // Плашка трафика с правильным фоном
                Box(modifier = Modifier
                    .background(Color(0xFFF1F5F9), CircleShape)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$usedStr / $totalStr", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B))
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
                
                // Нормальный размер Telegram иконки
                IconButton(
                    onClick = { 
                        val targetUrl = if (subInfo.supportUrl.isNotBlank()) subInfo.supportUrl else "https://t.me/shashachkaaa"
                        try { uriHandler.openUri(targetUrl) } catch(e: Exception){} 
                    }, 
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(painterResource(id = R.drawable.ic_telegram_24dp), contentDescription = "Telegram", tint = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp))
                }
            }
            
            // --- ТРЕТИЙ БЛОК (Анонс) ---
            if (subInfo.announce.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = subInfo.announce, 
                    fontSize = 11.sp, 
                    lineHeight = 16.sp, // Важно для компактности текста
                    textAlign = TextAlign.Center, 
                    fontWeight = FontWeight.Bold, 
                    color = Color(0xFF2C3E50), 
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // --- СПИСОК СЕРВЕРОВ ---
            servers.forEach { serverCache ->
                val isSelected = serverCache.guid == selectedGuid
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectServer(serverCache.guid) }
                        .padding(vertical = 12.dp, horizontal = 8.dp)
                ) {
                    if (isSelected) {
                        Box(modifier = Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(50)).background(Color(0xFF5C6BC0)))
                    } else {
                        Spacer(Modifier.width(4.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    
                    Box(
                        modifier = Modifier.size(44.dp).background(Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        WireframeGlobe(color = Color.Gray, modifier = Modifier.size(24.dp))
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = serverCache.profile.remarks ?: "Без названия", 
                            fontWeight = FontWeight.ExtraBold, 
                            fontSize = 16.sp, 
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        val finalDesc = getProtocolDescription(serverCache.profile) + " | JSON"

                        Text(
                            text = finalDesc, 
                            fontSize = 10.sp, 
                            color = Color.Gray, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    val delay = serverCache.testDelayMillis
                    if (delay > 0L) {
                        val pingColor = if (delay <= 300L) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        Text(text = "${delay}ms", style = MaterialTheme.typography.bodySmall, color = pingColor)
                        Spacer(Modifier.width(8.dp))
                    } else if (delay < 0L) {
                        Text(text = "таймаут", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                        Spacer(Modifier.width(8.dp))
                    }
                    
                    IconButton(
                        onClick = { onEditServer(serverCache.guid, serverCache.profile) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        ChevronRight(color = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }
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
