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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.repository.ThemeRepository
import kotlinx.coroutines.flow.StateFlow

// Light color scheme with color comments
private val LightColor = lightColorScheme(
    primary = Color(0xFF000000), // Black
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFE0E0E0), // Light Gray
    onPrimaryContainer = Color(0xFF000000), // Black
    secondary = Color(0xFFF97910), // Orange
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFFFE8D6), // Pale Orange
    onSecondaryContainer = Color(0xFF2B1700), // Dark Brown
    tertiary = Color(0xFF009966), // Green
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFA0F2D0), // Light Green
    onTertiaryContainer = Color(0xFF00201A), // Dark Teal
    error = Color(0xFFBA1A1A), // Red
    errorContainer = Color(0xFFFFDAD6), // Light Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF410002), // Dark Red
    background = Color(0xFFFFFFFF), // White
    onBackground = Color(0xFF1C1B1F), // Near Black
    surface = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFF1C1B1F), // Near Black
    surfaceVariant = Color(0xFFE7E0EC), // Light Purple Gray
    onSurfaceVariant = Color(0xFF49454F), // Dark Gray
    outline = Color(0xFF79747E), // Medium Gray
    outlineVariant = Color(0xFFCAC4D0), // Light Gray
    inverseSurface = Color(0xFF313033), // Dark Gray
    inverseOnSurface = Color(0xFFF4EFF4), // Very Light Gray
    inversePrimary = Color(0xFFC0C0C0), // Silver Gray
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF000000), // Black
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF7F7F7), // Very Light Gray
    surfaceContainer = Color(0xFFF1F1F1), // Light Gray
    surfaceContainerHigh = Color(0xFFEBEBEB), // Light Gray
    surfaceContainerHighest = Color(0xFFE5E5E5) // Light Gray
)

// Dark color scheme with color comments
private val DarkColor = darkColorScheme(
    primary = Color(0xFFC0C0C0), // Silver Gray
    onPrimary = Color(0xFF303030), // Dark Gray
    primaryContainer = Color(0xFF474747), // Gray
    onPrimaryContainer = Color(0xFFE0E0E0), // Light Gray
    secondary = Color(0xFFF97910), // Orange
    onSecondary = Color(0xFF4E2600), // Dark Brown
    secondaryContainer = Color(0xFF6F3800), // Brown
    onSecondaryContainer = Color(0xFFFFE8D6), // Pale Orange
    tertiary = Color(0xFF83D6B5), // Mint Green
    onTertiary = Color(0xFF00382E), // Dark Teal
    tertiaryContainer = Color(0xFF005143), // Teal
    onTertiaryContainer = Color(0xFFA0F2D0), // Light Green
    error = Color(0xFFFFB4AB), // Light Red
    errorContainer = Color(0xFF93000A), // Dark Red
    onError = Color(0xFF690005), // Deep Red
    onErrorContainer = Color(0xFFFFDAD6), // Light Red
    background = Color(0xFF1C1B1F), // Near Black
    onBackground = Color(0xFFE6E1E5), // Light Gray
    surface = Color(0xFF1C1B1F), // Near Black
    onSurface = Color(0xFFE6E1E5), // Light Gray
    surfaceVariant = Color(0xFF49454F), // Dark Gray
    onSurfaceVariant = Color(0xFFCAC4D0), // Light Gray
    outline = Color(0xFF938F99), // Grayish Purple
    outlineVariant = Color(0xFF49454F), // Dark Gray
    inverseSurface = Color(0xFFE6E1E5), // Light Gray
    inverseOnSurface = Color(0xFF1C1B1F), // Near Black
    inversePrimary = Color(0xFF000000), // Black
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFFC0C0C0), // Silver Gray
    surfaceContainerLowest = Color(0xFF0F0F12), // Near Black
    surfaceContainerLow = Color(0xFF1A191D), // Dark Gray
    surfaceContainer = Color(0xFF1E1D21), // Dark Gray
    surfaceContainerHigh = Color(0xFF282729), // Dark Gray
    surfaceContainerHighest = Color(0xFF333234) // Dark Gray
)

