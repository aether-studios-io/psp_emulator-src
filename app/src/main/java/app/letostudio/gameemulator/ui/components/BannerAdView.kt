package app.letostudio.gameemulator.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.letostudio.gameemulator.ads.BannerAdManager

/**
 * Hosts the shared Large Anchored Adaptive banner (ads/BannerAdManager) in Compose.
 * Callers gate visibility with [app.letostudio.gameemulator.ads.AdsManager.adsEnabled] —
 * this composable only handles hosting, sizing and reparenting.
 *
 * The height is reserved up-front (adaptive size is deterministic per width/device),
 * so the layout never shifts when the ad fills.
 */
@Composable
fun BannerAdView(
    bannerAdManager: BannerAdManager,
    modifier: Modifier = Modifier,
) {
    val adWidthDp = LocalConfiguration.current.screenWidthDp
    val adHeightDp = remember(adWidthDp) { bannerAdManager.bannerHeightDp(adWidthDp) }
    // The ACTIVITY context — the collapsible overlay never expands with an app context.
    val activityContext = LocalContext.current

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(adHeightDp.dp),
        factory = { context -> FrameLayout(context) },
        update = { host ->
            val banner = bannerAdManager.getBanner(activityContext, adWidthDp)
            if (banner != null && banner.parent !== host) {
                (banner.parent as? ViewGroup)?.removeView(banner)
                host.removeAllViews()
                host.addView(banner)
            }
        },
    )

    // release(), not a plain detach: while the loaded ad is collapsible its expanded
    // overlay could otherwise stay on top of the next screen (paywall, player) — the
    // manager destroys in exactly that case and detaches in every other, so the shared
    // AdView still survives ordinary fullscreen round trips.
    DisposableEffect(Unit) {
        onDispose { bannerAdManager.release() }
    }
}
