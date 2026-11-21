package app.letostudio.gameemulator.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.theme.BeVietnamPro
import app.letostudio.gameemulator.ui.theme.RnTheme
import app.letostudio.gameemulator.ui.theme.StoreColors

/** Set B layouts are drawn on a 390-dp-wide artboard; positions below are in its dp. */
const val STORE_ARTBOARD_W = 390f

/** A soft colour glow (CSS: blurred ellipse) at artboard position x, y with size w × h. */
data class StoreGlow(val x: Float, val y: Float, val w: Float, val h: Float, val color: Color, val alpha: Float)

private const val GLOW_BLUR = 60f

/** Navy ground, blurred glows and the faint handheld pattern behind Set B screens. */
@Composable
fun RnStoreBackdrop(glows: List<StoreGlow>, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(RnTheme.colors.ground)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / STORE_ARTBOARD_W
            glows.forEach { g ->
                val center = Offset((g.x + g.w / 2) * s, (g.y + g.h / 2) * s)
                val rx = (g.w / 2 + GLOW_BLUR) * s
                val ry = (g.h / 2 + GLOW_BLUR) * s
                val core = ((g.w / 2 - GLOW_BLUR) * s / rx).coerceIn(0f, 0.9f)
                val edge = (g.w / 2 * s / rx).coerceIn(core + 0.01f, 0.98f)
                scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to g.color.copy(alpha = g.alpha),
                            core to g.color.copy(alpha = g.alpha * 0.92f),
                            edge to g.color.copy(alpha = g.alpha * 0.45f),
                            1f to g.color.copy(alpha = 0f),
                            center = center,
                            radius = rx,
                        ),
                        radius = rx,
                        center = center,
                    )
                }
            }
        }
        Image(
            painterResource(R.drawable.store_pattern),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Fixed [designW] × [designH] artboard region, scaled to fit the space it gets (never above
 * 1.15×) so absolute Set B positions hold on any phone.
 */
@Composable
fun RnStoreStage(designW: Dp, designH: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth / designW, maxHeight / designH, 1.15f)
        Box(
            Modifier.requiredSize(designW, designH).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            content = content,
        )
    }
}

private fun storeText(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = BeVietnamPro, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** Centered 30 sp headline with [highlight] (if it occurs in [text]) in light blue. */
@Composable
fun RnStoreTitle(text: String, highlight: String, modifier: Modifier = Modifier, onLineCount: (Int) -> Unit = {}) {
    val i = if (highlight.isEmpty()) -1 else text.indexOf(highlight)
    val annotated = buildAnnotatedString {
        if (i < 0) {
            append(text)
        } else {
            append(text.substring(0, i))
            withStyle(SpanStyle(color = StoreColors.Highlight)) { append(highlight) }
            append(text.substring(i + highlight.length))
        }
    }
    Text(
        annotated,
        style = storeText(30, 36, FontWeight.ExtraBold).copy(letterSpacing = (-0.5).sp, lineBreak = LineBreak.Heading),
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = modifier,
        onTextLayout = { onLineCount(it.lineCount) },
    )
}

@Composable
fun RnStoreBody(text: String, modifier: Modifier = Modifier) {
    Text(text, style = storeText(15, 22, FontWeight.Normal), color = StoreColors.Body, textAlign = TextAlign.Center, modifier = modifier)
}

/** Set B primary CTA: #0070D0 pill, white label, blue glow. */
@Composable
fun RnGlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes trailingIcon: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = shape,
        color = if (enabled) StoreColors.Cta else StoreColors.Cta.copy(alpha = 0.4f),
        contentColor = Color.White,
        modifier = modifier
            .then(if (enabled) Modifier.shadow(14.dp, shape, ambientColor = StoreColors.Cta, spotColor = StoreColors.Cta) else Modifier)
            .heightIn(min = 52.dp),
    ) {
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(text, style = storeText(15, 20, FontWeight.Bold), color = Color.White, maxLines = 1)
                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    RnIcon(trailingIcon, tint = Color.White, size = 20.dp)
                }
            }
        }
    }
}

