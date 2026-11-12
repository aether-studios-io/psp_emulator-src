package app.letostudio.gameemulator.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.theme.RnTheme
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * RetroNova native ad (design_token.json › nativeAd): surface card, radius 12, "Ad" label,
 * outlined ink CTA. Small = icon + text + CTA in a row (N2–N5); Medium adds the 16:9 media
 * view and a full-width CTA (N1). Renders nothing while [nativeAd] is null.
 * The SDK needs a real NativeAdView, so this is plain Android views inside AndroidView.
 */
@Composable
fun RnNativeAd(nativeAd: NativeAd?, medium: Boolean, modifier: Modifier = Modifier) {
    if (nativeAd == null) return
    val c = RnTheme.colors
    val colors = remember(c) {
        AdColors(c.surface.toArgb(), c.line.toArgb(), c.ink.toArgb(), c.inkMuted.toArgb(), c.surfaceSunken.toArgb())
    }
    val label = stringResource(R.string.ad_label)
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx -> build(ctx, colors, label, medium) },
        update = { it.bind(nativeAd) },
        onRelease = { it.destroy() },
    )
}

private data class AdColors(val surface: Int, val line: Int, val ink: Int, val muted: Int, val sunken: Int)

private class Holder(
    val icon: ImageView,
    val headline: TextView,
    val body: TextView,
    val cta: Button,
    val media: MediaView?,
)

private fun build(ctx: Context, col: AdColors, label: String, medium: Boolean): NativeAdView {
    val d = ctx.resources.displayMetrics.density
    fun dp(v: Float) = (v * d).toInt()
    val font = runCatching { ResourcesCompat.getFont(ctx, R.font.be_vietnam_pro_semibold) }.getOrNull()
    val bold = runCatching { ResourcesCompat.getFont(ctx, R.font.be_vietnam_pro_bold) }.getOrNull()

    val root = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
        background = GradientDrawable().apply {
            cornerRadius = dp(12f).toFloat()
            setColor(col.surface)
            setStroke(dp(1f), col.line)
        }
    }
    val head = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val icon = ImageView(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply { cornerRadius = dp(10f).toFloat(); setColor(col.sunken) }
        clipToOutline = true
    }
    val texts = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(10f); marginEnd = dp(10f)
        }
    }
    val titleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val badge = TextView(ctx).apply {
        text = label
        textSize = 10f
        setTextColor(col.muted)
        typeface = bold ?: Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setPadding(dp(5f), dp(1f), dp(5f), dp(1f))
        background = GradientDrawable().apply { cornerRadius = dp(4f).toFloat(); setStroke(dp(1f), col.muted) }
    }
    val headline = TextView(ctx).apply {
        textSize = 14f
        setTextColor(col.ink)
        typeface = bold ?: Typeface.DEFAULT_BOLD
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6f) }
    }
    titleRow.addView(badge)
    titleRow.addView(headline)
    val body = TextView(ctx).apply {
        textSize = 12f
        setTextColor(col.muted)
        typeface = font
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    texts.addView(titleRow)
    texts.addView(body)
    val cta = Button(ctx).apply {
        isAllCaps = false
        textSize = 13f
        setTextColor(col.ink)
        typeface = bold ?: Typeface.DEFAULT_BOLD
        minHeight = 0; minimumHeight = 0; minWidth = 0; minimumWidth = 0
        stateListAnimator = null
        setPadding(dp(14f), 0, dp(14f), 0)
        background = GradientDrawable().apply { cornerRadius = dp(8f).toFloat(); setStroke(dp(1.5f), col.ink) }
    }
    head.addView(icon)
    head.addView(texts)
    root.addView(head)

    var media: MediaView? = null
    if (medium) {
        media = MediaView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12f) }
            setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        }
        root.addView(media)
        cta.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40f)).apply { topMargin = dp(12f) }
        root.addView(cta)
    } else {
        cta.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34f))
        head.addView(cta)
    }

    return NativeAdView(ctx).apply {
        addView(root)
        headlineView = headline
        bodyView = body
        iconView = icon
        callToActionView = cta
        mediaView = media
        tag = Holder(icon, headline, body, cta, media)
    }
}

private fun NativeAdView.bind(ad: NativeAd) {
    val h = tag as Holder
    h.headline.text = ad.headline
    h.body.text = ad.body
    h.body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
    h.cta.text = ad.callToAction
    h.cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    val icon = ad.icon?.drawable
    h.icon.setImageDrawable(icon)
    h.icon.visibility = if (icon == null) View.GONE else View.VISIBLE
    h.media?.mediaContent = ad.mediaContent
    setNativeAd(ad)
}
