package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFF805522),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E2CB),
    onPrimaryContainer = Color(0xFF35220C),
    secondary = Color(0xFF62625E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E6E0),
    onSecondaryContainer = Color(0xFF242522),
    tertiary = Color(0xFF356B57),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCEEE4),
    onTertiaryContainer = Color(0xFF12382A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F4F1),
    onBackground = Color(0xFF202120),
    surface = Color(0xFFF5F4F1),
    onSurface = Color(0xFF202120),
    surfaceVariant = Color(0xFFE9E7E2),
    onSurfaceVariant = Color(0xFF5C5D59),
    outline = Color(0xFF7A7B76),
    outlineVariant = Color(0xFFD4D3CD),
    inverseSurface = Color(0xFF292B2D),
    inverseOnSurface = Color(0xFFF4F2ED),
    inversePrimary = Color(0xFFE8B778),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF805522),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFEFEB),
    surfaceContainer = Color(0xFFEAEAE6),
    surfaceContainerHigh = Color(0xFFE4E4DF),
    surfaceContainerHighest = Color(0xFFDEDED8),
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFFE8B778),
    onPrimary = Color(0xFF2C1D0B),
    primaryContainer = Color(0xFF49351E),
    onPrimaryContainer = Color(0xFFF6DFBF),
    secondary = Color(0xFFC8C6BF),
    onSecondary = Color(0xFF292A27),
    secondaryContainer = Color(0xFF393B3A),
    onSecondaryContainer = Color(0xFFE9E7E0),
    tertiary = Color(0xFF9DCEB7),
    onTertiary = Color(0xFF103627),
    tertiaryContainer = Color(0xFF264D3D),
    onTertiaryContainer = Color(0xFFDCEEE4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17191B),
    onBackground = Color(0xFFF1F0EB),
    surface = Color(0xFF17191B),
    onSurface = Color(0xFFF1F0EB),
    surfaceVariant = Color(0xFF303336),
    onSurfaceVariant = Color(0xFFBCBEBB),
    outline = Color(0xFF929590),
    outlineVariant = Color(0xFF424649),
    inverseSurface = Color(0xFFF1F0EB),
    inverseOnSurface = Color(0xFF202224),
    inversePrimary = Color(0xFF805522),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFFE8B778),
    surfaceContainerLowest = Color(0xFF111315),
    surfaceContainerLow = Color(0xFF1D2022),
    surfaceContainer = Color(0xFF232628),
    surfaceContainerHigh = Color(0xFF2C3033),
    surfaceContainerHighest = Color(0xFF363A3D),
)

// Semantic colors stay distinct from the decorative glass treatment.
val colorPing = Color(0xFF356B57)
val colorPingRed = Color(0xFFBA1A1A)
val colorConfigType = Color(0xFF805522)
/**
 * One corner language for the whole app: chips, cards, dialogs and menus all draw from here, so
 * screens written at different times still look like the same product.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * Tone for a measured delay: calm while it is fast, warm when it drags, hard red when it failed.
 * Reading it from the scheme keeps the same meaning in light, dark and dynamic color.
 */
@Composable
fun rxPingColor(delayMs: Long): Color = when {
    delayMs < 0L -> MaterialTheme.colorScheme.error
    delayMs <= 400L -> MaterialTheme.colorScheme.tertiary
    delayMs <= 900L -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.error
}

/** Backing tint for [rxPingColor]; same hue, low enough to keep the label readable on top. */
@Composable
fun rxPingContainer(delayMs: Long): Color = rxPingColor(delayMs).copy(alpha = 0.14f)

val colorFabActive = Color(0xFF805522)
val colorFabInactiveLight = Color(0xFF62625E)
val colorFabInactiveDark = Color(0xFF424649)
val dividerColorLight = Color(0xFFD4D3CD)
val dividerColorDark = Color(0xFF424649)

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    // Only the missing-key default changes; an explicit saved theme is preserved.
    const val DEFAULT_UI_MODE = "2"

    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, DEFAULT_UI_MODE) ?: DEFAULT_UI_MODE
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
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
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, DEFAULT_UI_MODE) ?: DEFAULT_UI_MODE
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, false)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return resolveDarkTheme(mode, isSystemInDarkTheme())
}

internal fun resolveDarkTheme(mode: String, systemDark: Boolean): Boolean = when (mode) {
    "1" -> false
    "2" -> true
    else -> systemDark
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
            window.decorView.setBackgroundColor(colorScheme.background.toArgb())
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
            colorScheme = colorScheme,
            shapes = AppShapes,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
