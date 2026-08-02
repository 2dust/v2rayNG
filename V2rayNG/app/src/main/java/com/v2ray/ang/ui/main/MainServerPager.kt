package com.v2ray.ang.ui.main

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.subscription.SubSettingActivity

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
fun ProfileCard(
    subscription: SubscriptionCache,
    servers: List<ServersCache>,
    selectedGuid: String?,
    onPingProfile: (String) -> Unit,
    onUpdateSubscription: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
            // Первая строка
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                ChevronDown(color = Color(0xFF5C6BC0), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(12.dp))
                
                Column(Modifier.weight(1f)) {
                    Text(
                        text = subscription.subscription.remarks ?: "Без названия", 
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("- 1 ч. 02.08.2026 20:03 | ...", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                }
                
                IconButton(onClick = { onUpdateSubscription(subscription.guid) }, modifier = Modifier.size(32.dp)) {
                    Icon(painterResource(id = android.R.drawable.ic_menu_revert), contentDescription = "Обновить", tint = Color(0xFF5C6BC0))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onPingProfile(subscription.guid) }, modifier = Modifier.size(32.dp)) {
                    ClockIcon(color = Color(0xFF5C6BC0), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(painterResource(id = R.drawable.ic_menu_more), contentDescription = "Меню", tint = Color.Gray)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Управление подписками") },
                            onClick = { 
                                showMenu = false
                                context.startActivity(Intent(context, SubSettingActivity::class.java))
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))

            // Вторая строка: Инфо таблетка
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                IconButton(
                    onClick = {
                        val subUrl = subscription.subscription.url
                        if (!subUrl.isNullOrBlank()) {
                            try { uriHandler.openUri(subUrl) } catch(e: Exception) { Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show() }
                        } else {
                            Toast.makeText(context, "В подписке нет URL", Toast.LENGTH_SHORT).show()
                        }
                    }, 
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(painterResource(id = R.drawable.ic_menu_info_details), contentDescription = "Info", tint = Color(0xFF5C6BC0))
                }
                
                Spacer(Modifier.width(12.dp))
                
                Box(modifier = Modifier
                    .border(1.dp, Color.LightGray, CircleShape)
                    .padding(horizontal = 24.dp, vertical = 4.dp)) {
                    Text("54,8GB/∞", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                }
                
                Spacer(Modifier.weight(1f))
                Text("Истекает: 17.08.2026", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50))
                Spacer(Modifier.width(12.dp))
                
                IconButton(
                    onClick = { 
                        try { uriHandler.openUri("https://t.me/shashachkaaa") } catch(e: Exception){} 
                    }, 
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(painterResource(id = R.drawable.ic_menu_share), contentDescription = "Telegram", tint = Color(0xFF5C6BC0))
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Текст-хардкод
            Text(
                "💪 Vanguard VPN - Это не про обход, это про\nпревосходство.\nЕсли подписка не работает — нажмите на кнопку «↻»,\nчтобы обновить её", 
                fontSize = 11.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color(0xFF2C3E50), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "DALBAEB | Осталось 14 дней", 
                fontSize = 12.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E293B), modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Список серверов
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
                        
                        // Парсим протоколы красиво без CUSTOM: "VLESS / TCP / REALITY | JSON"
                        val configType = serverCache.profile.configType.name.uppercase()
                        val network = serverCache.profile.network?.uppercase()?.takeIf { it.isNotBlank() }
                        val security = serverCache.profile.security?.uppercase()?.takeIf { it.isNotBlank() && it != "NONE" }
                        
                        val parts = mutableListOf<String>()
                        parts.add(if (configType == "CUSTOM") "AUTO" else configType)
                        if (network != null) parts.add(network) else if (configType != "HYSTERIA2") parts.add("TCP")
                        if (security != null) parts.add(security)
                        
                        val finalDesc = parts.joinToString(" / ") + " | JSON"

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
                    
                    // Кликабельная стрелочка "Редактировать"
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
    // Архитектурная заглушка
}
