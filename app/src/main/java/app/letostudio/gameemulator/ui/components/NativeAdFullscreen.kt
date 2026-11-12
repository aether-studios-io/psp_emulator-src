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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.setPadding
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.theme.AppTheme
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Renders one [NativeAd] full-screen, used between the onboarding slides (loaded by
 * [app.letostudio.gameemulator.ads.NativeFullscreenAdManager]). Unlike the compact in-list
 * [NativeAdCard] this is the *fuller* asset set a full-screen placement expects — a large
 * [MediaView] up top and an icon + headline + advertiser + body + call-to-action panel
 * below — plus the required "Ad" attribution (AdChoices is added by the SDK).
 *
 * The SDK requires a real [NativeAdView] with registered asset views, so — like
 * [NativeAdCard] and [BannerAdView] — this is one of the few places raw Android views are
 * used in the UI layer. [onClose] is a Compose control layered *over* the ad view (not a
 * registered asset), so tapping it never counts as an ad click.
 *
 * The [nativeAd] is owned by the manager and never destroyed here.
 */
@Composable
fun NativeAdFullscreen(
    nativeAd: NativeAd,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val ext = AppTheme.extendedColors
    val colors = remember(cs, ext) {
        FullscreenAdColors(
            background = cs.background.toArgb(),
            surface = cs.surfaceVariant.toArgb(),
            headline = cs.onSurface.toArgb(),
            body = cs.onSurfaceVariant.toArgb(),
            ctaGradientStart = ext.gradientStart.toArgb(),
            ctaGradientEnd = ext.gradientEnd.toArgb(),
            ctaContent = Color.White.toArgb(),
            badgeContainer = cs.tertiary.toArgb(),
            badgeContent = cs.onTertiary.toArgb(),
        )
    }
    val adLabel = stringResource(R.string.native_ad_badge)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AndroidView(
            // Inset within the system bars so the media and CTA never sit under the
            // status/nav bars; the background fills the bar strips.
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            factory = { context -> buildFullscreenAdView(context, colors, adLabel) },
            update = { adView -> adView.bindFullscreen(nativeAd) },
            onRelease = { adView -> adView.destroyFullscreenView() },
        )

        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.native_ad_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class FullscreenAdColors(
    val background: Int,
    val surface: Int,
    val headline: Int,
    val body: Int,
    val ctaGradientStart: Int,
    val ctaGradientEnd: Int,
    val ctaContent: Int,
    val badgeContainer: Int,
    val badgeContent: Int,
)

/** Views registered as assets, kept for [bindFullscreen] to fill and toggle. */
private class FullscreenAdViewHolder(
    val media: MediaView,
    val icon: ImageView,
    val badge: TextView,
    val headline: TextView,
    val advertiser: TextView,
    val body: TextView,
    val cta: Button,
)

private fun buildFullscreenAdView(
    context: Context,
    colors: FullscreenAdColors,
    adLabel: String,
): NativeAdView {
    val d = context.resources.displayMetrics.density
    fun dp(v: Int) = (v * d).toInt()

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(colors.background)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    // Large media fills the upper area; the "Ad" badge sits over its top-start corner.
    val mediaFrame = android.widget.FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }

    val media = MediaView(context).apply {
        layoutParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    val badge = TextView(context).apply {
        text = adLabel
        setTextColor(colors.badgeContent)
        textSize = 11f
        includeFontPadding = false
        setPadding(dp(8), dp(2), dp(8), dp(2))
        background = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(colors.badgeContainer)
        }
        layoutParams = android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = dp(12)
            marginStart = dp(12)
        }
    }

    mediaFrame.addView(media)
    mediaFrame.addView(badge)

    // Info panel: icon + (headline / advertiser), body, CTA.
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24))
        background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(
                dp(24).toFloat(), dp(24).toFloat(),
                dp(24).toFloat(), dp(24).toFloat(),
                0f, 0f, 0f, 0f,
            )
            setColor(colors.surface)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
            }
        }
    }

    val titleCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(12)
        }
    }

    val headline = TextView(context).apply {
        setTextColor(colors.headline)
        textSize = 20f
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val advertiser = TextView(context).apply {
        setTextColor(colors.body)
        textSize = 13f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(2) }
    }

    titleCol.addView(headline)
    titleCol.addView(advertiser)
    headerRow.addView(icon)
    headerRow.addView(titleCol)

    val body = TextView(context).apply {
        setTextColor(colors.body)
        textSize = 15f
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(16) }
    }

    val cta = Button(context).apply {
        setTextColor(colors.ctaContent)
        textSize = 16f
        isAllCaps = false
        stateListAnimator = null
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(colors.ctaGradientStart, colors.ctaGradientEnd),
        ).apply {
            cornerRadius = dp(28).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(20) }
    }

    panel.addView(headerRow)
    panel.addView(body)
    panel.addView(cta)

    root.addView(mediaFrame)
    root.addView(panel)

    return NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        addView(root)
        mediaView = media
        iconView = icon
        headlineView = headline
        advertiserView = advertiser
        bodyView = body
        callToActionView = cta
        tag = FullscreenAdViewHolder(media, icon, badge, headline, advertiser, body, cta)
    }
}

private fun NativeAdView.bindFullscreen(nativeAd: NativeAd) {
    val holder = tag as? FullscreenAdViewHolder ?: return

    holder.headline.text = nativeAd.headline // guaranteed non-null asset
    holder.media.mediaContent = nativeAd.mediaContent

    val iconDrawable = nativeAd.icon?.drawable
    if (iconDrawable != null) {
        holder.icon.setImageDrawable(iconDrawable)
        holder.icon.visibility = View.VISIBLE
    } else {
        holder.icon.visibility = View.GONE
    }

    val advertiserText = nativeAd.advertiser
    if (advertiserText.isNullOrBlank()) {
        holder.advertiser.visibility = View.GONE
    } else {
        holder.advertiser.text = advertiserText
        holder.advertiser.visibility = View.VISIBLE
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

/** Releases the container view; the NativeAd is owned by the manager and left intact. */
private fun NativeAdView.destroyFullscreenView() {
    tag = null
    removeAllViews()
}
