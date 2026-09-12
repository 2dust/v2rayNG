package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// SkyVPN brand tokens (see brief section 1)
val skyLime = Color(0xFFC6FF2E)
val skyLimeDim = Color(0xFF8FB800)
val skyLimeSoft = Color(0x24C6FF2E) // ~14% lime
val skyBgDark = Color(0xFF0A0B08)
val skyBgLight = Color(0xFFF3F5EC)
val skyTextDark = Color(0xFFF4F6EE) // text on dark bg
val skyTextLight = Color(0xFF10120A) // text on light bg
val skyBtnIdle1 = Color(0xFF202218)
val skyBtnIdle2 = Color(0xFF101208)

private val LightColor = lightColorScheme(
    primary = skyLimeDim, // dark-enough lime to hold contrast on a light bg
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F6B0),
    onPrimaryContainer = Color(0xFF283300),
    secondary = skyLimeDim,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3F6B0),
    onSecondaryContainer = Color(0xFF283300),
    tertiary = Color(0xFF3D6B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCF3AE),
    onTertiaryContainer = Color(0xFF223200),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = skyBgLight,
    onBackground = skyTextLight,
    surface = skyBgLight,
    onSurface = skyTextLight,
    surfaceVariant = Color(0xFFEAEBE2),
    onSurfaceVariant = Color(0xFF666860),
    outline = Color(0xFFAFC17A),
    outlineVariant = Color(0xFFD8E2BA),
    inverseSurface = Color(0xFF10120A),
    inverseOnSurface = skyBgLight,
    inversePrimary = skyLime,
    scrim = Color(0xFF000000),
    surfaceTint = skyLimeDim,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9F2),
    surfaceContainer = Color(0xFFF0F2E6),
    surfaceContainerHigh = Color(0xFFEAEBE2),
    surfaceContainerHighest = Color(0xFFE2E4D6),
)

private val DarkColor = darkColorScheme(
    primary = skyLime,
    onPrimary = Color(0xFF10120A),
    primaryContainer = Color(0xFF262D0C),
    onPrimaryContainer = Color(0xFFE8FFB8),
    secondary = skyLimeDim,
    onSecondary = Color(0xFF10120A),
    secondaryContainer = Color(0xFF23290A),
    onSecondaryContainer = Color(0xFFDFF08A),
    tertiary = skyLimeDim,
    onTertiary = Color(0xFF10120A),
    tertiaryContainer = Color(0xFF23290A),
    onTertiaryContainer = Color(0xFFDFF08A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = skyBgDark,
    onBackground = skyTextDark,
    surface = skyBgDark,
    onSurface = skyTextDark,
    surfaceVariant = Color(0xFF1E1F1C),
    onSurfaceVariant = Color(0xFF9B9D97),
    outline = Color(0xFF3A4A12),
    outlineVariant = Color(0xFF20260C),
    inverseSurface = skyTextDark,
    inverseOnSurface = Color(0xFF10120A),
    inversePrimary = Color(0xFF4E5E00),
    scrim = Color(0xFF000000),
    surfaceTint = skyLime,
    surfaceContainerLowest = Color(0xFF050603),
    surfaceContainerLow = skyBtnIdle2,
    surfaceContainer = Color(0xFF161714),
    surfaceContainerHigh = Color(0xFF1E1F1C),
    surfaceContainerHighest = Color(0xFF262720),
)

// Semantic Colors
val colorPing = Color(0xFF8FB800) // Lime-dim (good ping)
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = skyLimeDim
val colorFabActive = skyLime
val colorFabInactiveLight = skyBtnIdle1
val colorFabInactiveDark = skyBtnIdle1
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 70%
val toastNormalBgLight = Color(0xB3353A3E) // Dark Gray
val toastNormalBgDark = Color(0xB34A4F54) // Darker Gray
val toastSuccessBg = Color(0xB3388E3C) // Green
val toastErrorBg = Color(0xB3D50000) // Red
val toastInfoBg = Color(0xB33F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        // SkyVPN ships a fixed lime/black brand palette by default; a user can still
        // opt into Material You dynamic color from Settings if that toggle exists.
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
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
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
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
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
