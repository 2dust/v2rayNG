package com.v2ray.ang.compose

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFF000000), // Black 黑色
    onPrimary = Color(0xFFFFFFFF), // White 白色
    primaryContainer = Color(0xFFE0E0E0), // Light Gray 浅灰色
    onPrimaryContainer = Color(0xFF000000), // Black 黑色
    secondary = Color(0xFFf97910), // Orange 橙色
    onSecondary = Color(0xFFFFFFFF), // White 白色
    secondaryContainer = Color(0xFFFFE8D6), // Pale Orange 淡橙色
    onSecondaryContainer = Color(0xFF2B1700), // Dark Brown 深棕色
    tertiary = Color(0xFF009966), // Green 绿色
    onTertiary = Color(0xFFFFFFFF), // White 白色
    tertiaryContainer = Color(0xFFA0F2D0), // Light Green 浅绿色
    onTertiaryContainer = Color(0xFF00201A), // Dark Teal 深蓝绿色
    error = Color(0xFFBA1A1A), // Red 红色
    errorContainer = Color(0xFFFFDAD6), // Light Red 浅红色
    onError = Color(0xFFFFFFFF), // White 白色
    onErrorContainer = Color(0xFF410002), // Dark Red 暗红色
    background = Color(0xFFFFFFFF), // White 白色
    onBackground = Color(0xFF1C1B1F), // Near Black 近黑色
    surface = Color(0xFFFFFFFF), // White 白色
    onSurface = Color(0xFF1C1B1F), // Near Black 近黑色
    surfaceVariant = Color(0xFFE7E0EC), // Light Purple Gray 浅紫灰色
    onSurfaceVariant = Color(0xFF49454F), // Dark Gray 深灰色
    outline = Color(0xFF79747E), // Medium Gray 中灰色
    outlineVariant = Color(0xFFCAC4D0), // Light Gray 浅灰色
    inverseSurface = Color(0xFF313033), // Dark Gray 深灰色
    inverseOnSurface = Color(0xFFF4EFF4), // Very Light Gray 极浅灰色
    inversePrimary = Color(0xFFC0C0C0), // Silver Gray 银灰色
    scrim = Color(0xFF000000), // Black 黑色
    surfaceTint = Color(0xFF000000), // Black 黑色
    surfaceContainerLowest = Color(0xFFFFFFFF), // White 白色
    surfaceContainerLow = Color(0xFFF7F7F7), // Very Light Gray 极浅灰色
    surfaceContainer = Color(0xFFF1F1F1), // Light Gray 浅灰色
    surfaceContainerHigh = Color(0xFFEBEBEB), // Light Gray 浅灰色
    surfaceContainerHighest = Color(0xFFE5E5E5), // Light Gray 浅灰色
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFFC0C0C0), // Silver Gray 银灰色
    onPrimary = Color(0xFF303030), // Dark Gray 深灰色
    primaryContainer = Color(0xFF474747), // Gray 灰色
    onPrimaryContainer = Color(0xFFE0E0E0), // Light Gray 浅灰色
    secondary = Color(0xFFf97910), // Orange 橙色
    onSecondary = Color(0xFF4E2600), // Dark Brown 深棕色
    secondaryContainer = Color(0xFF6F3800), // Brown 棕色
    onSecondaryContainer = Color(0xFFFFE8D6), // Pale Orange 淡橙色
    tertiary = Color(0xFF83D6B5), // Mint Green 薄荷绿
    onTertiary = Color(0xFF00382E), // Dark Teal 深蓝绿色
    tertiaryContainer = Color(0xFF005143), // Teal 蓝绿色
    onTertiaryContainer = Color(0xFFA0F2D0), // Light Green 浅绿色
    error = Color(0xFFFFB4AB), // Light Red 浅红色
    errorContainer = Color(0xFF93000A), // Dark Red 暗红色
    onError = Color(0xFF690005), // Deep Red 深红色
    onErrorContainer = Color(0xFFFFDAD6), // Light Red 浅红色
    background = Color(0xFF1C1B1F), // Near Black 近黑色
    onBackground = Color(0xFFE6E1E5), // Light Gray 浅灰色
    surface = Color(0xFF1C1B1F), // Near Black 近黑色
    onSurface = Color(0xFFE6E1E5), // Light Gray 浅灰色
    surfaceVariant = Color(0xFF49454F), // Dark Gray 深灰色
    onSurfaceVariant = Color(0xFFCAC4D0), // Light Gray 浅灰色
    outline = Color(0xFF938F99), // Grayish Purple 灰紫色
    outlineVariant = Color(0xFF49454F), // Dark Gray 深灰色
    inverseSurface = Color(0xFFE6E1E5), // Light Gray 浅灰色
    inverseOnSurface = Color(0xFF1C1B1F), // Near Black 近黑色
    inversePrimary = Color(0xFF000000), // Black 黑色
    scrim = Color(0xFF000000), // Black 黑色
    surfaceTint = Color(0xFFC0C0C0), // Silver Gray 银灰色
    surfaceContainerLowest = Color(0xFF0F0F12), // Near Black 近黑色
    surfaceContainerLow = Color(0xFF1A191D), // Dark Gray 深灰色
    surfaceContainer = Color(0xFF1E1D21), // Dark Gray 深灰色
    surfaceContainerHigh = Color(0xFF282729), // Dark Gray 深灰色
    surfaceContainerHighest = Color(0xFF333234), // Dark Gray 深灰色
)

// Semantic Colors
val colorPing = Color(0xFF009966) // Green 绿色
val colorPingRed = Color(0xFFFF0099) // Pink Red 粉红色
val colorConfigType = Color(0xFFf97910) // Orange 橙色
val colorFabActive = Color(0xFFf97910) // Orange 橙色
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray 灰色
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray 深灰色
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray 浅灰色
val dividerColorDark = Color(0xFF424242) // Dark Gray 深灰色

// Toast Colors 70%
val toastNormalBgLight = Color(0xB3353A3E) // Dark Gray 深灰色
val toastNormalBgDark = Color(0xB34A4F54) // Darker Gray 暗灰色
val toastSuccessBg = Color(0xB3388E3C) // Green 绿色
val toastErrorBg = Color(0xB3D50000) // Red 红色
val toastInfoBg = Color(0xB33F51B5) // Indigo Blue 靛蓝色
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White 半透明白色
val toastTextColor = Color.White // White 白色

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColor else LightColor
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                content()

                AppSnackbarBridge(controller = snackbarController)
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
