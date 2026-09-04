package com.v2ray.ang.ui.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceRemoteViewsService
import androidx.glance.appwidget.MyPackageReplacedReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ActionCallbackBroadcastReceiver
import androidx.glance.appwidget.action.ActionTrampolineActivity
import androidx.glance.appwidget.action.InvisibleActionTrampolineActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.multiprocess.MultiProcessConfig
import androidx.glance.appwidget.multiprocess.MultiProcessGlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.core.content.ContextCompat
import androidx.work.multiprocess.RemoteWorkerService
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.shortcut.ScStartActivity

private val HORIZONTAL_WIDGET_HEIGHT = 64.dp
private val ACTION_BUTTON_SIZE = 48.dp
private val ACTION_EDGE_GAP = 8.dp

private val ACTIVE_COLOR = ColorProvider(Color(0xFFF97910))
private val INACTIVE_COLOR = ColorProvider(Color(0xFF9C9C9C))
private val SUCCESS_COLOR = ColorProvider(Color(0xFF009966))
private val ERROR_COLOR = ColorProvider(Color(0xFFFF0099))
private val ICON_COLOR = ColorProvider(Color.White)

class LauncherWidget : MultiProcessGlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        LauncherWidgetLayout.entries.map { it.size }.toSet()
    )
    override val stateDefinition = null

    override fun getMultiProcessConfig(context: Context) = MultiProcessConfig(
        remoteWorkerService = ComponentName(context, LauncherWidgetWorkerService::class.java),
        actionTrampolineActivity = ComponentName(context, LauncherWidgetActionActivity::class.java),
        invisibleActionTrampolineActivity =
            ComponentName(context, LauncherWidgetInvisibleActionActivity::class.java),
        actionCallbackBroadcastReceiver =
            ComponentName(context, LauncherWidgetActionReceiver::class.java),
        remoteViewsService = ComponentName(context, LauncherWidgetRemoteViewsService::class.java),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = LauncherWidgetStateRepository.instance
        val initialState = repository.refresh()
        val states = repository.states
        val localizedContext = AppLocaleManager.localizedContext(context)
        val openAppAction = actionStartActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )

        provideContent {
            GlanceTheme {
                // Glance owns this composition's lifetime; it has no Activity LifecycleOwner.
                val state by states.collectAsState(initialState)
                LauncherWidgetContent(
                    uiState = state.present(localizedContext),
                    openAppAction = openAppAction,
                )
            }
        }
    }
}

@Composable
private fun LauncherWidgetContent(
    uiState: LauncherWidgetUiState,
    openAppAction: Action,
) {
    val textMetrics = launcherWidgetTextMetrics(rememberFontScale())
    val layout = LauncherWidgetLayout.forWidth(LocalSize.current.width.value)
    when (layout) {
        LauncherWidgetLayout.COMPACT -> CompactWidget(uiState)
        LauncherWidgetLayout.MEDIUM,
        LauncherWidgetLayout.WIDE,
        LauncherWidgetLayout.EXTRA_WIDE -> HorizontalWidget(
            uiState = uiState,
            layout = layout,
            openAppAction = openAppAction,
            textMetrics = textMetrics,
        )
    }
}

@Composable
private fun rememberFontScale(): Float {
    val context = LocalContext.current
    var fontScale by remember(context) {
        mutableFloatStateOf(context.resources.configuration.fontScale)
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                fontScale = context.resources.configuration.fontScale
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return fontScale
}

@Composable
private fun CompactWidget(uiState: LauncherWidgetUiState) {
    Scaffold(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(serviceAction(uiState.isRunning))
            .semantics {
                contentDescription = if (uiState.isRunning) uiState.stopActionLabel
                else uiState.startActionLabel
            },
        backgroundColor = if (uiState.isRunning) ACTIVE_COLOR else INACTIVE_COLOR,
        horizontalPadding = 0.dp,
    ) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = GlanceModifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // The adaptive foreground includes padding; clip an enlarged copy to its V mark.
                    Image(
                        provider = ImageProvider(R.mipmap.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = GlanceModifier.size(96.dp),
                    )
                }
                Spacer(GlanceModifier.height(4.dp))
                Image(
                    provider = ImageProvider(
                        if (uiState.isRunning) R.drawable.ic_stop_24dp
                        else R.drawable.ic_play_24dp
                    ),
                    contentDescription = null,
                    modifier = GlanceModifier.size(24.dp),
                    colorFilter = ColorFilter.tint(ICON_COLOR),
                )
            }
        }
    }
}