/** Pill chip floating over the art (e.g. "60 FPS"). */
@Composable
fun RnStoreChip(text: String, @DrawableRes icon: Int, container: Color, content: Color, iconTint: Color = content, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(container)
            .height(36.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RnIcon(icon, tint = iconTint, size = 18.dp)
        Text(text, style = storeText(14, 18, FontWeight.ExtraBold), color = content, maxLines = 1)
    }
}

/** White floating card: icon tile + small label + bold value. */
@Composable
fun RnStoreCard(
    label: String,
    value: String,
    @DrawableRes icon: Int,
    iconContainer: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    iconShape: RoundedCornerShape = RoundedCornerShape(12.dp),
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .shadow(14.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(38.dp).clip(iconShape).background(iconContainer), contentAlignment = Alignment.Center) {
            RnIcon(icon, tint = iconTint, size = 22.dp)
        }
        Column {
            Text(label, style = storeText(12, 16, FontWeight.SemiBold), color = StoreColors.CardMuted, maxLines = 1)
            Text(value, style = storeText(15, 20, FontWeight.ExtraBold), color = StoreColors.CardInk, maxLines = 1)
        }
    }
}

/**
 * Device bezel around a screenshot: [frameRadius] outer, 7 dp bezel. [overlay] is laid out
 * over the whole frame (bezel included), as in the artboard.
 */
@Composable
fun RnDeviceShot(
    @DrawableRes image: Int,
    contentDescription: String?,
    frameRadius: Dp,
    modifier: Modifier = Modifier,
    alignTop: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val outer = RoundedCornerShape(frameRadius)
    Box(
        modifier
            .shadow(22.dp, outer, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(outer)
            .background(StoreColors.DeviceFill)
            .border(BorderStroke(1.5.dp, StoreColors.DeviceEdge), outer),
    ) {
        Image(
            painterResource(image),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            alignment = if (alignTop) Alignment.TopCenter else Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(7.dp).clip(RoundedCornerShape(frameRadius - 6.dp)),
        )
        overlay()
    }
}

private val PadGlass = Color(0x8F080C14)
private val PadEdge = Color(0x47FFFFFF)

/** The app's on-screen D-pad drawn at [scale] (1 = 132 dp), decorative. */
@Composable
fun RnMiniDpad(scale: Float, modifier: Modifier = Modifier) {
    val k = 44.dp * scale
    val shape = RoundedCornerShape(10.dp * scale)
    Box(modifier.size(132.dp * scale)) {
        listOf(
            Alignment.TopCenter to R.drawable.ms_arrow_drop_up,
            Alignment.BottomCenter to R.drawable.ms_arrow_drop_down,
            Alignment.CenterStart to R.drawable.ms_arrow_left,
            Alignment.CenterEnd to R.drawable.ms_arrow_right,
        ).forEach { (align, icon) ->
            Box(
                Modifier.align(align).size(k).clip(shape).background(PadGlass).border(BorderStroke(1.5.dp * scale, PadEdge), shape),
                contentAlignment = Alignment.Center,
            ) { RnIcon(icon, tint = Color.White, size = 30.dp * scale) }
        }
    }
}

/** The app's A B X Y cluster drawn at [scale] (1 = 144 dp), decorative. */
@Composable
fun RnMiniFace(scale: Float, modifier: Modifier = Modifier) {
    val k = 50.dp * scale
    Box(modifier.size(144.dp * scale)) {
        listOf(
            Alignment.TopCenter to "Y",
            Alignment.CenterStart to "X",
            Alignment.CenterEnd to "B",
            Alignment.BottomCenter to "A",
        ).forEach { (align, label) ->
            val a = label == "A"
            Box(
                Modifier
                    .align(align)
                    .size(k)
                    .clip(CircleShape)
                    .background(if (a) Color(0xD90070D1) else PadGlass)
                    .border(BorderStroke(1.5.dp * scale, if (a) Color(0x80FFFFFF) else PadEdge), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = storeText((16 * scale).toInt().coerceAtLeast(7), (18 * scale).toInt().coerceAtLeast(8), FontWeight.Bold), color = Color.White)
            }
        }
    }
}

/** Absolute placement helper for artboard coordinates. */
fun Modifier.at(x: Dp, y: Dp): Modifier = offset(x, y)
