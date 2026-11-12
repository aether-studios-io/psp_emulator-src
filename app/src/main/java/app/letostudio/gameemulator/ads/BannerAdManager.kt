package app.letostudio.gameemulator.ads

import android.app.Activity
import android.content.Context
import app.letostudio.gameemulator.analytics.AnalyticsEvents
import app.letostudio.gameemulator.analytics.AnalyticsManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns ONE shared banner [AdView] that is reparented between screens (an AdView keeps
 * its loaded ad across reattachment, so screen switches don't burn impressions).
 *
 * Sizing uses the Large Anchored Adaptive Banner API: the size is fixed at load time
 * for a given width, so when the available width changes (rotation, foldable resize —
 * MainActivity handles configChanges itself) the view is destroyed and recreated with
 * the new size.
 *
 * The AdView is created with an ACTIVITY context, never the application context —
 * the collapsible expanded overlay is a popup that needs the activity window and
 * silently never expands otherwise. MainActivity calls [destroy] in onDestroy so the
 * held activity reference is released.
 *
 * Collapsible teardown rule: the expanded overlay is a WINDOW the SDK owns — detaching
 * the AdView does not take it down, and even [AdView.destroy] removes it asynchronously
 * (~1s, measured; nothing app-side can shorten it). So the overlay must never be alive
 * when the UI navigates somewhere it could cover. Two containment measures:
 *  - collapsible extras go on the FIRST request of the session only ([collapsibleRequested]),
 *    so recreated banners are normal banners and the overlay can exist at most once
 *  - [release] destroys the view only while its loaded ad is collapsible; otherwise it
 *    detaches, keeping the shared-view economy for the whole rest of the session
 */
@Singleton
class BannerAdManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val analyticsManager: AnalyticsManager,
) {

    private var adView: AdView? = null
    private var loadedWidthDp = -1

    /** True once a collapsible request has been sent this session — never send a second. */
    private var collapsibleRequested = false

    /** Height (dp) reserved for the banner at [adWidthDp] — known before any ad loads. */
    fun bannerHeightDp(adWidthDp: Int): Int = adSizeFor(adWidthDp).height

    /**
     * Starts loading the banner before any screen hosts it ("ads as early as possible"):
     * called right after consent resolves so the first Home frame already has a filled
     * ad. Uses the current configuration width — the same value BannerAdView reads.
     */
    fun preload(activity: Activity) {
        getBanner(activity, activity.resources.configuration.screenWidthDp)
    }

    /**
     * Returns the shared banner sized for [adWidthDp], creating (and loading) it on
     * first use or when the width changed. [activityContext] must be an Activity (see
     * class doc). Returns null when the unit is not configured — callers should
     * already be gated on [AdsManager.adsEnabled].
     */
    fun getBanner(activityContext: Context, adWidthDp: Int): AdView? {
        val adUnitId = AdsConfig.bannerAdUnitId
        if (adUnitId.isBlank()) return null

        val existing = adView
        if (existing != null && loadedWidthDp == adWidthDp) return existing

        existing?.destroy()
        loadedWidthDp = adWidthDp
        return AdView(activityContext).apply {
            this.adUnitId = adUnitId
            setAdSize(adSizeFor(adWidthDp))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner loaded, collapsible=${this@apply.isCollapsible}")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Banner load failed: ${error.code} ${error.message}")
                }
            }
            // ad_impression revenue (specs/006-analytics US4, research R6).
            setOnPaidEventListener { adValue ->
                analyticsManager.logAdImpression(
                    AnalyticsEvents.AdFormat.BANNER,
                    adValue.valueMicros,
                    adValue.currencyCode,
                    adValue.precisionType,
                )
            }
            loadAd(buildAdRequest())
            adView = this
        }
    }

    /**
     * Collapsible banner (AdsConfig.BANNER_COLLAPSIBLE): the first impression expands
     * into a dismissible overlay anchored to the bottom; the SDK serves normal banners
     * on auto-refresh. The composable's reserved height stays the collapsed AdSize —
     * the expanded state is an overlay, not a layout change.
     *
     * The extras ride along ONCE PER SESSION: a recreated banner (after [release]
     * destroyed a collapsible one) must load as a normal banner, or every fullscreen
     * round trip would pop the expanded overlay open again.
     */
    private fun buildAdRequest(): AdRequest {
        val builder = AdRequest.Builder()
        if (AdsConfig.BANNER_COLLAPSIBLE && !collapsibleRequested) {
            collapsibleRequested = true
            val extras = Bundle().apply {
                putString("collapsible", AdsConfig.BANNER_COLLAPSIBLE_PLACEMENT)
            }
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }
        return builder.build()
    }

    /**
     * Navigation teardown — BannerAdView's onDispose and every paywall entry point.
     *
     * While the loaded ad is collapsible its expanded overlay may be up, and destroy is
     * the only thing that takes the overlay down at all — so that one ad is sacrificed.
     * In every other case (no ad, normal banner) a plain detach keeps the loaded ad
     * alive for the next host, exactly the original shared-view design.
     */
    fun release() {
        val view = adView ?: return
        if (view.isCollapsible) {
            destroy()
        } else {
            (view.parent as? ViewGroup)?.removeView(view)
        }
    }

    /** Full teardown; MainActivity.onDestroy (releases the held activity context). */
    fun destroy() {
        (adView?.parent as? ViewGroup)?.removeView(adView)
        adView?.destroy()
        adView = null
        loadedWidthDp = -1
    }

    private fun adSizeFor(adWidthDp: Int): AdSize =
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidthDp)

    private companion object {
        const val TAG = "BannerAdManager"
    }
}
