package app.letostudio.gameemulator.ads

import android.content.Context
import android.util.Log
import app.letostudio.gameemulator.billing.EntitlementManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Loads the small batch of native ads rendered full-screen between the onboarding slides
 * (one, after slide 2 — see [NativeAdFullscreen]; none after slide 1 since 2026-11-01). Onboarding is a
 * first-launch-only screen that runs *before / in parallel with* [AdsManager.onConsentResolved],
 * so this manager cannot assume MobileAds is initialized the way [NativeAdManager] does:
 * it initializes the SDK itself (idempotently) before loading.
 *
 * Both gates are enforced here (premium via [EntitlementManager], consent via
 * [ConsentManager]) plus the build switch, so a premium or no-consent user loads nothing.
 * The gate is read directly rather than through [AdsManager] to avoid coupling (same
 * reason as [NativeAdManager]/[AppOpenAdManager]).
 *
 * Ads are handed out sequentially via [poll] and every ad this manager creates is owned
 * here — [destroy] (wired into MainActivity.onDestroy, like the other managers) releases
 * them. The UI never destroys a [NativeAd].
 *
 * App-scoped Main coroutine scope: the GMA SDK requires the main thread, and every batch
 * access ([ensureLoaded]/[poll]/[destroy]) happens on it, so the batch needs no lock.
 */
@Singleton
class NativeFullscreenAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val consentManager: ConsentManager,
    private val entitlementManager: EntitlementManager,
    private val adsRemoteConfig: AdsRemoteConfig,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val ads = mutableListOf<NativeAd>()
    private var nextIndex = 0
    private var isLoading = false
    private var isInitialized = false

    /**
     * Fills the batch once if empty. Safe to call repeatedly (no-ops while loading or once
     * a batch is loaded). Call the moment onboarding composes and [AdsManager.adsEnabled]
     * is true — the load must start before the first Next tap or the window is lost.
     */
    fun ensureLoaded() {
        val adUnitId = AdsConfig.nativeFullscreenAdUnitId
        if (adUnitId.isBlank() || isLoading || ads.isNotEmpty()) return
        isLoading = true

        scope.launch {
            if (!adsEnabled()) {
                isLoading = false
                return@launch
            }
            // Unlike NativeAdManager this can run before onConsentResolved finished init,
            // so initialize here. MobileAds.initialize is idempotent and cheap once done.
            if (!isInitialized) {
                withContext(Dispatchers.IO) {
                    AdsConfig.applyRequestConfiguration()
                    MobileAds.initialize(context)
                }
                isInitialized = true
            }

            val loader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { ad ->
                    // Late arrivals after a teardown must not resurrect the batch.
                    if (isLoading) ads.add(ad) else ad.destroy()
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Native fullscreen load failed: ${error.code} ${error.message}")
                    }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .build()

            loader.loadAds(AdRequest.Builder().build(), AdsConfig.NATIVE_FULLSCREEN_COUNT)
            scope.launch { awaitLoaderIdle(loader) }
        }
    }

    /**
     * The next unshown ad, or null when none is ready (gate off, batch not filled yet, or
     * the batch is exhausted). Each call advances; onboarding calls it once, on the slide
     * 2 -> 3 tap. The returned ad stays owned here; the caller only renders it.
     */
    fun poll(): NativeAd? = ads.getOrNull(nextIndex)?.also { nextIndex++ }

    /** Destroys every loaded ad — call from MainActivity.onDestroy (like the others). */
    fun destroy() {
        ads.forEach { it.destroy() }
        ads.clear()
        nextIndex = 0
        isLoading = false
    }

    private suspend fun awaitLoaderIdle(loader: AdLoader) {
        var waited = 0L
        while (loader.isLoading && waited < LOADER_TIMEOUT_MS) {
            kotlinx.coroutines.delay(LOADER_POLL_MS)
            waited += LOADER_POLL_MS
        }
        isLoading = false
    }

    /**
     * Mirrors [AdsManager.adsEnabled] (duplicated to avoid an AdsManager dependency),
     * plus this format's OWN remote kill-switch — read at load time, so a console flip
     * that fetches before onboarding's first Next tap still applies.
     */
    private suspend fun adsEnabled(): Boolean {
        if (!AdsConfig.ADS_ENABLED) return false
        if (!adsRemoteConfig.nativeFullscreenAdsEnabled) return false
        return combine(entitlementManager.premiumStatus, consentManager.canRequestAds) { premium, canRequest ->
            !premium.isPremium && canRequest
        }.first()
    }

    private companion object {
        const val TAG = "NativeFullscreenAd"
        const val LOADER_POLL_MS = 250L
        const val LOADER_TIMEOUT_MS = 30_000L
    }
}
