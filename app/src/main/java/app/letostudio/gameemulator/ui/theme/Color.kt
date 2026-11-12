package app.letostudio.gameemulator.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Mirrors docs/spec/design_token.json (RetroNova "PSP_Design system") exactly — change the
// JSON first, then this file. Material 3 `primary` maps to primaryStrong so filled buttons
// pass AA with onPrimary; brandFill (#0070D0) is for icon tiles, toggles, sliders, progress.

/** Every design token colour, light or dark. Read through [RnTheme.colors]. */
@Immutable
data class RnColors(
    val ground: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val tint: Color,
    val line: Color,
    val ink: Color,
    val inkMuted: Color,
    /** Brand fill #0070D0: icon tiles, toggles, sliders, progress, A button. */
    val primary: Color,
    val onPrimary: Color,
    /** #004CA0: CTAs and brand text (M3 primary). */
    val primaryStrong: Color,
    /** Navy #002470: hero blocks, continue card. */
    val primaryDeep: Color,
    val accent: Color,
    val ember: Color,
    val success: Color,
    val danger: Color,
    val glass: Color,
    val onGlass: Color,
    val scrim: Color,
    val focus: Color,
    val isDark: Boolean,
)

val RnLight = RnColors(
    ground = Color(0xFFF3F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFE8ECF3),
    tint = Color(0xFFE3EEFF),
    line = Color(0xFFD6DCE6),
    ink = Color(0xFF0F1724),
    inkMuted = Color(0xFF576173),
    primary = Color(0xFF0070D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryStrong = Color(0xFF004CA0),
    primaryDeep = Color(0xFF002470),
    accent = Color(0xFFB24A00),
    ember = Color(0xFF855400),
    success = Color(0xFF18703F),
    danger = Color(0xFFB8231B),
    glass = Color(0x8F080C14),
    onGlass = Color(0xFFFFFFFF),
    scrim = Color(0x7A060A12),
    focus = Color(0xFFB24A00),
    isDark = false,
)

val RnDark = RnColors(
    ground = Color(0xFF0A0E16),
    surface = Color(0xFF131A26),
    surfaceSunken = Color(0xFF070A10),
    tint = Color(0xFF0F2547),
    line = Color(0xFF243044),
    ink = Color(0xFFEEF3FB),
    inkMuted = Color(0xFF9AA6BA),
    primary = Color(0xFF2F86FF),
    onPrimary = Color(0xFF04122B),
    primaryStrong = Color(0xFF6AAEFF),
    primaryDeep = Color(0xFF002470),
    accent = Color(0xFFFF9A4D),
    ember = Color(0xFFFFB938),
    success = Color(0xFF4ED38C),
    danger = Color(0xFFFF6B5E),
    glass = Color(0x9E080C14),
    onGlass = Color(0xFFFFFFFF),
    scrim = Color(0xA3000000),
    focus = Color(0xFFFF9A4D),
    isDark = true,
)

/** Token → Material 3 role mapping from design_token.json `colorRoles`. */
private fun RnColors.toScheme() = if (isDark) {
    darkColorScheme(
        primary = primaryStrong, onPrimary = onPrimary,
        primaryContainer = tint, onPrimaryContainer = primaryStrong,
        secondary = accent, onSecondary = onPrimary,
        tertiary = ember,
        background = ground, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = surfaceSunken, onSurfaceVariant = inkMuted,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerLow = surface,
        surfaceContainerHighest = surface, surfaceContainerLowest = surface,
        outline = line, outlineVariant = line,
        error = danger, onError = onPrimary,
        scrim = scrim,
    )
} else {
    lightColorScheme(
        primary = primaryStrong, onPrimary = onPrimary,
        primaryContainer = tint, onPrimaryContainer = primaryStrong,
        secondary = accent, onSecondary = onPrimary,
        tertiary = ember,
        background = ground, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = surfaceSunken, onSurfaceVariant = inkMuted,
        surfaceContainer = surface, surfaceContainerHigh = surface, surfaceContainerLow = surface,
        surfaceContainerHighest = surface, surfaceContainerLowest = surface,
        outline = line, outlineVariant = line,
        error = danger, onError = onPrimary,
        scrim = scrim,
    )
}

val LightColors = RnLight.toScheme()
val DarkColors = RnDark.toScheme()

/** Template compatibility shim: older components still read these few roles. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
) {
    val brandGradient: Brush get() = Brush.horizontalGradient(listOf(gradientStart, gradientEnd))
}

val ExtendedLight = ExtendedColors(RnLight.success, RnLight.primaryStrong, RnLight.primaryStrong)
val ExtendedDark = ExtendedColors(RnDark.success, RnDark.primaryStrong, RnDark.primaryStrong)

val LocalExtendedColors = staticCompositionLocalOf { ExtendedLight }
val LocalRnColors = staticCompositionLocalOf { RnLight }

/** Player screens are always dark (game picture), whatever the app theme. */
object PlayerColors {
    val background = Color(0xFF000000)
    val scrim = Color(0x99000000)
    val control = Color(0xFFFFFFFF)
}
