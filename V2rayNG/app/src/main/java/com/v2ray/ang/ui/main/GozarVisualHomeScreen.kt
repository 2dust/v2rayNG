package com.v2ray.ang.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/*
 * Visual-only first pass for Gozar.
 * This screen intentionally does not start/stop the VPN and does not read or modify configs.
 * Once the appearance is approved, it can be integrated into the existing MainScreen.
 */

private val GozarBg = Color(0xFF0A0F0D)
private val GozarPanel = Color(0xFF0F1714)
private val GozarPanel2 = Color(0xFF121C18)
private val GozarInset = Color(0xFF0C1310)
private val GozarLine = Color(0xFF1C2B25)
private val GozarLine2 = Color(0xFF2A4238)
private val GozarText = Color(0xFFDFEAE4)
private val GozarSub = Color(0xFF8AA296)
private val GozarFaint = Color(0xFF5C7268)
private val GozarAccent = Color(0xFF2FE6A8)
private val GozarAccentDark = Color(0xFF0D3A2B)

@Composable
fun GozarVisualHomeScreen(
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onConnectClick: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = GozarBg,
        contentColor = GozarText
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 62.dp, bottom = 118.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopIcon("⋮", onMoreClick)
                    TopIcon("+", onAddClick)
                    TopIcon("▽", onFilterClick)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("فایل کانفیگ", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = GozarText)
                        Text("گذر", fontSize = 10.sp, color = GozarFaint)
                    }
                    Spacer(Modifier.width(6.dp))
                    TopIcon("☰", onMenuClick)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("همه  12", "فعال  1", "سریع  4").forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (index == 0) GozarAccentDark else GozarBg)
                                .border(1.dp, if (index == 0) GozarAccent else GozarLine, CircleShape)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(label, color = if (index == 0) GozarAccent else GozarSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GozarPanel)
                        .border(1.dp, GozarLine, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("کانفیگ فعال", color = GozarFaint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("سرور آلمان 🇩🇪", color = GozarText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Text("de.example.net:443", color = GozarSub, fontSize = 10.sp)
                        }
                        Text("⌄", color = GozarFaint, fontSize = 22.sp)
                    }

                    Spacer(Modifier.height(8.dp))
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(218.dp).clickable { onConnectClick() }) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 5.dp.toPx()
                            val inset = stroke / 2
                            drawArc(
                                color = GozarLine,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(stroke)
                            )
                            drawArc(
                                color = GozarAccent.copy(alpha = 0.65f),
                                startAngle = -90f,
                                sweepAngle = 82f,
                                useCenter = false,
                                topLeft = Offset(11.dp.toPx(), 11.dp.toPx()),
                                size = Size(size.width - 22.dp.toPx(), size.height - 22.dp.toPx()),
                                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = GozarFaint.copy(alpha = 0.6f),
                                startAngle = 70f,
                                sweepAngle = 95f,
                                useCenter = false,
                                topLeft = Offset(11.dp.toPx(), 11.dp.toPx()),
                                size = Size(size.width - 22.dp.toPx(), size.height - 22.dp.toPx()),
                                style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .size(166.dp)
                                .clip(CircleShape)
                                .background(GozarPanel2)
                                .border(1.dp, GozarLine, CircleShape)
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("⏻", color = GozarSub, fontSize = 32.sp)
                            Text("اتصال", color = GozarText, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Text("برای اتصال لمس کنید", color = GozarFaint, fontSize = 9.sp)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Metric("۰", "دریافت")
                        Metric("۰", "ارسال")
                        Metric("—", "پینگ")
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(GozarLine))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniButton("تست سرعت")
                        MiniButton("جزئیات")
                        MiniButton("بررسی امنیت")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "این صفحه در حال حاضر فقط پیش‌نمایش ظاهری است؛ اتصال واقعی هنوز به آن متصل نشده.",
                        color = GozarFaint,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("فهرست کانفیگ‌ها", modifier = Modifier.fillMaxWidth(), color = GozarSub, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Spacer(Modifier.height(6.dp))
                ConfigPreview("🇩🇪", "سرور آلمان", "de.example.net:443", "VLESS", "۳۸ ms", selected = true)
                ConfigPreview("🇳🇱", "سرور هلند", "nl.example.net:443", "VMESS", "۷۲ ms")
                ConfigPreview("🇫🇮", "سرور فنلاند", "fi.example.net:443", "TROJAN", "۱۱۸ ms")

                Spacer(Modifier.height(18.dp))
                Text("برنامه‌نویسی‌شده توسط محمد حسین زاده", color = GozarFaint, fontSize = 11.sp)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(GozarPanel.copy(alpha = 0.98f))
                    .border(width = 0.dp, color = GozarLine)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⋮", modifier = Modifier.padding(horizontal = 10.dp), color = GozarText, fontSize = 22.sp)
                Text("+", modifier = Modifier.padding(horizontal = 10.dp), color = GozarText, fontSize = 22.sp)
                Text("▽", modifier = Modifier.padding(horizontal = 10.dp), color = GozarText, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("فایل کانفیگ", color = GozarText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text("گذر", color = GozarFaint, fontSize = 10.sp)
                }
                Text("☰", modifier = Modifier.clickable { onMenuClick() }.padding(start = 14.dp, end = 4.dp), color = GozarText, fontSize = 22.sp)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(GozarBg.copy(alpha = 0.97f))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(GozarPanel2)
                        .border(1.dp, GozarLine2, CircleShape)
                        .clickable { onConnectClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = GozarText, fontSize = 24.sp)
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GozarPanel)
                        .border(1.dp, GozarLine, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(GozarFaint))
                    Spacer(Modifier.width(8.dp))
                    Text("قطع", color = GozarSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TopIcon(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GozarText, fontSize = 21.sp)
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(label, color = GozarFaint, fontSize = 10.sp)
        Text(value, color = GozarText, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MiniButton(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(GozarPanel2)
            .border(1.dp, GozarLine, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = GozarText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConfigPreview(
    flag: String,
    name: String,
    host: String,
    protocol: String,
    ping: String,
    selected: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF0D1913) else GozarPanel)
            .border(1.dp, if (selected) Color(0xFF1A5C45) else GozarLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(protocol, color = GozarAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(name, color = GozarText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(flag, fontSize = 14.sp)
            }
            Text(host, color = GozarSub, fontSize = 10.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(ping, color = if (selected) GozarAccent else GozarSub, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
