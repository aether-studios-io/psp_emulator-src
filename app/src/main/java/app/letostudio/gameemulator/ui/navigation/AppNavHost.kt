package app.letostudio.gameemulator.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.analytics.ScreenNames
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.debug.DebugRoute
import app.letostudio.gameemulator.di.RetroEntryPoint
import app.letostudio.gameemulator.ui.components.RnBottomNav
import app.letostudio.gameemulator.ui.components.RnNavItem
import app.letostudio.gameemulator.ui.controls.ControlsRoute
import app.letostudio.gameemulator.ui.emusettings.EmuSettingsRoute
import app.letostudio.gameemulator.ui.game.GameDetailRoute
import app.letostudio.gameemulator.ui.homebrew.HomebrewGuideRoute
import app.letostudio.gameemulator.ui.homebrew.HomebrewRoute
import app.letostudio.gameemulator.ui.importer.ImportRoute
import app.letostudio.gameemulator.ui.library.LibraryRoute
import app.letostudio.gameemulator.ui.onboarding.RnOnboardingRoute
import app.letostudio.gameemulator.ui.oss.OssLicensesRoute
import app.letostudio.gameemulator.ui.paywall.RnPaywallRoute
import app.letostudio.gameemulator.ui.player.PlayerRoute
import app.letostudio.gameemulator.ui.settings.RnSettingsRoute
import app.letostudio.gameemulator.ui.theme.RnTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * RetroNova shell: single NavHost, bottom nav on the three tabs, manual screen_view
 * (analytics.md §2) + Crashlytics `screen` key and breadcrumb on every destination change.
 * Cold start (D-007): first launch → onboarding → paywall once (free users) → Library;
 * returning users → Library.
 */
@Composable
fun AppNavHost(startOnOnboarding: Boolean = false, @Suppress("UNUSED_PARAMETER") startOnPaywall: Boolean = false) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val context = LocalContext.current
    val entry = remember { EntryPointAccessors.fromApplication(context.applicationContext, RetroEntryPoint::class.java) }
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow
            .map { it.destination.route }
            .distinctUntilChanged()
            .collect { r ->
                val name = ScreenNames.forRoute(r)
                if (name != null) entry.analytics().screen(name)
                val crashName = r?.substringBefore('?')?.substringBefore('/') ?: "unknown"
                entry.crashContext().set(CrashContext.Keys.SCREEN, crashName)
                entry.crashContext().breadcrumb("nav", crashName)
            }
    }

    val tabs = listOf(
        RnNavItem(stringResource(R.string.nav_library), R.drawable.ms_grid_view, R.drawable.ms_grid_view_fill),
        RnNavItem(stringResource(R.string.nav_homebrew), R.drawable.ms_explore, R.drawable.ms_explore_fill),
        RnNavItem(stringResource(R.string.nav_settings), R.drawable.ms_settings, R.drawable.ms_settings_fill),
    )
    val tabRoutes = listOf(Routes.LIBRARY, Routes.HOMEBREW, Routes.SETTINGS)
    val showBottomBar = route in Routes.tabRoutes

    Scaffold(
        containerColor = RnTheme.colors.ground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                RnBottomNav(tabs, tabRoutes.indexOf(route).coerceAtLeast(0)) { i ->
                    navController.navigateToTab(tabRoutes[i])
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = if (startOnOnboarding) Routes.ONBOARDING else Routes.LIBRARY,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable(Routes.ONBOARDING) {
                RnOnboardingRoute(onFinished = {
                    scope.launch {
                        val pro = entry.proGate().state.first().isPro
                        navController.navigate(if (pro) Routes.LIBRARY else Routes.paywall("onboarding")) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                })
            }
            composable(
                Routes.PAYWALL,
                arguments = listOf(navArgument("source") { type = NavType.StringType; defaultValue = "settings" }),
            ) {
                RnPaywallRoute(
                    onClose = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        } else {
                            navController.navigate(Routes.LIBRARY) { popUpTo(Routes.PAYWALL) { inclusive = true } }
                        }
                    },
                    snackbar = snackbar,
                )
            }
            composable(Routes.LIBRARY) {
                LibraryRoute(
                    onOpenGame = { navController.navigate(Routes.game(it)) },
                    onPlay = { navController.navigate(Routes.player(it, 0, "continue")) },
                    onAddGames = { navController.navigate(Routes.IMPORT) },
                    onOpenHomebrew = { navController.navigateToTab(Routes.HOMEBREW) },
                )
            }
            composable(Routes.IMPORT) {
                ImportRoute(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() }, snackbar = snackbar)
            }
            composable(Routes.GAME, arguments = listOf(navArgument("gameId") { type = NavType.LongType })) {
                GameDetailRoute(
                    onBack = { navController.popBackStack() },
                    onPlay = { id, slot -> navController.navigate(Routes.player(id, slot, if (slot == null) "game_detail" else "slot")) },
                    onGraphics = { navController.navigate(Routes.emuSettings(it)) },
                    onControls = { navController.navigate(Routes.controls(it)) },
                    onPaywall = { navController.navigate(Routes.paywall("save_slot")) },
                    snackbar = snackbar,
                )
            }
            composable(Routes.HOMEBREW) {
                HomebrewRoute(
                    onGuide = { navController.navigate(Routes.HOMEBREW_GUIDE) },
                    onGameAdded = { navController.navigate(Routes.game(it)) },
                    snackbar = snackbar,
                )
            }
            composable(Routes.HOMEBREW_GUIDE) { HomebrewGuideRoute(onBack = { navController.popBackStack() }) }
            composable(
                Routes.PLAYER,
                arguments = listOf(
                    navArgument("gameId") { type = NavType.LongType },
                    navArgument("slot") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("source") { type = NavType.StringType; defaultValue = "game_detail" },
                ),
            ) {
                PlayerRoute(onExit = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                RnSettingsRoute(
                    onUpgrade = { navController.navigate(Routes.paywall("settings")) },
                    onGamesFolder = { navController.navigate(Routes.IMPORT) },
                    onEmulator = { navController.navigate(Routes.emuSettings(null)) },
                    onControls = { navController.navigate(Routes.controls(null)) },
                    onOss = { navController.navigate(Routes.OSS) },
                    onDebug = { navController.navigate(Routes.DEBUG) },
                    debugAllowed = entry.remoteFlags().debugMenuAllowed,
                    snackbar = snackbar,
                )
            }
            composable(Routes.EMU_SETTINGS, arguments = listOf(navArgument("gameId") { type = NavType.LongType; defaultValue = -1L })) {
                EmuSettingsRoute(
                    onBack = { navController.popBackStack() },
                    onPaywall = { navController.navigate(Routes.paywall("emu_settings")) },
                    snackbar = snackbar,
                )
            }
            composable(Routes.CONTROLS, arguments = listOf(navArgument("gameId") { type = NavType.LongType; defaultValue = -1L })) {
                ControlsRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.OSS) { OssLicensesRoute(onBack = { navController.popBackStack() }) }
            composable(Routes.DEBUG) { DebugRoute(onBack = { navController.popBackStack() }) }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.LIBRARY) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Suppress("unused")
@Composable
private fun Fill() = Box(Modifier.fillMaxSize())
