package app.letostudio.gameemulator.analytics

import app.letostudio.gameemulator.ui.navigation.Routes

/**
 * Route PATTERN → `screen_name` (analytics.md §2). Keyed on the pattern, never a resolved
 * route (argument values would leak). Routes mapped to null log their own screen_view from
 * the ViewModel: onboarding (one per page), paywall, player/pause, emulator settings, guide,
 * controls — so each fires exactly once.
 */
object ScreenNames {

    private val names: Map<String, String?> = mapOf(
        Routes.ONBOARDING to null,
        Routes.PAYWALL to null,
        Routes.LIBRARY to AppScreens.LIBRARY,
        Routes.IMPORT to AppScreens.IMPORT,
        Routes.GAME to AppScreens.GAME_DETAIL,
        Routes.HOMEBREW to AppScreens.HOMEBREW,
        Routes.HOMEBREW_GUIDE to null,
        Routes.PLAYER to null,
        Routes.SETTINGS to AppScreens.SETTINGS,
        Routes.EMU_SETTINGS to null,
        Routes.CONTROLS to null,
        Routes.OSS to AppScreens.OSS_LICENSES,
        Routes.DEBUG to AppScreens.DEBUG,
    )

    fun isMapped(routePattern: String): Boolean = names.containsKey(routePattern)

    /** The screen name for a route pattern, or null (log nothing here). */
    fun forRoute(routePattern: String?): String? = routePattern?.let { names[it] }
}
