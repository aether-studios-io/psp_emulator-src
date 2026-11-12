package app.letostudio.gameemulator.analytics

import app.letostudio.gameemulator.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** analytics.md §2: every route is mapped deliberately, names are clean and unique. */
class ScreenNamesTest {

    @Test
    fun `every route is mapped - exhaustive, catches new routes`() {
        Routes.all.forEach { assertTrue("Route $it has no ScreenNames entry", ScreenNames.isMapped(it)) }
    }

    @Test
    fun `no name contains a route argument placeholder`() {
        Routes.all.mapNotNull(ScreenNames::forRoute).forEach { assertFalse(it.contains('{')) }
    }

    @Test
    fun `names are unique`() {
        val names = Routes.all.mapNotNull(ScreenNames::forRoute)
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `names are snake_case and within GA4 limits`() {
        Routes.all.mapNotNull(ScreenNames::forRoute).forEach {
            assertTrue(it.length <= AnalyticsConfig.MAX_PARAM_VALUE_LENGTH)
            assertTrue("'$it' is not snake_case", it.matches(Regex("[a-z][a-z0-9_]*")))
        }
    }

    @Test
    fun `spec screen names are all used somewhere`() {
        val viaRoutes = Routes.all.mapNotNull(ScreenNames::forRoute).toSet()
        val viaViewModels = setOf(
            AppScreens.ONBOARDING_1, AppScreens.ONBOARDING_2, AppScreens.ONBOARDING_3, AppScreens.PAYWALL,
            AppScreens.PLAYER, AppScreens.PAUSE_MENU, AppScreens.EMULATOR_SETTINGS, AppScreens.FIX_LAG_SHEET,
            AppScreens.HOMEBREW_GUIDE, AppScreens.CONTROLS_EDITOR,
        )
        val spec = listOf(
            "onboarding_1", "onboarding_2", "onboarding_3", "paywall", "library", "import", "game_detail", "homebrew",
            "player", "pause_menu", "controls_editor", "settings", "emulator_settings", "fix_lag_sheet", "homebrew_guide",
        )
        spec.forEach { assertTrue("screen_name $it is never logged", it in viaRoutes || it in viaViewModels) }
    }

    @Test
    fun `unknown routes map to nothing`() {
        assertNull(ScreenNames.forRoute("nope"))
        assertNull(ScreenNames.forRoute(null))
    }
}
