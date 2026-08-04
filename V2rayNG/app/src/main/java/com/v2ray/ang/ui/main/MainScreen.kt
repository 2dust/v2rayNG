package com.v2ray.ang.ui.main

import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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

    // Крутится, пока служба не отчиталась о новом состоянии (или об ошибке запуска)
    var isConnecting by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRunning, uiState.statusText) { isConnecting = false }
    LaunchedEffect(isConnecting) {
        if (isConnecting) {
            delay(20000)
            isConnecting = false
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
            // ДИНАМИЧЕСКИЙ ФОН: Подстраивается под тему (белый, серый или черный AMOLED)
            containerColor = MaterialTheme.colorScheme.background
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
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onNavigate("settings") }) {
                        Icon(
                            painterResource(id = R.drawable.ic_settings_24dp), 
                            contentDescription = "Настройки", 
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onBackground // Динамический цвет иконки
                        )
                    }
                    Box {
                        IconButton(onClick = { showImportMenu = true }) {
                            Icon(
                                painterResource(id = R.drawable.ic_add_24dp), 
                                contentDescription = "Добавить", 
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onBackground // Динамический цвет иконки
                            )
                        }
                        DropdownMenu(
                            expanded = showImportMenu, 
                            onDismissRequest = { showImportMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface) // Фон меню
                        ) {
                            DropdownMenuItem(text = { Text("Импорт из буфера", color = MaterialTheme.colorScheme.onSurface) }, onClick = { showImportMenu = false; onAction(MainAction.ImportClipboard) })
                            DropdownMenuItem(text = { Text("Сканировать QR", color = MaterialTheme.colorScheme.onSurface) }, onClick = { showImportMenu = false; onAction(MainAction.ImportQRcode) })
                            DropdownMenuItem(text = { Text("Импорт из файла", color = MaterialTheme.colorScheme.onSurface) }, onClick = { showImportMenu = false; onAction(MainAction.ImportConfigLocal) })
                        }
                    }
                }

                // Центральная кнопка
                PowerButton(
                    isConnected = uiState.isRunning,
                    isConnecting = isConnecting,
                    timeString = timeString,
                    onClick = {
                        isConnecting = true
                        onAction(MainAction.ToggleService)
                    }
                )

                // Действия под кнопкой оформлены чипами, а не голым текстом
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionChip(
                        text = "Проверить подключение",
                        modifier = Modifier.weight(1f),
                        onClick = { onAction(MainAction.TestCurrentServer) }
                    )
                    ActionChip(
                        text = "Скрыть все",
                        onClick = { }
                    )
                }

                if (subscriptions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painterResource(id = R.drawable.ic_cloud_download_24dp), 
                                contentDescription = null, 
                                modifier = Modifier.size(64.dp), 
                                tint = MaterialTheme.colorScheme.outlineVariant // ДИНАМИЧЕСКИЙ СЕРЫЙ
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Нет добавленных профилей.\nНажмите '+' чтобы импортировать подписку.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), // ДИНАМИЧЕСКИЙ СЕРЫЙ
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(
                            items = subscriptions,
                            key = { _, item -> item.guid + (item.subscription.remarks ?: "") }
                        ) { index, subCache ->
                            val serversFlow = remember(subCache.guid) { mainViewModel.serversForGroup(subCache.guid) }
                            val servers by serversFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                            // Карточки въезжают снизу по очереди при первом показе
                            var appeared by rememberSaveable(subCache.guid) { mutableStateOf(false) }
                            LaunchedEffect(subCache.guid) {
                                if (!appeared) {
                                    delay(index * 60L)
                                    appeared = true
                                }
                            }
                            val itemAlpha by animateFloatAsState(
                                targetValue = if (appeared) 1f else 0f,
                                animationSpec = tween(360),
                                label = "cardAlpha"
                            )
                            val itemOffset by animateDpAsState(
                                targetValue = if (appeared) 0.dp else 24.dp,
                                animationSpec = tween(360),
                                label = "cardOffset"
                            )

                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        alpha = itemAlpha
                                        translationY = itemOffset.toPx()
                                    }
                            ) {
                            ProfileCard(
                                subscription = subCache,
                                servers = servers,
                                selectedGuid = uiState.selectedGuid,
                                onAction = onAction,
                                onPingProfile = { guid -> 
                                    onAction(MainAction.SelectGroup(guid))
                                    onAction(MainAction.TestProfilePing(guid)) 
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
        }
        
        TopProgressBanner(
            visible = isImporting,
            text = "Обновление подписки...",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
        )

        // Same banner for the ping run, carrying the "x / y left" progress
        TopProgressBanner(
            visible = uiState.isTesting && !isImporting,
            text = uiState.statusText,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
        )

        AnimatedVisibility(
            visible = importError != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), // ДИНАМИЧЕСКИЙ ЦВЕТ ОШИБКИ
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Text(
                    text = importError ?: "", 
                    color = MaterialTheme.colorScheme.onErrorContainer, // ДИНАМИЧЕСКИЙ ЦВЕТ ТЕКСТА ОШИБКИ
                    fontWeight = FontWeight.Bold, 
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * Pill that slides in from the top while a long running task is in progress.
 */
@Composable
private fun TopProgressBanner(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Крупная круглая кнопка подключения: заливка и свечение берутся из темы,
 * при подключении по кругу бежит дуга, таймер появляется плавно.
 */
@Composable
private fun PowerButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    timeString: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (isConnected) scheme.primary else scheme.outline

    // Свечение и обводка дышат при подключении и при активном соединении
    val transition = rememberInfiniteTransition(label = "power")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // Насколько ярко подкрашивать кнопку: на светлой теме мягкое свечение
    // расплывается в серое пятно, поэтому там оно почти не нужно
    val isLightTheme = scheme.background.luminance() > 0.5f

    val ringAlpha by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0.45f,
        animationSpec = tween(500),
        label = "ringAlpha"
    )
    val ringColor by animateColorAsState(
        targetValue = if (isConnected) scheme.primary else scheme.outlineVariant,
        animationSpec = tween(500),
        label = "ring"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Внешнее тонкое кольцо
        Box(
            modifier = Modifier
                .size(206.dp)
                .scale(if (isConnecting) pulse else 1f)
                .border(
                    width = 1.dp,
                    color = ringColor.copy(alpha = 0.15f * ringAlpha),
                    shape = CircleShape
                )
        )

        // Среднее кольцо, по нему бежит дуга подключения
        Canvas(
            modifier = Modifier
                .size(182.dp)
                .scale(if (isConnecting) pulse else 1f)
        ) {
            drawCircle(color = ringColor.copy(alpha = 0.35f * ringAlpha), style = Stroke(width = 3f))
            if (isConnecting) {
                rotate(sweepAngle) {
                    drawArc(
                        color = accent,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = 7f, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Заливка непрозрачная: сквозь полупрозрачную просвечивала бы тень кнопки,
        // а система рисует её многоугольником
        val tint = if (isLightTheme) 0.10f else 0.28f
        val fillCenter = if (isConnected) {
            lerp(scheme.surface, scheme.primary, tint)
        } else {
            scheme.surfaceContainerHigh
        }
        val fillEdge = scheme.surface

        Box(
            modifier = Modifier
                .size(150.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(brush = Brush.radialGradient(colors = listOf(fillCenter, fillEdge)))
                .border(
                    width = 2.dp,
                    color = if (isConnected) scheme.primary else scheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PowerIcon(
                    color = if (isConnected) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        isConnecting && !isConnected -> "ПОДКЛЮЧЕНИЕ"
                        isConnecting && isConnected -> "ОТКЛЮЧЕНИЕ"
                        isConnected -> "ПОДКЛЮЧЕН"
                        else -> "ОТКЛЮЧЕН"
                    },
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                AnimatedVisibility(
                    visible = isConnected,
                    enter = fadeIn(tween(400)) + expandVertically(tween(400)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = timeString,
                            color = scheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Небольшой чип-действие: мягкая подложка, отклик на нажатие.
 */
@Composable
private fun ActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "chipScale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            )
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}
