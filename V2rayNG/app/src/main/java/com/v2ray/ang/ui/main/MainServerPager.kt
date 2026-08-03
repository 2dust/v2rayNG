package com.v2ray.ang.ui.main

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.v2ray.ang.util.JsonUtil
import java.io.File

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
        val strokeW = 3f
        drawCircle(color, style = Stroke(width = strokeW))
        drawOval(color, topLeft = Offset(size.width * 0.25f, 0f), size = Size(size.width * 0.5f, size.height), style = Stroke(width = strokeW))
        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = strokeW)
    }
}

// Форматер даты
fun formatDate(millis: Long, format: String = "dd.MM.yyyy"): String {
    if (millis <= 0L) return "Никогда"
    val formatter = SimpleDateFormat(format, Locale.getDefault())
    return formatter.format(Date(millis))
}

// Умный парсер протоколов (ИСПОЛЬЗУЕТ ПЕРЕДАННЫЙ GUID СЕРВЕРА)
fun getProtocolDescription(context: Context, profile: ProfileItem, guid: String): String {
    val configTypeName = profile.configType.name.uppercase()
    
    // 1. Стандартные профили
    if (configTypeName != "CUSTOM") {
        val parts = mutableListOf(configTypeName)
        val network = profile.network?.uppercase()
        if (!network.isNullOrBlank() && network != "TCP") parts.add(network)
        else if (configTypeName != "HYSTERIA2") parts.add("TCP")
        
        val security = profile.security?.uppercase()
        if (!security.isNullOrBlank() && security != "NONE") parts.add(security)
        
        return parts.joinToString(" / ")
    }

    // 2. Парсинг CUSTOM (JSON) профилей
    return try {
        var jsonStr = ""
        
        // Агрессивный поиск файла конфига по всем типичным путям v2rayNG
        val possibleFiles = listOf(
            File(context.filesDir, "$guid.json"),
            File(context.filesDir, "$guid.txt"),
            File(context.filesDir, guid), // Часто v2rayNG сохраняет файл без расширения
            File(context.getExternalFilesDir(null), "$guid.txt"),
            File(context.getExternalFilesDir(null), guid)
        )

        for (file in possibleFiles) {
            if (file.exists()) {
                jsonStr = file.readText()
                break
            }
        }

        // Если файла нет, возможно JSON лежит прямо в поле server (если форк переделан под прямую передачу)
        if (jsonStr.isEmpty() && profile.server?.trim()?.startsWith("{") == true) {
            jsonStr = profile.server!!
        }

        // Если так и не нашли данные
        if (jsonStr.isEmpty()) return "CUSTOM"

        // Используем встроенный JsonUtil (Gson), он не крашится из-за комментариев в Xray-конфигах
        val root = JsonUtil.parseString(jsonStr) ?: return "CUSTOM"
        val outbounds = root.getAsJsonArray("outbounds") ?: return "CUSTOM"

        for (i in 0 until outbounds.size()) {
            val outbound = outbounds.get(i).asJsonObject
            var protocol = outbound.get("protocol")?.asString?.uppercase() ?: continue

            // Пропускаем технические outbound'ы маршрутизации
            if (protocol.isEmpty() || protocol == "FREEDOM" || protocol == "BLACKHOLE") continue

            val streamSettings = outbound.getAsJsonObject("streamSettings")
            
            if (protocol == "HYSTERIA" && streamSettings != null) {
                val hSettings = streamSettings.getAsJsonObject("hysteriaSettings")
                if (hSettings != null && hSettings.get("version")?.asInt == 2) {
                    protocol = "HYSTERIA2"
                }
            }

            val parts = mutableListOf(protocol)

            if (streamSettings != null) {
                val network = streamSettings.get("network")?.asString?.uppercase() ?: ""
                if (network.isNotEmpty() && !protocol.startsWith(network)) {
                    parts.add(network)
                } else if (protocol != "HYSTERIA" && protocol != "HYSTERIA2") {
                    parts.add("TCP") 
                }

                val security = streamSettings.get("security")?.asString?.uppercase() ?: ""
                if (security.isNotEmpty() && security != "NONE") {
                    parts.add(security)
                }
            }
            return parts.joinToString(" / ")
        }
        "AUTO / TCP"
    } catch (e: Exception) {
        e.printStackTrace()
        "CUSTOM"
    }
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

    val sub = subscription.subscription

    val title = sub.remarks.takeIf { !it.isNullOrBlank() } ?: "Без названия"
    val lastUpdateStr = formatDate(sub.lastUpdated, "dd.MM.yyyy HH:mm")
    val intervalHours = sub.updateInterval / 60
    val updateStatus = "- $intervalHours ч. $lastUpdateStr"

    val gbDivisor = 1073741824.0
    val usedTraffic = sub.trafficUpload + sub.trafficDownload
    val usedStr = String.format(Locale.US, "%.1fGB", usedTraffic / gbDivisor)
    val totalStr = if (sub.trafficTotal == 0L) "∞" else String.format(Locale.US, "%.1fGB", sub.trafficTotal / gbDivisor)
    val trafficDisplay = "$usedStr/$totalStr"

    val expireMillis = if (sub.trafficExpire > 9999999999L) sub.trafficExpire else sub.trafficExpire * 1000
    val expireDisplay = if (expireMillis > 0L) "Истекает: ${formatDate(expireMillis)}" else "Истекает: ∞"

    val announceText = sub.announce ?: ""
    val supportUrl = sub.supportUrl ?: ""

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 12.dp)) {
                
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ChevronDown(color = Color(0xFF5C6BC0), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = title, 
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B),
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = updateStatus, 
                            fontSize = 9.sp, 
                            color = Color.Gray, 
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    // ИСПРАВЛЕНО: Вернули subscription.guid
                    IconButton(onClick = { onUpdateSubscription(subscription.guid) }, modifier = Modifier.size(28.dp)) {
                        Icon(painterResource(id = R.drawable.ic_restore_24dp), contentDescription = "Обновить", tint = Color(0xFF5C6BC0))
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onPingProfile(subscription.guid) }, modifier = Modifier.size(28.dp)) {
                        ClockIcon(color = Color(0xFF5C6BC0), modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                            Icon(painterResource(id = R.drawable.ic_more_vert_24dp), contentDescription = "Меню", tint = Color.Gray)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Редактировать") }, 
                                onClick = { 
                                    showMenu = false
                                    context.startActivity(Intent(context, SubEditActivity::class.java).putExtra("subId", subscription.guid)) 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить", color = Color.Red) }, 
                                onClick = { 
                                    showMenu = false
                                    onDeleteSubscription(subscription.guid) 
                                }
                            )
                        }
                    }
                }
                
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            val subUrl = sub.url
                            if (!subUrl.isNullOrBlank()) try { uriHandler.openUri(subUrl) } catch(e: Exception) { Toast.makeText(context, "Ссылка недоступна", Toast.LENGTH_SHORT).show() }
                            else Toast.makeText(context, "В подписке нет URL", Toast.LENGTH_SHORT).show()
                        }, 
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(painterResource(id = R.drawable.ic_about_24dp), contentDescription = "Info", tint = Color(0xFF5C6BC0))
                    }
                    
                    Spacer(Modifier.width(8.dp))
                    
                    Surface(shape = CircleShape, color = Color(0xFFF1F5F9)) {
                        Text(
                            text = trafficDisplay, 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color(0xFF2C3E50), 
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    
                    Text(
                        text = expireDisplay, 
                        fontSize = 11.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = Color(0xFF2C3E50), 
                        modifier = Modifier.weight(1f), 
                        textAlign = TextAlign.Center
                    )
                    
                    if (supportUrl.isNotBlank()) {
                        IconButton(
                            onClick = { try { uriHandler.openUri(supportUrl) } catch(e: Exception){} }, 
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(painterResource(id = R.drawable.ic_telegram_24dp), contentDescription = "Support", tint = Color(0xFF5C6BC0))
                        }
                    } else {
                        Spacer(Modifier.size(20.dp))
                    }
                }
                
                if (announceText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = announceText, 
                        fontSize = 11.sp, 
                        textAlign = TextAlign.Center, 
                        fontWeight = FontWeight.Bold, 
                        color = Color(0xFF1E293B), 
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }
            }
        }
        
        servers.forEach { serverCache ->
            val isSelected = serverCache.guid == selectedGuid
            
            // ИСПРАВЛЕНО: Передаем serverCache.guid для корректного поиска JSON
            val finalDesc = remember(serverCache.guid)              {
                val desc = getProtocolDescription(context, serverCache.profile, serverCache.guid)
                if (serverCache.profile.configType == com.v2ray.ang.enums.EConfigType.CUSTOM) {
                    "$desc | JSON"
                } else {
                    desc
                }
            }
            
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFF8FAFC) else Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
                    .clickable { onSelectServer(serverCache.guid) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                ) {
                    if (isSelected) {
                        Box(modifier = Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(50)).background(Color(0xFF5C6BC0)))
                    } else {
                        Spacer(Modifier.width(4.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    
                    Box(
                        modifier = Modifier.size(36.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        WireframeGlobe(color = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                    
                    Spacer(Modifier.width(12.dp))
                    
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = serverCache.profile.remarks ?: "Без названия", 
                            fontWeight = FontWeight.ExtraBold, 
                            fontSize = 15.sp, 
                            color = Color(0xFF1E293B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = finalDesc, 
                            fontSize = 9.sp, 
                            color = Color.Gray, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    val delay = serverCache.testDelayMillis
                    if (delay > 0L) {
                        val pingColor = if (delay <= 300L) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        Text(text = "${delay}ms", style = MaterialTheme.typography.bodySmall, color = pingColor, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                    } else if (delay < 0L) {
                        Text(text = "таймаут", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                    }
                    
                    IconButton(
                        onClick = { onEditServer(serverCache.guid, serverCache.profile) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        ChevronRight(color = Color(0xFFCBD5E1), modifier = Modifier.size(14.dp))
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
