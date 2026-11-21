package app.letostudio.gameemulator.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import app.letostudio.gameemulator.ads.AdsConfig
import app.letostudio.gameemulator.ads.AdsManager
import app.letostudio.gameemulator.ads.FullScreenAdState
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AnalyticsEvents
import app.letostudio.gameemulator.analytics.AnalyticsManager
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.crash.CrashContext
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Native placements N1–N5 (features.md › Ad placements). */
enum class NativePlacement(val analyticsName: String, val medium: Boolean) {
    LIBRARY("native_library", true),
    IMPORT("native_import", false),
    HOMEBREW("native_homebrew", false),
    SETTINGS("native_settings", false),
    SHEET("native_sheet", false);

    val unitId: String
        get() = when (this) {
            LIBRARY -> AdsConfig.nativeLibraryUnitId
            IMPORT -> AdsConfig.nativeImportUnitId
            HOMEBREW -> AdsConfig.nativeHomebrewUnitId
            SETTINGS -> AdsConfig.nativeSettingsUnitId
            SHEET -> AdsConfig.nativeSheetUnitId
        }
}

/**
 * One native ad per placement, each with its own unit (the template pool has one unit for
 * every slot). Same gates as the template: `AdsManager.adsEnabled` = build switch AND free
 * user AND UMP consent — a Pro user never triggers a request.
 */
@Singleton
class PlacementNativeAds @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adsManager: AdsManager,
    private val analyticsManager: AnalyticsManager,
    private val crash: CrashContext,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val flows = NativePlacement.entries.associateWith { MutableStateFlow<NativeAd?>(null) }
    private val loadedAt = mutableMapOf<NativePlacement, Long>()
    private val loading = mutableSetOf<NativePlacement>()

    init {
        // Becoming Pro (or withdrawing consent) tears every loaded ad down at once.
        scope.launch {
            adsManager.adsEnabled.collect { enabled -> if (!enabled) clearAll() }
        }
    }

    fun ad(placement: NativePlacement): StateFlow<NativeAd?> = flows.getValue(placement).asStateFlow()

    /** Loads (or refreshes after the TTL) the ad for [placement]; no-op when gated. */
    fun request(placement: NativePlacement) {
        val unit = placement.unitId
        if (unit.isBlank() || placement in loading) return
        val fresh = flows.getValue(placement).value != null &&
            System.currentTimeMillis() - (loadedAt[placement] ?: 0) < AdsConfig.NATIVE_AD_TTL_MS
        if (fresh) return
        loading += placement
        scope.launch {
            if (!adsManager.adsEnabled.first()) {
                loading -= placement
                return@launch
            }
            crash.breadcrumb("ads", "native load ${placement.analyticsName}")
            val options = NativeAdOptions.Builder()
                .setMediaAspectRatio(
                    if (placement.medium) {
                        com.google.android.gms.ads.nativead.NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE
                    } else {
                        com.google.android.gms.ads.nativead.NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY
                    },
                )
                .build()
            AdLoader.Builder(context, unit)
                .forNativeAd { ad ->
                    ad.setOnPaidEventListener { v ->
                        analyticsManager.logAdImpression(AnalyticsEvents.AdFormat.NATIVE, v.valueMicros, v.currencyCode, v.precisionType)
                    }
                    flows.getValue(placement).value?.destroy()
                    flows.getValue(placement).value = ad
                    loadedAt[placement] = System.currentTimeMillis()
                    loading -= placement
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "native ${placement.name} failed: ${error.code}")
                        crash.breadcrumb("ads", "native fail ${placement.analyticsName} code=${error.code}")
                        loading -= placement
                    }
                })
                .withNativeAdOptions(options)
                .build()
                .loadAd(AdRequest.Builder().build())
        }
    }

    /**
     * The view showing [ad] was released (screen left or slot disposed). A cached ad keeps
     * its SDK webview alive and registered on the activity's view tree, so it is destroyed
     * here, and a fresh one is preloaded for the next time the placement shows.
     */
    fun release(ad: NativeAd) {
        val placement = flows.entries.firstOrNull { it.value.value === ad }?.key ?: return
        ad.destroy()
        flows.getValue(placement).value = null
        loadedAt -= placement
        request(placement)
    }

    private fun clearAll() {
        flows.values.forEach { flow -> flow.value?.destroy(); flow.value = null }
        loadedAt.clear()
    }

    private companion object { const val TAG = "PlacementNativeAds" }
}

