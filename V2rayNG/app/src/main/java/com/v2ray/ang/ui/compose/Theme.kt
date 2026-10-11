package com.v2ray.ang.ui.compose

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---- Matixray palette: flat charcoal canvas and cards in the style of the Shadow VPN kit, with the brand lilac where that kit has blue ----
val XfBackground = Color(0xFF0B1016)
val XfCard = Color(0xFF131A22)
val XfPurple = Color(0xFFB69BFF)
val XfPurpleDark = Color(0xFF9B7BF0)
val XfPurpleLight = Color(0xFFDACEFF)
val XfTextMain = Color(0xFFF3F5F9)
val XfTextSub = Color(0xFF8E98A8)
val XfTextMuted = Color(0xFF5F6978)
val XfBorder = Color(0xFF222B36)
val XfGreen = Color(0xFF22C55E)
val XfYellow = Color(0xFFFACC15)
// Psiphon section: orange, so it reads apart from the violet main section
val XfOrange = Color(0xFFFF8A1F)
val XfOrangeDark = Color(0xFFE5700A)
val XfOrangeLight = Color(0xFFFFC38A)
val XfRed = Color(0xFFEF4444)
val XfProgressTrack = Color(0xFF1B232D)

private val LightColor = lightColorScheme(
    primary = Color(0xFF8A63E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFE7FF),
    onPrimaryContainer = Color(0xFF2A1560),
    secondary = Color(0xFF9A78EE),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1EBFF),
    onSecondaryContainer = Color(0xFF2E1A66),
    tertiary = Color(0xFF16A34A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC9F4D7),
    onTertiaryContainer = Color(0xFF05301A),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF0B1016),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1016),
    surfaceVariant = Color(0xFFE9EDF3),
    onSurfaceVariant = Color(0xFF586273),
    outline = Color(0xFF8A94A6),
    outlineVariant = Color(0x338A63E6),
    inverseSurface = Color(0xFF241A4A),
    inverseOnSurface = Color(0xFFF4F1FF),
    inversePrimary = Color(0xFFC9B6FF),
    scrim = Color(0xFF000000),
    surfaceTint = Color(0xFF8A63E6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFEFF2F7),
    surfaceContainerHigh = Color(0xFFE8ECF2),
    surfaceContainerHighest = Color(0xFFE0E5ED),
)

private val DarkColor = darkColorScheme(
    primary = XfPurple,
    onPrimary = Color(0xFF22104F),
    primaryContainer = Color(0xFF3B2E66),
    onPrimaryContainer = Color(0xFFEADFFF),
    secondary = Color(0xFFCDBBFF),
    onSecondary = Color(0xFF14083F),
    secondaryContainer = Color(0xFF2F2650),
    onSecondaryContainer = Color(0xFFE7E0FF),
    tertiary = XfGreen,
    onTertiary = Color(0xFF03210F),
    tertiaryContainer = Color(0xFF0F3D22),
    onTertiaryContainer = Color(0xFFBBF7D0),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
    errorContainer = Color(0xFF4A0F0F),
    onErrorContainer = Color(0xFFFECACA),
    background = XfBackground,
    onBackground = XfTextMain,
    surface = Color(0xFF10161D),
    onSurface = XfTextMain,
    surfaceVariant = Color(0xFF1B232D),
    onSurfaceVariant = XfTextSub,
    outline = XfTextMuted,
    outlineVariant = Color(0xFF222B36),
    inverseSurface = XfTextMain,
    inverseOnSurface = XfBackground,
    inversePrimary = XfPurpleDark,
    scrim = Color(0xFF000000),
    surfaceTint = XfPurple,
    surfaceContainerLowest = Color(0xFF080C11),
    surfaceContainerLow = Color(0xFF0E141A),
    surfaceContainer = XfCard,
    surfaceContainerHigh = Color(0xFF18212A),
    surfaceContainerHighest = Color(0xFF1F2933),
)

/** Vazirmatn (static weights in res/font), used for every text style of the app. */
@Composable
fun rememberAppFontFamily(): FontFamily = remember {
    FontFamily(
        Font(R.font.vazirmatn_regular, FontWeight.Normal),
        Font(R.font.vazirmatn_medium, FontWeight.Medium),
        Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
        Font(R.font.vazirmatn_bold, FontWeight.Bold),
    )
}

private fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    titleMedium = titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

// Semantic Colors
val colorPing = Color(0xFF009966) // Green
val colorPingRed = Color(0xFFFF4D6D) // Red
val colorConfigType = Color(0xFFC9B6FF) // Lilac
val colorFabActive = Color(0xFFB69BFF) // Brand lilac
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD97E5BD0) // Lilac
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
    val useDark = darkTheme
    val colorScheme = if (useDark) DarkColor else LightColor
    val fontFamily = rememberAppFontFamily()
    val typography = remember(fontFamily) { Typography().withFontFamily(fontFamily) }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !useDark
                isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides useDark,
        LocalGlassStyle provides if (useDark) DarkGlassStyle else LightGlassStyle,
        LocalAppSnackbar provides snackbarController,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppBackdrop()
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