@Composable
private fun HorizontalWidget(
    uiState: LauncherWidgetUiState,
    layout: LauncherWidgetLayout,
    openAppAction: Action,
    textMetrics: LauncherWidgetTextMetrics,
) {
    val showTestAction = layout != LauncherWidgetLayout.MEDIUM
    val showRestartAction = layout == LauncherWidgetLayout.EXTRA_WIDE

    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(HORIZONTAL_WIDGET_HEIGHT)
                .background(
                    imageProvider = ImageProvider(R.drawable.widget_background),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
                )
                .appWidgetBackground(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(GlanceModifier.width(6.dp))
                if (uiState.isRunning) {
                    Box(
                        modifier = GlanceModifier
                            .width(4.dp)
                            .height(32.dp)
                            .background(ACTIVE_COLOR)
                    ) {}
                } else {
                    Spacer(GlanceModifier.width(4.dp))
                }
                ServerText(
                    profileName = uiState.profileName,
                    status = if (showTestAction) uiState.connectionStatus
                    else uiState.serviceStatus,
                    statusTone = if (showTestAction) uiState.connectionStatusTone
                    else LauncherWidgetStatusTone.NORMAL,
                    openAppAction = openAppAction,
                    textMetrics = textMetrics,
                )
                if (showTestAction) {
                    WidgetActionButton(
                        drawable = R.drawable.ic_speed_24dp,
                        contentDescription = uiState.testActionLabel,
                        action = if (uiState.canTest) actionRunCallback<TestLauncherWidgetAction>()
                        else null,
                    )
                }
                if (showRestartAction) {
                    WidgetActionButton(
                        drawable = R.drawable.ic_restore_24dp,
                        contentDescription = uiState.restartActionLabel,
                        action = if (uiState.isRunning) {
                            actionRunCallback<RestartLauncherWidgetAction>()
                        } else {
                            null
                        },
                    )
                }
                WidgetActionButton(
                    drawable = if (uiState.isRunning) R.drawable.ic_stop_24dp
                    else R.drawable.ic_play_24dp,
                    contentDescription = if (uiState.isRunning) uiState.stopActionLabel
                    else uiState.startActionLabel,
                    action = serviceAction(uiState.isRunning),
                    isPrimary = true,
                    isActive = uiState.isRunning,
                )
                Spacer(GlanceModifier.width(ACTION_EDGE_GAP))
            }
        }
    }
}

@Composable
private fun RowScope.ServerText(
    profileName: String,
    status: String,
    statusTone: LauncherWidgetStatusTone,
    openAppAction: Action,
    textMetrics: LauncherWidgetTextMetrics,
) {
    val accessibleText = listOf(profileName, status).joinToString(". ")
    val modifier = GlanceModifier
        .defaultWeight()
        .fillMaxHeight()
        .padding(
            start = textMetrics.startPaddingDp.dp,
            end = textMetrics.endPaddingDp.dp,
            top = textMetrics.verticalPaddingDp.dp,
            bottom = textMetrics.verticalPaddingDp.dp,
        )
        .clickable(openAppAction)
        .semantics { contentDescription = accessibleText }

    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = profileName,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = textMetrics.profileFontSizeSp.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Text(
            text = status,
            style = TextStyle(
                color = when (statusTone) {
                    LauncherWidgetStatusTone.NORMAL -> GlanceTheme.colors.onSurfaceVariant
                    LauncherWidgetStatusTone.SUCCESS -> SUCCESS_COLOR
                    LauncherWidgetStatusTone.ERROR -> ERROR_COLOR
                },
                fontSize = textMetrics.statusFontSizeSp.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun WidgetActionButton(
    drawable: Int,
    contentDescription: String,
    action: Action?,
    isPrimary: Boolean = false,
    isActive: Boolean = false,
) {
    val enabled = action != null
    var modifier = GlanceModifier.size(ACTION_BUTTON_SIZE)
    if (isPrimary) {
        modifier = modifier.background(
            if (isActive) ImageProvider(R.drawable.ic_rounded_corner_active)
            else ImageProvider(R.drawable.ic_rounded_corner_inactive)
        )
    }
    if (action != null) modifier = modifier.clickable(action)
    modifier = modifier.semantics { this.contentDescription = contentDescription }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(drawable),
            contentDescription = null,
            alpha = if (enabled) 1f else 0.45f,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(
                if (isPrimary) ICON_COLOR
                else if (enabled) GlanceTheme.colors.onSurfaceVariant
                else GlanceTheme.colors.outline
            ),
        )
    }
}

@Composable
private fun serviceAction(isRunning: Boolean): Action =
    if (isRunning) actionRunCallback<StopLauncherWidgetAction>()
    else actionStartActivity(Intent(LocalContext.current, ScStartActivity::class.java))

class StopLauncherWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        LauncherManager.stopService(context)
    }
}

class RestartLauncherWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        if (!CoreServiceManager.connectionState.value.isRunning) return
        LauncherManager.restartService(context)
    }
}

class TestLauncherWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        MessageHelper.sendMsg2Service(context, AppConfig.MSG_MEASURE_DELAY, "")
    }
}

class LauncherWidgetWorkerService : RemoteWorkerService()

class LauncherWidgetInvisibleActionActivity : InvisibleActionTrampolineActivity()

class LauncherWidgetActionActivity : ActionTrampolineActivity()

class LauncherWidgetActionReceiver : ActionCallbackBroadcastReceiver()

class LauncherWidgetRemoteViewsService : GlanceRemoteViewsService()

class LauncherWidgetPackageReplacedReceiver : MyPackageReplacedReceiver()