/**
 * I1 pacing rule (CLAUDE.md §7): only after leaving a game, ≥ 60 s since the last one, no
 * daily cap, no first-session delay, never for Pro, skipped when not preloaded. Pure.
 */
object InterstitialPacer {
    const val MIN_GAP_MS = 60_000L

    enum class Skip(val reason: String) { PRO("pro"), COOLDOWN("cooldown"), NOT_LOADED("not_loaded") }

    /** null = may show; otherwise why not. [lastShownAtMs] 0 = never shown this process. */
    fun decide(isPro: Boolean, nowMs: Long, lastShownAtMs: Long, loaded: Boolean): Skip? = when {
        isPro -> Skip.PRO
        lastShownAtMs > 0 && nowMs - lastShownAtMs < MIN_GAP_MS -> Skip.COOLDOWN
        !loaded -> Skip.NOT_LOADED
        else -> null
    }
}

/** I1 + R1 orchestration with analytics, on top of the template AdsManager. */
@Singleton
class AdPlacements @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adsManager: AdsManager,
    private val proGate: ProGate,
    private val analytics: Analytics,
    private val analyticsManager: AnalyticsManager,
    private val fullScreenAdState: FullScreenAdState,
    private val crash: CrashContext,
) {
    @Volatile private var lastInterstitialAt = 0L
    private var rewarded: RewardedAd? = null
    @Volatile private var rewardedLoading = false

    /** Warm the I1 interstitial while the player runs, so exit never waits on a load. */
    fun preloadInterstitial() = adsManager.preloadInterstitial()

    /**
     * Called after the Player closed AND auto-save finished. Suspends while the ad is up.
     * Returns true when an interstitial was shown.
     */
    suspend fun onGameExited(activity: Activity): Boolean {
        val isPro = proGate.isPro()
        val enabled = adsManager.adsEnabled.first()
        val now = System.currentTimeMillis()
        val skip = InterstitialPacer.decide(isPro, now, lastInterstitialAt, loaded = enabled)
        if (skip != null) {
            logSkip(skip)
            return false
        }
        crash.breadcrumb("ads", "interstitial show exit_game")
        val shown = adsManager.showInterstitialIfReady(activity)
        if (shown) {
            lastInterstitialAt = System.currentTimeMillis()
            analytics.log(AppEvents.INTERSTITIAL_SHOW, mapOf(AppParams.PLACEMENT to "exit_game"))
        } else {
            logSkip(InterstitialPacer.Skip.NOT_LOADED)
        }
        return shown
    }

    private fun logSkip(skip: InterstitialPacer.Skip) {
        if (skip == InterstitialPacer.Skip.PRO) return // Pro users never enter the ad flow at all
        analytics.log(AppEvents.INTERSTITIAL_SKIP, mapOf(AppParams.REASON to skip.reason))
    }

    /** R1: load the rewarded ad when the "Watch an ad" row becomes visible. */
    fun preloadRewarded() {
        val unit = AdsConfig.rewardedSaveSlotUnitId
        if (unit.isBlank() || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        CoroutineScope(Dispatchers.Main).launch {
            if (!adsManager.adsEnabled.first()) {
                rewardedLoading = false
                return@launch
            }
            RewardedAd.load(context, unit, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.setOnPaidEventListener { v ->
                        analyticsManager.logAdImpression(AnalyticsEvents.AdFormat.REWARDED, v.valueMicros, v.currencyCode, v.precisionType)
                    }
                    rewarded = ad
                    rewardedLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    crash.breadcrumb("ads", "rewarded fail code=${error.code}")
                    rewardedLoading = false
                }
            })
        }
    }

    fun isRewardedReady(): Boolean = rewarded != null

    /**
     * Shows R1 after the user tapped the explicit "Watch an ad…" row. Returns true when the
     * reward was earned. Logs rewarded_start / rewarded_earned.
     */
    suspend fun showRewarded(activity: Activity, placement: String = "save_slot"): Boolean {
        analytics.log(AppEvents.REWARDED_START, mapOf(AppParams.PLACEMENT to placement))
        if (!adsManager.adsEnabled.first()) return false
        val ad = rewarded ?: run { preloadRewarded(); return false }
        if (!fullScreenAdState.tryClaim()) return false
        rewarded = null
        var earned = false
        suspendCancellableCoroutine { cont ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    fullScreenAdState.onFinished()
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    fullScreenAdState.onFinished()
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            ad.show(activity) { earned = true }
        }
        if (earned) analytics.log(AppEvents.REWARDED_EARNED, mapOf(AppParams.PLACEMENT to placement))
        preloadRewarded()
        return earned
    }
}
