package app.letostudio.gameemulator.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import app.letostudio.gameemulator.analytics.AnalyticsEvents
import app.letostudio.gameemulator.analytics.AnalyticsManager
import app.letostudio.gameemulator.billing.EntitlementManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Owns the Google Mobile Ads SDK lifecycle and the full-screen (interstitial) ad slot.
 * Both gates are enforced here, not at call sites: UMP consent ([ConsentManager]) and
 * premium status ([EntitlementManager]) — a premium user or a no-consent user can call
 * any method safely and nothing loads or shows.
 *
 * App-scoped Main coroutine scope on purpose: preloaded ads must survive individual
 * screens, and all GMA SDK entry points require the main thread.
 */
@Singleton
class AdsManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val consentManager: ConsentManager,
    private val entitlementManager: EntitlementManager,
    private val bannerAdManager: BannerAdManager,
    private val appOpenAdManager: AppOpenAdManager,
    private val analyticsManager: AnalyticsManager,
    private val fullScreenAdState: FullScreenAdState,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The policy gate: free user AND UMP allows ad requests. Kept free of the build
     * switch so it stays meaningful in debug unit tests — [adsEnabled] is what call
     * sites use.
     */
    internal val adsAllowedByPolicy: Flow<Boolean> = combine(
        entitlementManager.premiumStatus,
        consentManager.canRequestAds,
    ) { premium, canRequest -> !premium.isPremium && canRequest }

    /** True when ads may be shown at all: the build switch AND the policy gate. */
    val adsEnabled: Flow<Boolean> = adsAllowedByPolicy.map { AdsConfig.ADS_ENABLED && it }

    @Volatile
    private var isMobileAdsInitialized = false

    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var isInterstitialLoading = false

    private object Keys {
        val lastFullScreenAdShownAt = longPreferencesKey("last_full_screen_ad_shown_at")
    }

    /**
     * Initializes the GMA SDK (once, off the main thread) and preloads the banner +
     * interstitial. Call after [ConsentManager.gatherConsent] resolves; no-ops for
     * premium users and when consent forbids ad requests. [activity] is required by
     * the banner (collapsible overlay needs an activity window).
     */
    fun onConsentResolved(activity: Activity) {
        scope.launch {
            if (!adsEnabled.first()) return@launch
            if (!isMobileAdsInitialized) {
                withContext(Dispatchers.IO) {
                    AdsConfig.applyRequestConfiguration()
                    MobileAds.initialize(context)
                }
                isMobileAdsInitialized = true
            }
            bannerAdManager.preload(activity)
            appOpenAdManager.preload()
            preloadInterstitial()
        }
    }

    /**
     * Shows the preloaded interstitial if every gate passes (premium, consent, cooldown,
     * ad ready) and suspends until it is dismissed, so callers can sequence navigation
     * after the ad closes. Returns whether an ad was actually shown; a new interstitial
     * starts loading in the background either way.
     */
    suspend fun showInterstitialIfReady(activity: Activity): Boolean {
        if (!adsEnabled.first()) return false

        val now = System.currentTimeMillis()
        val lastShownAt = context.adsDataStore.data.first()[Keys.lastFullScreenAdShownAt] ?: 0L
        if (now - lastShownAt < AdsConfig.INTERSTITIAL_COOLDOWN_MS) return false

        val ad = interstitialAd ?: run {
            preloadInterstitial()
            return false
        }
        // Another full-screen ad (app-open) is up — never stack; keep this one for later.
        if (!fullScreenAdState.tryClaim()) return false
        interstitialAd = null

        context.adsDataStore.edit { it[Keys.lastFullScreenAdShownAt] = now }

        suspendCancellableCoroutine { continuation ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    fullScreenAdState.onFinished()
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                    fullScreenAdState.onFinished()
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            ad.show(activity)
        }
        // Restart the cooldown from DISMISS, not from show — a long video ad must not
        // eat its own quiet window (the write before show() only guards re-entry).
        context.adsDataStore.edit { it[Keys.lastFullScreenAdShownAt] = System.currentTimeMillis() }
        preloadInterstitial()
        return true
    }

    /** Loads the next interstitial with retry/backoff; safe to call repeatedly. */
    fun preloadInterstitial() {
        val adUnitId = AdsConfig.interstitialAdUnitId
        if (adUnitId.isBlank() || interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true

        scope.launch {
            if (!adsEnabled.first() || !isMobileAdsInitialized) {
                isInterstitialLoading = false
                return@launch
            }
            var attempt = 0
            while (attempt < AdsConfig.MAX_LOAD_ATTEMPTS) {
                val loaded = loadInterstitialOnce(adUnitId)
                if (loaded != null) {
                    // ad_impression revenue (specs/006-analytics US4, research R6).
                    loaded.setOnPaidEventListener { adValue ->
                        analyticsManager.logAdImpression(
                            AnalyticsEvents.AdFormat.INTERSTITIAL,
                            adValue.valueMicros,
                            adValue.currencyCode,
                            adValue.precisionType,
                        )
                    }
                    interstitialAd = loaded
                    break
                }
                attempt++
                if (attempt < AdsConfig.MAX_LOAD_ATTEMPTS) {
                    delay(AdsConfig.RETRY_INITIAL_DELAY_MS * (1L shl (attempt - 1)))
                }
            }
            isInterstitialLoading = false
        }
    }

    private suspend fun loadInterstitialOnce(adUnitId: String): InterstitialAd? =
        suspendCancellableCoroutine { continuation ->
            InterstitialAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        if (continuation.isActive) continuation.resume(ad)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Interstitial load failed: ${error.code} ${error.message}")
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
            )
        }

    private companion object {
        const val TAG = "AdsManager"
    }
}
