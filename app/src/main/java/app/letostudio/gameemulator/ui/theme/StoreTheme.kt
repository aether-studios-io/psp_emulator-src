package app.letostudio.gameemulator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Set B "store style" palette (dark navy, blue/orange glow) used by onboarding and the
 * paywall whatever the app theme is. Shared components pick it up through [RnTheme.colors].
 */
val RnStore = RnDark.copy(
    ground = Color(0xFF050B18),
    surface = Color(0x0DFFFFFF),
    surfaceSunken = Color(0x12FFFFFF),
    tint = Color(0x3D0070D0),
    line = Color(0x29FFFFFF),
    ink = Color(0xFFFFFFFF),
    inkMuted = Color(0xFF9FB3D1),
    primary = Color(0xFF0070D0),
    primaryStrong = Color(0xFF6AAEFF),
)

/** Set B text and accent colours that have no palette slot. */
object StoreColors {
    val Highlight = Color(0xFF6AAEFF)
    val Body = Color(0xFFBFD0EA)
    val Fine = Color(0xFF8796B0)
    val Cta = Color(0xFF0070D0)
    val Orange = Color(0xFFFF9A4D)
    val OnOrange = Color(0xFF1A0B05)
    val DeviceFill = Color(0xFF0E1626)
    val DeviceEdge = Color(0xFF3C5A8C)
    val CardInk = Color(0xFF0F1724)
    val CardMuted = Color(0xFF576173)
}

private var activeStoreScreens = 0

/** Dark store palette plus light status/navigation bar icons while shown. */
@Composable
fun RnStoreTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val outerDark = LocalIsDarkTheme.current
    if (!view.isInEditMode) {
        // Counted: onboarding → paywall overlap during the transition, and the leaving
        // screen must not restore dark icons over the one entering.
        DisposableEffect(Unit) {
            val window = (view.context as? android.app.Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            activeStoreScreens++
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose {
                activeStoreScreens--
                if (activeStoreScreens == 0) {
                    controller?.isAppearanceLightStatusBars = !outerDark
                    controller?.isAppearanceLightNavigationBars = !outerDark
                }
            }
        }
    }
    CompositionLocalProvider(LocalIsDarkTheme provides true, LocalRnColors provides RnStore, LocalExtendedColors provides ExtendedDark) {
        MaterialTheme(colorScheme = DarkColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
