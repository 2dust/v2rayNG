package com.v2ray.ang.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.work.multiprocess.RemoteWorkerService
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.shortcut.ScStartActivity

private val HORIZONTAL_WIDGET_HEIGHT = 64.dp
private val ACTION_BUTTON_SIZE = 48.dp
private val ACTION_EDGE_GAP = 8.dp

private val ACTIVE_COLOR = ColorProvider(Color(0xFFF97910))
private val INACTIVE_COLOR = ColorProvider(Color(0xFF9C9C9C))
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
    val layout = LauncherWidgetLayout.forWidth(LocalSize.current.width.value)
    when (layout) {
        LauncherWidgetLayout.COMPACT -> CompactWidget(uiState)
        LauncherWidgetLayout.HORIZONTAL -> HorizontalWidget(
            uiState = uiState,
            openAppAction = openAppAction,
        )
    }
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
    openAppAction: Action,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(HORIZONTAL_WIDGET_HEIGHT)
                .background(
                    imageProvider = ImageProvider(R.drawable.widget_background),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
                )
                .appWidgetBackground(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(if (uiState.isRunning) ACTIVE_COLOR else INACTIVE_COLOR),
            ) {}
            ServerText(
                profileName = uiState.profileName,
                openAppAction = openAppAction,
            )
            ServiceButton(uiState)
            Spacer(GlanceModifier.width(ACTION_EDGE_GAP))
        }
    }
}

@Composable
private fun RowScope.ServerText(
    profileName: String,
    openAppAction: Action,
) {
    val modifier = GlanceModifier
        .defaultWeight()
        .fillMaxHeight()
        .padding(start = 4.dp)
        .clickable(openAppAction)
        .semantics { contentDescription = profileName }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = profileName,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun ServiceButton(uiState: LauncherWidgetUiState) {
    Box(
        modifier = GlanceModifier
            .size(ACTION_BUTTON_SIZE)
            .background(
                if (uiState.isRunning) ImageProvider(R.drawable.ic_rounded_corner_active)
                else ImageProvider(R.drawable.ic_rounded_corner_inactive)
            )
            .clickable(serviceAction(uiState.isRunning))
            .semantics {
                contentDescription = if (uiState.isRunning) uiState.stopActionLabel
                else uiState.startActionLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (uiState.isRunning) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp
            ),
            contentDescription = null,
            modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(ICON_COLOR),
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

class LauncherWidgetWorkerService : RemoteWorkerService()

class LauncherWidgetInvisibleActionActivity : InvisibleActionTrampolineActivity()

class LauncherWidgetActionActivity : ActionTrampolineActivity()

class LauncherWidgetActionReceiver : ActionCallbackBroadcastReceiver()

class LauncherWidgetRemoteViewsService : GlanceRemoteViewsService()

class LauncherWidgetPackageReplacedReceiver : MyPackageReplacedReceiver()
