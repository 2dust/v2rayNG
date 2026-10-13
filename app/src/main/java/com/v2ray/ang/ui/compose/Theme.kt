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

private val LightColor = lightColorScheme(
    primary = Color(0xFF0F3D32), // Forest Emerald
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2F4EC),
    onPrimaryContainer = Color(0xFF053828),
    secondary = Color(0xFF05A870), // ExpressVPN Emerald
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4F3E6),
    onSecondaryContainer = Color(0xFF023623),
    tertiary = Color(0xFFD9383A), // Express Red
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD6),
    onTertiaryContainer = Color(0xFF410002),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color(0xFFFFFFFF),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F8F7),
    onBackground = Color(0xFF11231E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF11231E),
    surfaceVariant = Color(0xFFDEE5E1),
    onSurfaceVariant = Color(0xFF404A46),
    outline = Color(0xFF707A76),
    outlineVariant = Color(0xFFC0CBC6),
    inverseSurface = Color(0xFF2B3632),
    inverseOnSurface = Color(0xFFECF3EE),
    inversePrimary = Color(0xFF53DB9F),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF0F3D32),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F2),
    surfaceContainer = Color(0xFFE9EFEA),
    surfaceContainerHigh = Color(0xFFE3E9E5),
    surfaceContainerHighest = Color(0xFFDDE3DF),
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF05A870), // ExpressVPN Emerald
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF134537),
    onPrimaryContainer = Color(0xFFA0F6CD),
    secondary = Color(0xFF45D99C),
    onSecondary = Color(0xFF003823),
    secondaryContainer = Color(0xFF194A3B),
    onSecondaryContainer = Color(0xFFA8F8D2),
    tertiary = Color(0xFFFFB4AB),
    onTertiary = Color(0xFF690005),
    tertiaryContainer = Color(0xFF93000A),
    onTertiaryContainer = Color(0xFFFFDAD6),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0B1714), // Deep Express Slate
    onBackground = Color(0xFFE2EDE7),
    surface = Color(0xFF10211D),
    onSurface = Color(0xFFE2EDE7),
    surfaceVariant = Color(0xFF3F4B46),
    onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF3F4B46),
    inverseSurface = Color(0xFFE2EDE7),
    inverseOnSurface = Color(0xFF2B3632),
    inversePrimary = Color(0xFF006D47),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF05A870),
    surfaceContainerLowest = Color(0xFF070F0D),
    surfaceContainerLow = Color(0xFF0E1C18),
    surfaceContainer = Color(0xFF142621),
    surfaceContainerHigh = Color(0xFF1C312B),
    surfaceContainerHighest = Color(0xFF243B34),
)

// Semantic Colors
val colorPing = Color(0xFF05A870) // Express Green
val colorPingRed = Color(0xFFD9383A) // Express Red
val colorConfigType = Color(0xFF05A870) // Emerald
val colorFabActive = Color(0xFF05A870) // Express Emerald
val colorFabInactiveLight = Color(0xFF8E9995)
val colorFabInactiveDark = Color(0xFF55635E)
val dividerColorLight = Color(0xFFE2EBE6)
val dividerColorDark = Color(0xFF1E332D)

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
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
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
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
