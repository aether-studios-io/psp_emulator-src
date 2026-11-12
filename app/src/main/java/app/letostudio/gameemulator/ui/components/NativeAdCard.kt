package app.letostudio.gameemulator.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.setPadding
import androidx.compose.material3.MaterialTheme
import app.letostudio.gameemulator.R
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Renders one pooled [NativeAd] as an in-list card (Xtream catalog grid + search list).
 * The AdMob SDK requires a real [NativeAdView] with registered asset views, so this is
 * the one place raw Android views are used in the UI layer (like [BannerAdView]) — it
 * keeps the screens themselves on `ui/components` + layout primitives for the Konsist rule.
 *
 * Renders nothing when [nativeAd] is null (gate off / pool not filled). Every optional
 * asset (icon, body, call-to-action) is hidden when absent — only the headline and the
 * "Ad" label are guaranteed per impression. A compact icon+text+CTA layout is used
 * (no MediaView), which is a policy-valid native rendering.
 */
@Composable
fun NativeAdCard(
    nativeAd: NativeAd?,
    modifier: Modifier = Modifier,
    /**
     * Horizontal inset for the card. Defaults to 16dp so it aligns with list rows
     * (search). In a grid whose own `contentPadding` already insets the row, pass 0 so
     * the card's edges match the poster columns instead of insetting a second time.
     */
    horizontalPadding: Dp = 16.dp,
) {
    if (nativeAd == null) return

    val cs = MaterialTheme.colorScheme
    val colors = remember(cs) {
        NativeAdColors(
            container = cs.surfaceVariant.toArgb(),
            headline = cs.onSurface.toArgb(),
            body = cs.onSurfaceVariant.toArgb(),
            ctaContainer = cs.primary.toArgb(),
            ctaContent = cs.onPrimary.toArgb(),
            badgeContainer = cs.tertiary.toArgb(),
            badgeContent = cs.onTertiary.toArgb(),
        )
    }
    val adLabel = stringResource(R.string.native_ad_badge)

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp),
        factory = { context -> buildNativeAdView(context, colors, adLabel) },
        update = { adView -> adView.bind(nativeAd) },
        // The NativeAdView container is per-slot; destroy it when the slot scrolls off.
        // The pooled NativeAd itself is owned by NativeAdManager and NEVER destroyed here.
        onRelease = { adView -> adView.destroyView() },
    )
}

private data class NativeAdColors(
    val container: Int,
    val headline: Int,
    val body: Int,
    val ctaContainer: Int,
    val ctaContent: Int,
    val badgeContainer: Int,
    val badgeContent: Int,
)

/** Views registered as assets, kept for [NativeAdView.bind] to fill and toggle. */
private class AdViewHolder(
    val icon: ImageView,
    val badge: TextView,
    val headline: TextView,
    val body: TextView,
    val cta: Button,
)

private fun buildNativeAdView(
    context: Context,
    colors: NativeAdColors,
    adLabel: String,
): NativeAdView {
    val d = context.resources.displayMetrics.density
    fun dp(v: Int) = (v * d).toInt()

    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(colors.container)
        }
    }

    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(8).toFloat())
            }
        }
    }

    val textCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
            marginEnd = dp(12)
        }
    }

    val badge = TextView(context).apply {
        text = adLabel
        setTextColor(colors.badgeContent)
        textSize = 10f
        includeFontPadding = false
        setPadding(dp(6), dp(1), dp(6), dp(1))
        background = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(colors.badgeContainer)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val headline = TextView(context).apply {
        setTextColor(colors.headline)
        textSize = 15f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(4) }
    }

    val body = TextView(context).apply {
        setTextColor(colors.body)
        textSize = 13f
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) }
    }

    textCol.addView(badge)
    textCol.addView(headline)
    textCol.addView(body)

    val cta = Button(context).apply {
        setTextColor(colors.ctaContent)
        textSize = 13f
        isAllCaps = false
        minWidth = 0
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        setPadding(dp(16), dp(8), dp(16), dp(8))
        stateListAnimator = null
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(colors.ctaContainer)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    row.addView(icon)
    row.addView(textCol)
    row.addView(cta)

    return NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(row)
        // Register asset views so the SDK tracks clicks/impressions.
        iconView = icon
        headlineView = headline
        bodyView = body
        callToActionView = cta
        tag = AdViewHolder(icon, badge, headline, body, cta)
    }
}

private fun NativeAdView.bind(nativeAd: NativeAd) {
    val holder = tag as? AdViewHolder ?: return

    holder.headline.text = nativeAd.headline // guaranteed non-null asset

    val iconDrawable = nativeAd.icon?.drawable
    if (iconDrawable != null) {
        holder.icon.setImageDrawable(iconDrawable)
        holder.icon.visibility = View.VISIBLE
    } else {
        holder.icon.visibility = View.GONE
    }

    val bodyText = nativeAd.body
    if (bodyText.isNullOrBlank()) {
        holder.body.visibility = View.GONE
    } else {
        holder.body.text = bodyText
        holder.body.visibility = View.VISIBLE
    }

    val ctaText = nativeAd.callToAction
    if (ctaText.isNullOrBlank()) {
        holder.cta.visibility = View.GONE
    } else {
        holder.cta.text = ctaText
        holder.cta.visibility = View.VISIBLE
    }

    setNativeAd(nativeAd)
}

/** Releases the container view; the pooled NativeAd is owned elsewhere and left intact. */
private fun NativeAdView.destroyView() {
    tag = null
    removeAllViews()
}
