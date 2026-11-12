package app.letostudio.gameemulator.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.letostudio.gameemulator.analytics.AnalyticsEvents
import app.letostudio.gameemulator.analytics.AnalyticsManager
import app.letostudio.gameemulator.billing.EntitlementManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * App-open ad with two entry paths:
 *
 * 1. Background return — the process ON_START observer. The first (cold-start) event
 *    is still swallowed here, because cold start is handled explicitly by path 2.
 * 2. Cold start (product decision 2026-07-19, replacing the earlier swallow-only rule):
 *    MainActivity calls [showColdStartAdIfReady] once the splash hold and start gates
 *    resolve, showing the ad OVER the splash — the placement the app-open format is
 *    designed for. This path deliberately bypasses [isUiBlocked]: the splash IS the
 *    loading screen the format covers, and the NavHost (with the paywall) is not
 *    composed until the call returns.
 *
 * The observer path never shows when: the UI is on a fullscreen route (players/
 * paywall/import — covers "playback running"), the app is in/returning from PiP, the
 * UMP form is visible, the user is premium or consent forbids ads. Both paths share
 * ONE cooldown key — deliberately NOT shared with the interstitial cooldown — and never
 * show while another full-screen ad holds [FullScreenAdState] (no stacking).
 */
@Singleton
class AppOpenAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val consentManager: ConsentManager,
    private val entitlementManager: EntitlementManager,
    private val analyticsManager: AnalyticsManager,
    private val fullScreenAdState: FullScreenAdState,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var currentActivity: Activity? = null
    private var appOpenAd: AppOpenAd? = null
    private var loadedAtMs = 0L

    @Volatile
    private var isLoading = false
    private var isFirstForeground = true
    private var isObserverRegistered = false

    /**
     * True while the UI is somewhere an app-open ad must not appear (fullscreen
     * player/paywall/import routes, PiP). Updated by AppNavHost; reset to true on
     * [attach] because a fresh activity's first route isn't known yet (it may be the
     * cold-start paywall).
     */
    @Volatile
    var isUiBlocked: Boolean = true

    private object Keys {
        val lastAppOpenShownAt = longPreferencesKey("last_app_open_shown_at")
    }

    /** Called from MainActivity.onCreate — also registers the process observer once. */
    fun attach(activity: Activity) {
        currentActivity = activity
        isUiBlocked = true
        if (!isObserverRegistered) {
            isObserverRegistered = true
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
    }

    fun detach(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    /** Process-level foreground event (also fires on cold start — swallowed once). */
    override fun onStart(owner: LifecycleOwner) {
        if (isFirstForeground) {
            isFirstForeground = false
            return
        }
        scope.launch { showIfEligible() }
    }

    /** Preloads the next ad; safe to call repeatedly (no-ops while loading/loaded). */
    fun preload() {
        val adUnitId = AdsConfig.appOpenAdUnitId
        if (adUnitId.isBlank() || isLoading || validAd() != null) return
        isLoading = true

        scope.launch {
            if (!adsEnabled()) {
                isLoading = false
                return@launch
            }
            AppOpenAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        // ad_impression revenue (specs/006-analytics US4, research R6).
                        ad.setOnPaidEventListener { adValue ->
                            analyticsManager.logAdImpression(
                                AnalyticsEvents.AdFormat.APP_OPEN,
                                adValue.valueMicros,
                                adValue.currencyCode,
                                adValue.precisionType,
                            )
                        }
                        appOpenAd = ad
                        loadedAtMs = System.currentTimeMillis()
                        isLoading = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "App-open load failed: ${error.code} ${error.message}")
                        isLoading = false
                    }
                },
            )
        }
    }

    private suspend fun showIfEligible() {
        val activity = currentActivity ?: return
        if (fullScreenAdState.isShowing.value || isUiBlocked || consentManager.isFormVisible) return
        if (!adsEnabled()) return
        if (underCooldown()) return

        val ad = validAd() ?: run {
            preload() // nothing ready this time — be ready for the next foreground
            return
        }
        showAndAwaitDismiss(activity, ad)
    }

    /** True when a loaded, unexpired ad is sitting ready to show. */
    fun isAdReady(): Boolean = validAd() != null

    /**
     * Cold-start path (see class doc): shows over the SPLASH and suspends until the ad
     * is dismissed, so MainActivity can sequence the NavHost after it. Never waits for
     * a load — not ready means an immediate false (the caller owns any bounded wait).
     * Skips [isUiBlocked] on purpose; every other gate (consent form, premium/consent,
     * shared cooldown, TTL) still applies. Returns whether an ad was shown.
     */
    suspend fun showColdStartAdIfReady(): Boolean {
        val activity = currentActivity ?: return false
        if (fullScreenAdState.isShowing.value || consentManager.isFormVisible) return false
        if (!adsEnabled()) return false
        if (underCooldown()) return false

        val ad = validAd() ?: run {
            preload()
            return false
        }
        return showAndAwaitDismiss(activity, ad)
    }

    private suspend fun underCooldown(): Boolean {
        val lastShownAt = context.adsDataStore.data.first()[Keys.lastAppOpenShownAt] ?: 0L
        return System.currentTimeMillis() - lastShownAt < AdsConfig.APP_OPEN_COOLDOWN_MS
    }

    /**
     * Common show core; suspends until dismiss/failure. Cooldown restarts from DISMISS.
     * Returns false without showing when another full-screen ad (interstitial) holds the
     * shared slot — the gates above suspend, so only this synchronous claim is race-free.
     */
    private suspend fun showAndAwaitDismiss(activity: Activity, ad: AppOpenAd): Boolean {
        if (!fullScreenAdState.tryClaim()) return false
        appOpenAd = null
        // Guard re-entry while the ad is on screen; the real cooldown write is below.
        context.adsDataStore.edit { it[Keys.lastAppOpenShownAt] = System.currentTimeMillis() }

        var dismissed = false
        suspendCancellableCoroutine { continuation ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    dismissed = true
                    fullScreenAdState.onFinished()
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "App-open failed to show: ${adError.message}")
                    fullScreenAdState.onFinished()
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            ad.show(activity)
        }
        if (dismissed) {
            // Restart from dismiss so the on-screen time never eats the quiet window.
            context.adsDataStore.edit { it[Keys.lastAppOpenShownAt] = System.currentTimeMillis() }
        }
        preload()
        return true
    }

    /** Google guidance: app-open ads go stale after 4 hours — discard, don't show. */
    private fun validAd(): AppOpenAd? {
        val ad = appOpenAd ?: return null
        if (System.currentTimeMillis() - loadedAtMs > AdsConfig.APP_OPEN_AD_TTL_MS) {
            appOpenAd = null
            return null
        }
        return ad
    }

    /**
     * Mirrors [AdsManager.adsEnabled] — duplicated rather than injected because AdsManager
     * already depends on this class, so reading it back would be a dependency cycle.
     */
    private suspend fun adsEnabled(): Boolean {
        if (!AdsConfig.ADS_ENABLED) return false
        return combine(entitlementManager.premiumStatus, consentManager.canRequestAds) { premium, canRequest ->
            !premium.isPremium && canRequest
        }.first()
    }

    private companion object {
        const val TAG = "AppOpenAdManager"
    }
}
