package app.letostudio.gameemulator

import android.os.Bundle
import android.os.Build
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.letostudio.gameemulator.ads.AdsConfig
import app.letostudio.gameemulator.ads.AdsManager
import app.letostudio.gameemulator.ads.AdsRemoteConfig
import app.letostudio.gameemulator.ads.AppOpenAdManager
import app.letostudio.gameemulator.ads.BannerAdManager
import app.letostudio.gameemulator.ads.ConsentManager
import app.letostudio.gameemulator.ads.FullScreenAdState
import app.letostudio.gameemulator.ads.NativeAdManager
import app.letostudio.gameemulator.ads.NativeFullscreenAdManager
import app.letostudio.gameemulator.analytics.AnalyticsManager
import app.letostudio.gameemulator.crash.CrashReportingManager
import app.letostudio.gameemulator.billing.BillingRepository
import app.letostudio.gameemulator.data.repository.SettingsRepository
import app.letostudio.gameemulator.domain.model.ThemeSetting
import app.letostudio.gameemulator.ui.navigation.AppNavHost
import app.letostudio.gameemulator.ui.splash.SplashScreen
import app.letostudio.gameemulator.ui.theme.AppTheme
import app.letostudio.gameemulator.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The single Activity. TEMPLATE: this file owns the cold-start contract — consent,
 * ads init, purchase reconciliation, the theme and the two start gates. Feature work
 * belongs in the NavHost, not here; adding to onCreate is how cold start regresses.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var billingRepository: BillingRepository

    @Inject
    lateinit var consentManager: ConsentManager

    @Inject
    lateinit var adsManager: AdsManager

    @Inject
    lateinit var bannerAdManager: BannerAdManager

    @Inject
    lateinit var appOpenAdManager: AppOpenAdManager

    @Inject
    lateinit var nativeAdManager: NativeAdManager

    @Inject
    lateinit var nativeFullscreenAdManager: NativeFullscreenAdManager

    @Inject
    lateinit var adsRemoteConfig: AdsRemoteConfig

    @Inject
    lateinit var analyticsManager: AnalyticsManager

    // Never called here: injecting it is what starts it. It follows analyticsManager's
    // consent outcome on its own, so it has no consent call site (crash/CrashReportingManager).
    @Inject
    lateinit var crashReportingManager: CrashReportingManager

    @Inject
    lateinit var fullScreenAdState: FullScreenAdState

    /** Last insets dispatched while no full-screen ad was up (see [installFullScreenAdInsetsFreeze]). */
    private var lastStableInsets: WindowInsetsCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (Theme.App.Starting) — dismisses on the first Compose frame,
        // which renders ui/splash/SplashScreen with the identical background and icon,
        // then the ads notice + spinner the system splash cannot host.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appOpenAdManager.attach(this)
        // Kick the RC fetch as early as possible — the first-launch ad params are only
        // remotely controllable on devices where this beats the splash (AdsRemoteConfig).
        adsRemoteConfig.fetchInBackground()

        // GDPR first, ads second: the UMP form (when required) overlays whatever is on
        // screen — including the cold-start paywall — because consent is a legal
        // prerequisite for ad requests. Premium/no-consent users no-op inside AdsManager.
        lifecycleScope.launch {
            consentManager.gatherConsent(this@MainActivity)
            adsManager.onConsentResolved(this@MainActivity)
            // Analytics adopts the SAME consent outcome (specs/006-analytics US1) —
            // never a second consent mechanism. Off the first-frame path (SC-007).
            analyticsManager.onConsentResolved()
        }

        // Restore/reconcile purchases on launch and on every foreground resume —
        // catches cross-device purchases and unacknowledged purchases (FR-014, US3).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                billingRepository.refreshPurchases()
            }
        }

        setContent {
            val themeFlow = remember {
                settingsRepository.settings.map { it.themeMode }.distinctUntilChanged()
            }
            val themeSetting by themeFlow.collectAsStateWithLifecycle(initialValue = ThemeSetting.LIGHT)

            // Cold-start gates (FR-034/FR-048): decided once per activity creation
            // from the persisted onboarding flag and the locally cached premium
            // status — resume from background never re-triggers either. The Compose
            // splash covers the resolution window, so the NavHost still renders
            // exactly once and an onboarded user never sees the slides flash by.
            val startGates by produceState<StartGates?>(initialValue = null) {
                value = StartGates(
                    onboarding = !settingsRepository.onboardingSeen.first(),
                    paywall = !billingRepository.currentPremiumStatus().isPremium,
                )
            }

            // Minimum splash hold: the gates resolve in milliseconds, and the ads
            // notice must be readable, not a one-frame flash. This is the ONLY
            // artificial delay in cold start — tune or zero it here.
            var splashHoldElapsed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(SPLASH_MIN_DURATION_MS)
                splashHoldElapsed = true
            }

            // Cold-start app-open ad OVER the splash — the AdMob-sanctioned launch
            // placement. Includes the FIRST launch: the ad shows before onboarding. On
            // first launch the consent flow is usually still resolving when the hold
            // ends, so the wait POLLS the full gate rather than sampling it once. Two
            // early exits keep the wait from taxing users it can never pay off for:
            //  - premium (locally cached) -> skip with zero wait
            //  - consent resolved and still no ad permission (denied) -> stop waiting
            // Not ready inside the window -> skipped, never stuck.
            var coldStartAdDone by remember { mutableStateOf(false) }
            LaunchedEffect(startGates, splashHoldElapsed) {
                val gates = startGates
                if (gates == null || !splashHoldElapsed || coldStartAdDone) {
                    return@LaunchedEffect
                }
                // Remote kill-switch for the first-launch showing only; ordinary
                // cold starts are not remote-configurable.
                val allowedByRemote =
                    !gates.onboarding || adsRemoteConfig.firstLaunchAppOpenEnabled
                // RetroNova: no app-open ad (spec section 7) - its unit is blank, so skip the
                // wait entirely instead of holding the splash for COLD_START_AD_WAIT_MS.
                if (AdsConfig.appOpenAdUnitId.isNotBlank() && allowedByRemote &&
                    !billingRepository.currentPremiumStatus().isPremium
                ) {
                    var waitedMs = 0L
                    while (waitedMs < COLD_START_AD_WAIT_MS) {
                        val adsOk = adsManager.adsEnabled.first()
                        if (adsOk && appOpenAdManager.isAdReady()) break
                        if (!adsOk && consentManager.isResolvedThisSession) break
                        kotlinx.coroutines.delay(100)
                        waitedMs += 100
                    }
                    appOpenAdManager.showColdStartAdIfReady()
                }
                coldStartAdDone = true
            }

            AppTheme(themeMode = themeSetting.toThemeMode()) {
                val gates = startGates
                if (gates == null || !splashHoldElapsed || !coldStartAdDone) {
                    SplashScreen(progressDurationMillis = SPLASH_MIN_DURATION_MS.toInt())
                } else {
                    AppNavHost(
                        startOnOnboarding = gates.onboarding,
                        startOnPaywall = gates.paywall,
                    )
                }
            }
        }
        installFullScreenAdInsetsFreeze()
    }

    /**
     * Stops the UI jumping under interstitial / app-open ads. AdActivity is translucent
     * and hides the system bars, so our window receives zero bar insets as the ad opens
     * and the real ones back as it closes. While [FullScreenAdState.holdsInsets], the
     * content view keeps handing Compose the last stable insets.
     *
     * - The hold outlives the dismiss until the status bar is reported visible again
     *   ([FullScreenAdState.INSETS_HOLD_AFTER_AD_MS] at the latest): GMA reports the
     *   dismiss while its activity still animates out with the bars hidden. It is decided
     *   synchronously, so no zero-insets dispatch slips through in between.
     * - On Android 11+ the bars hide/show with a WindowInsetsAnimation, and Compose follows
     *   those progress callbacks, which bypass onApplyWindowInsets. A DISPATCH_MODE_STOP
     *   callback on the content view keeps them away from Compose for the window's
     *   lifetime (installing it only while an ad was up raced with the hide animation).
     *   Cost: the IME/bars move the layout in one step instead of animating.
     *
     * Verified in crochet (built from this template) on a Pixel 6a, 2026-10-31. Children are
     * dispatched manually and CONSUMED returned: on API 30+ a ViewGroup dispatches the
     * ORIGINAL insets to children, ignoring a listener's return value.
     */
    private fun installFullScreenAdInsetsFreeze() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val frozen = fullScreenAdState.holdsInsets(insets.isVisible(WindowInsetsCompat.Type.statusBars()))
            val applied = if (frozen) {
                lastStableInsets ?: insets
            } else {
                insets.also { lastStableInsets = it }
            }
            (view as ViewGroup).children.forEach { ViewCompat.dispatchApplyWindowInsets(it, applied) }
            WindowInsetsCompat.CONSUMED
        }
        blockInsetsAnimations(content)
        lifecycleScope.launch {
            fullScreenAdState.isShowing
                .drop(1)
                .filter { showing -> !showing }
                .collectLatest {
                    ViewCompat.requestApplyInsets(content)
                    // Safety net if no insets dispatch arrives once the hold expires.
                    delay(FullScreenAdState.INSETS_HOLD_AFTER_AD_MS + 100)
                    ViewCompat.requestApplyInsets(content)
                }
        }
    }

    /**
     * Android 11+ only: the compat setWindowInsetsAnimationCallback replaces the view's
     * OnApplyWindowInsetsListener (the freeze) on older releases, which do not animate
     * the system bars anyway.
     */
    private fun blockInsetsAnimations(content: ViewGroup) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        content.setWindowInsetsAnimationCallback(
            object : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP) {
                override fun onProgress(
                    insets: WindowInsets,
                    runningAnimations: MutableList<WindowInsetsAnimation>,
                ): WindowInsets = insets
            },
        )
    }

    // RetroNova Player: hardware gamepad keys and analog sticks go to the emulator while it runs.
    @android.annotation.SuppressLint("RestrictedApi") // public Activity API; lint flags ComponentActivity's override
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        app.letostudio.gameemulator.input.PlayerInputBridge.handler?.onKey(event)?.takeIf { it } ?: super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean =
        app.letostudio.gameemulator.input.PlayerInputBridge.handler?.onMotion(ev)?.takeIf { it } ?: super.dispatchGenericMotionEvent(ev)

    override fun onDestroy() {
        // The shared AdView holds this activity's context (collapsible overlay) —
        // release it so the activity can't leak. Recreation reloads the banner.
        bannerAdManager.destroy()
        nativeAdManager.destroy()
        nativeFullscreenAdManager.destroy()
        appOpenAdManager.detach(this)
        super.onDestroy()
    }
}

/** The two cold-start decisions, resolved together so the NavHost renders once. */
private data class StartGates(val onboarding: Boolean, val paywall: Boolean)

/** Minimum Compose-splash hold so the ads notice is readable (see usage above). */
private const val SPLASH_MIN_DURATION_MS = 1_500L

/**
 * Extra time past the splash hold the cold-start app-open ad may spend finishing its
 * load. During it the (already full) progress bar sits still — keep it short. Zero
 * disables the wait entirely: the ad then only shows when preload beat the hold.
 */
private const val COLD_START_AD_WAIT_MS = 2_000L

private fun ThemeSetting.toThemeMode(): ThemeMode = when (this) {
    ThemeSetting.LIGHT -> ThemeMode.Light
    ThemeSetting.DARK -> ThemeMode.Dark
    ThemeSetting.SYSTEM -> ThemeMode.System
}
