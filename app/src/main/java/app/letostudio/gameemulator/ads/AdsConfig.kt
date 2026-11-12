package app.letostudio.gameemulator.ads

import app.letostudio.gameemulator.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

/**
 * Single configuration point for the ads integration (mirrors [app.letostudio.gameemulator.billing.BillingConfig]):
 * every ad unit ID and tuning constant used anywhere in the ads stack is declared here.
 *
 * The AdMob App ID lives in AndroidManifest.xml (`com.google.android.gms.ads.APPLICATION_ID`).
 * Debug builds always use Google's sample ad units; release builds use the production
 * units below — a blank production ID disables that format entirely (no load attempts),
 * so formats can be enabled one by one as units are created in the AdMob console.
 */
object AdsConfig {

    /**
     * Build-level master switch, ANDed into every gate. On by default in both build
     * types; set the debug `buildConfigField` in app/build.gradle.kts to false to
     * silence banners and interstitials while working on something else.
     *
     * This is deliberately separate from the premium/consent policy gate
     * ([AdsManager.adsAllowedByPolicy]) — that rule stays testable whatever this is set to.
     */
    val ADS_ENABLED: Boolean get() = BuildConfig.ADS_ENABLED

    // RetroNova (features.md > AdMob IDs): unit IDs come from admob.properties through
    // BuildConfig - Google's test IDs in debug, the real IDs in release (test IDs while
    // the release IDs are still placeholders; see BuildConfig.ADMOB_IDS_ARE_TEST).
    // Banner, app-open and full-screen native are not used by RetroNova (spec section 7:
    // nothing tied to launch / app open) - blank disables them in every build.

    /** N1 Library mid-grid, Native Medium. */
    val nativeLibraryUnitId: String get() = BuildConfig.AD_NATIVE_LIBRARY

    /** N2 under Done on the Import result. */
    val nativeImportUnitId: String get() = BuildConfig.AD_NATIVE_IMPORT

    /** N3 end of the Homebrew hub. */
    val nativeHomebrewUnitId: String get() = BuildConfig.AD_NATIVE_HOMEBREW

    /** N4 bottom of Settings (Free). */
    val nativeSettingsUnitId: String get() = BuildConfig.AD_NATIVE_SETTINGS

    /** N5 Fix-lag explainer sheet. */
    val nativeSheetUnitId: String get() = BuildConfig.AD_NATIVE_SHEET

    /** R1 rewarded extra save slot. */
    val rewardedSaveSlotUnitId: String get() = BuildConfig.AD_REWARDED_SAVE_SLOT

    /**
     * Must run before every [MobileAds.initialize] call (idempotent, cheap). Caps ad
     * creatives at the "T" content rating to match the app's Play content rating, and
     * registers the GMA test device from local.properties — without it, taps on a
     * locally-built release artifact hit production units as live clicks (the classic
     * invalid-traffic account-suspension path). The UMP hash is a different value with
     * a different scope; it does not substitute for this one.
     */
    fun applyRequestConfiguration() {
        val builder = RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
        if (BuildConfig.GMA_TEST_DEVICE_ID.isNotEmpty()) {
            builder.setTestDeviceIds(listOf(BuildConfig.GMA_TEST_DEVICE_ID))
        }
        MobileAds.setRequestConfiguration(builder.build())
    }

    val bannerAdUnitId: String get() = ""

    /** I1 interstitial after leaving a game. */
    val interstitialAdUnitId: String get() = BuildConfig.AD_INTER_EXIT_GAME

    val appOpenAdUnitId: String get() = ""

    /** Template in-list pool (unused by RetroNova screens; N1 uses its own unit). */
    val nativeAdUnitId: String get() = BuildConfig.AD_NATIVE_LIBRARY

    val nativeFullscreenAdUnitId: String get() = ""

    /**
     * Collapsible banner: DISABLED (product decision, 2026-07-19). The expanded state is
     * an SDK-owned popup window whose removal is asynchronous (~1s, measured) — it kept
     * lingering over the paywall after tapping a paywall entry point, and the SDK offers
     * no way to dismiss it programmatically (no fix as of GMA 25.4.0, the latest).
     *
     * The banner itself stays Large Anchored Adaptive — this flag only controls the
     * expand-overlay request extra. All handling (once-per-session request, the
     * isCollapsible branch in BannerAdManager.release) is kept, so re-enabling is
     * this one flag. "bottom" matches the banner's anchor above the bottom nav.
     */
    const val BANNER_COLLAPSIBLE = false
    const val BANNER_COLLAPSIBLE_PLACEMENT = "bottom"

    /** Minimum gap between two interstitials, persisted across restarts. */
    const val INTERSTITIAL_COOLDOWN_MS = 60_000L // RetroNova I1: >= 60 s apart

    /**
     * App-open frequency guard — deliberately SEPARATE from the interstitial
     * cooldown (product decision): an interstitial shown seconds ago must not
     * suppress the app-open on the next foreground, and vice versa.
     */
    const val APP_OPEN_COOLDOWN_MS = 45_000L

    /** Google guidance: app-open ads expire 4 hours after load — never show stale ones. */
    const val APP_OPEN_AD_TTL_MS = 4 * 60 * 60 * 1_000L

    /** Load retry policy: 3 attempts with exponential backoff 2s → 4s → 8s. */
    const val MAX_LOAD_ATTEMPTS = 3
    const val RETRY_INITIAL_DELAY_MS = 2_000L

    /**
     * Native ads inside a content grid: one full-width card after every
     * [NATIVE_ROW_INTERVAL] rows (converted to items via the live column count — 2 rows
     * = 6 items at 3 columns, 4 items at 2). A single-column list uses a plain item
     * interval ([NATIVE_SEARCH_INTERVAL]) instead.
     */
    const val NATIVE_ROW_INTERVAL = 2
    const val NATIVE_SEARCH_INTERVAL = 8

    /**
     * Size of the reused native ad pool. Slot N renders `pool[N % size]`, so co-visible
     * slots must map to distinct ads — 5 comfortably exceeds the 2–3 cards a tall screen
     * can show at the every-2-rows spacing, and one NativeAd is never bound to two
     * attached views at once.
     */
    const val NATIVE_POOL_SIZE = 5

    /** Native ads are refreshed once the pool is older than this (Google discourages
     * holding native ads much beyond an hour — there is no hard SDK TTL like app-open). */
    const val NATIVE_AD_TTL_MS = 60 * 60 * 1_000L

    /**
     * Full-screen native ad shown between onboarding slides 2 and 3 (none between 1 and 2
     * since 2026-11-01), so a one-ad batch is requested up front. First-launch only, so no
     * TTL/refresh — the batch is loaded once and destroyed when onboarding leaves.
     */
    const val NATIVE_FULLSCREEN_COUNT = 1
}