/**
 * Semantic colors used across the app (toasts, dividers, FAB states, etc.)
 */
@Immutable
data class AppSemanticColors(
    val pingBad: Color,
    val fabInactive: Color,
    val fabContent: Color,
    val divider: Color,
    val toastBackground: Color,
    val toastSuccess: Color,
    val toastError: Color,
    val toastInfo: Color,
    val toastContent: Color
)

private val LightSemanticColors = AppSemanticColors(
    pingBad = Color(0xFFFF0099),   // Pink Red
    fabInactive = Color(0xFF9C9C9C), // Gray
    fabContent = Color.White,       // White
    divider = Color(0xFFE0E0E0),    // Light Gray
    toastBackground = Color(0xB3353A3E), // Dark Gray (70%)
    toastSuccess = Color(0xB3388E3C), // Green (70%)
    toastError = Color(0xB3D50000),   // Red (70%)
    toastInfo = Color(0xB33F51B5),    // Indigo Blue (70%)
    toastContent = Color.White      // White
)

private val DarkSemanticColors = AppSemanticColors(
    pingBad = Color(0xFFFF0099),
    fabInactive = Color(0xFF646464),
    fabContent = Color.White,
    divider = Color(0xFF424242),
    toastBackground = Color(0xB34A4F54),
    toastSuccess = Color(0xB3388E3C),
    toastError = Color(0xB3D50000),
    toastInfo = Color(0xB33F51B5),
    toastContent = Color.White
)

// Additional global color constants (used elsewhere in the app)
val colorPing = Color(0xFF009966)       // Green
val colorPingRed = Color(0xFFFF0099)    // Pink Red
val colorConfigType = Color(0xFFF97910) // Orange
val colorFabActive = Color(0xFFF97910)  // Orange
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White

// Composition locals
val LocalAppColors = staticCompositionLocalOf { LightSemanticColors }
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * App theme mode enumeration
 */
enum class AppThemeMode(val value: String) {
    System("0"),
    Light("1"),
    Dark("2");

    companion object {
        fun from(raw: String?): AppThemeMode = entries.firstOrNull { it.value == raw } ?: System
    }
}

/**
 * UI-facing facade over [ThemeRepository], which owns the single source of truth for theming.
 * Kept as an object so existing call sites (Application bootstrap, settings screen) stay valid.
 */
object ThemeManager {
    val mode: StateFlow<AppThemeMode> = ThemeRepository.themeMode
    val dynamicColorEnabled: StateFlow<Boolean> = ThemeRepository.dynamicColorEnabled
    val isDynamicColorSupported: Boolean get() = ThemeRepository.isDynamicColorSupported

    fun setMode(mode: AppThemeMode) = ThemeRepository.setThemeMode(mode)

    fun setDynamicColorEnabled(enabled: Boolean) = ThemeRepository.setDynamicColorEnabled(enabled)

    fun refresh() = ThemeRepository.refresh()
}

/**
 * Resolve whether dark theme should be applied based on user preference and system setting.
 */
@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.mode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    return when (mode) {
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
        AppThemeMode.System -> systemDark
    }
}

/**
 * App theme composable that provides MaterialTheme, semantic colors, and snackbar support.
 *
 * Monet dynamic color is opt-in and silently ignored below Android 12; the semantic palette
 * (toasts, dividers, ping colors) stays brand-owned in both cases.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val useDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = remember(useDynamicColor, darkTheme, context) {
        when {
            useDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
            useDynamicColor -> dynamicLightColorScheme(context)
            darkTheme -> DarkColor
            else -> LightColor
        }
    }
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors
    val snackbarController = rememberAppSnackbarController()
    val view = LocalView.current

    if (!view.isInEditMode) {
        LaunchedEffect(view, darkTheme) {
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppColors provides semanticColors,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(colorScheme = colorScheme) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
