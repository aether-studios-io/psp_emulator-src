package app.letostudio.gameemulator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { Light, Dark, System }

/**
 * The dark/light decision [AppTheme] actually resolved (System included). Use this, never
 * isSystemInDarkTheme(): the app setting overrides the system. App default: Dark (owner change 2026-11-21).
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

// Dynamic colour (Material You) deliberately OFF — token values only.
@Composable
fun AppTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    // Status/navigation bar icons follow the resolved theme (dark icons on Light).
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    RnThemeProvider(dark, content)
}

/** Forces a palette regardless of the setting — the Player is always dark. */
@Composable
fun RnThemeProvider(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalIsDarkTheme provides dark,
        LocalRnColors provides if (dark) RnDark else RnLight,
        LocalExtendedColors provides if (dark) ExtendedDark else ExtendedLight,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** Design-token accessors: `RnTheme.colors.primaryDeep`, `RnTheme.type.display`. */
object RnTheme {
    val colors: RnColors
        @Composable @ReadOnlyComposable
        get() = LocalRnColors.current
    val type: RnType
        @Composable @ReadOnlyComposable
        get() = RnTypeScale
}

// Accessor kept for template components.
object AppTheme {
    val extendedColors: ExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
