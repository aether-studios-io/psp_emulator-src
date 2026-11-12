package app.letostudio.gameemulator.ads

import android.content.Context
import android.util.Log
import app.letostudio.gameemulator.billing.EntitlementManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
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

/**
 * Owns a small, reused pool of native ads for in-list placements (content grids and
 * lists). Unlike the banner/interstitial/app-open managers this is NOT preloaded at
 * consent resolution: native cards live only on screens a user may never reach, and a
 * native ad held from launch goes silently stale. Instead the hosting Route holds the
 * pool with [acquire]/[release] while it is visible.
 *
 * The pool lives exactly while at least one host screen is on screen: each pooled
 * [NativeAd] pins its internal ad WebView even after that view is detached (LeakCanary
 * finding, 2026-07-26 — 5× zzcmi retained via MainActivity.nativeAdManager), so the
 * last host leaving destroys the pool instead of keeping it for the session. Hops
 * between two hosting screens survive because both compositions overlap during the nav
 * transition, so the refcount never touches zero between them.
 *
 * Both gates are enforced here (premium via [EntitlementManager], consent via
 * [ConsentManager]) plus the build switch, so a premium or no-consent user never loads
 * anything. The gate is read directly rather than through [AdsManager] to avoid coupling
 * (same reason as [AppOpenAdManager]).
 *
 * App-scoped Main coroutine scope: the GMA SDK requires the main thread, and every pool
 * access ([ensureLoaded]/[adForSlot]/[destroy]) happens on it, so the pool needs no lock.
 */
@Singleton
class NativeAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val consentManager: ConsentManager,
    private val entitlementManager: EntitlementManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val pool = mutableListOf<NativeAd>()
    private var loadedAtMs = 0L
    private var isLoading = false
    private var hostCount = 0

    // Bumped by every clearPool(); a load only touches the pool / isLoading while its
    // captured generation is still current, so ads arriving after a teardown are
    // destroyed instead of resurrecting the pool, and a stale load can't clobber the
    // flags of a newer one.
    private var generation = 0

    /**
     * A hosting screen became visible: refcount up and fill the pool if needed. Pair
     * with [release] from the same host's DisposableEffect.
     */
    fun acquire() {
        hostCount++
        ensureLoaded()
    }

    /** Host left; the last one out destroys the pool (frees the pinned ad WebViews). */
    fun release() {
        hostCount = (hostCount - 1).coerceAtLeast(0)
        if (hostCount == 0) {
            clearPool()
            isLoading = false
        }
    }

    /**
     * Fills the pool if it is empty or has gone stale. Safe to call repeatedly (no-ops
     * while loading or while a fresh pool is already loaded). Assumes MobileAds is
     * already initialized — true for any eligible user, since [AdsManager.onConsentResolved]
     * runs at launch and native only loads once a deep screen is reached afterwards.
     */
    fun ensureLoaded() {
        val adUnitId = AdsConfig.nativeAdUnitId
        if (adUnitId.isBlank() || isLoading) return
        if (isStale()) clearPool()
        if (pool.isNotEmpty()) return
        isLoading = true
        val gen = generation

        scope.launch {
            if (!adsEnabled()) {
                if (gen == generation) isLoading = false
                return@launch
            }

            val loader = AdLoader.Builder(context, adUnitId)
                .forNativeAd { ad ->
                    if (gen == generation) {
                        pool.add(ad)
                        loadedAtMs = System.currentTimeMillis()
                    } else {
                        ad.destroy()
                    }
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Native load failed: ${error.code} ${error.message}")
                    }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .build()

            // One request for N distinct ads; loadAds fills asynchronously.
            loader.loadAds(AdRequest.Builder().build(), AdsConfig.NATIVE_POOL_SIZE)
            awaitLoaderIdle(loader)
            if (gen == generation) isLoading = false
        }
    }

    /**
     * The ad to render at list slot [index], stable across recomposition. Returns null
     * when the pool is empty (gate off, not yet filled, or unit not configured) — the
     * card then renders nothing. Reuse is safe because slots sit ≥2 poster rows apart
     * and consecutive indices map to distinct pool entries, so one [NativeAd] is never
     * bound to two on-screen views at once.
     */
    fun adForSlot(index: Int): NativeAd? {
        if (pool.isEmpty()) return null
        return pool[index % pool.size]
    }

    /** Destroys every pooled ad — call from MainActivity.onDestroy (like the banner). */
    fun destroy() {
        clearPool()
        isLoading = false
        hostCount = 0
    }

    private fun clearPool() {
        generation++
        pool.forEach { it.destroy() }
        pool.clear()
        loadedAtMs = 0L
    }

    private fun isStale(): Boolean =
        System.currentTimeMillis() - loadedAtMs > AdsConfig.NATIVE_AD_TTL_MS

    private suspend fun awaitLoaderIdle(loader: AdLoader) {
        // loadAds fills asynchronously; the loader reports isLoading until the last ad
        // (or failure) arrives. Poll briefly so completion is mirrored to the caller.
        var waited = 0L
        while (loader.isLoading && waited < LOADER_TIMEOUT_MS) {
            kotlinx.coroutines.delay(LOADER_POLL_MS)
            waited += LOADER_POLL_MS
        }
    }

    /** Mirrors [AdsManager.adsEnabled]; duplicated to avoid an AdsManager dependency. */
    private suspend fun adsEnabled(): Boolean {
        if (!AdsConfig.ADS_ENABLED) return false
        return combine(entitlementManager.premiumStatus, consentManager.canRequestAds) { premium, canRequest ->
            !premium.isPremium && canRequest
        }.first()
    }

    private companion object {
        const val TAG = "NativeAdManager"
        const val LOADER_POLL_MS = 250L
        const val LOADER_TIMEOUT_MS = 30_000L
    }
}
