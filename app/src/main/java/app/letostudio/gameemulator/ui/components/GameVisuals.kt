package app.letostudio.gameemulator.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.theme.RnRadius
import app.letostudio.gameemulator.ui.theme.RnTheme
import coil3.compose.AsyncImage
import java.io.File

/** The "sun over peaks" fallback art palettes (F05: palette by hash of title). */
private val ArtPalettes = listOf(
    listOf(0xFF0D1B3E, 0xFF2F86FF, 0xFFBFD8FF, 0xFF1B3A6B, 0xFF16305A),
    listOf(0xFF2A1B3D, 0xFFF07A3A, 0xFFFFC56B, 0xFF6B3A5E, 0xFF3E2650),
    listOf(0xFF2A120C, 0xFFEF5A1E, 0xFFFFB534, 0xFF5A2416, 0xFF401A10),
    listOf(0xFF16261C, 0xFF6CC27A, 0xFFE6F7B4, 0xFF2A5238, 0xFF1F3F2B),
    listOf(0xFF1E1631, 0xFFA77BEA, 0xFFFFD6F2, 0xFF3D2D63, 0xFF2C2148),
    listOf(0xFF2E2412, 0xFFE3B54A, 0xFFFFF0C2, 0xFF5C4A22, 0xFF43361A),
).map { p -> p.map { Color(it) } }

fun artPaletteIndex(seed: String): Int = Math.floorMod(seed.hashCode(), ArtPalettes.size)

/** Vector cover art — a scene drawn from a palette chosen by [seed]. */
@Composable
fun GameArt(seed: String, modifier: Modifier = Modifier, sunX: Float? = null) {
    val p = ArtPalettes[artPaletteIndex(seed)]
    val sx = sunX ?: (0.35f + (Math.floorMod(seed.hashCode() / 7, 30)) / 100f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(p[0])
        val r = minOf(w, h) * 0.17f
        drawCircle(p[1].copy(alpha = 0.9f), radius = r, center = Offset(w * sx, h * 0.35f))
        drawCircle(p[2], radius = r * 0.59f, center = Offset(w * sx, h * 0.35f))
        val peaks = Path().apply {
            moveTo(0f, h * 0.70f)
            lineTo(w * 0.20f, h * 0.48f)
            lineTo(w * 0.39f, h * 0.61f)
            lineTo(w * 0.58f, h * 0.41f)
            lineTo(w * 0.76f, h * 0.59f)
            lineTo(w, h * 0.48f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(peaks, p[3])
        val wave = Path().apply {
            moveTo(0f, h * 0.80f)
            quadraticTo(w * 0.25f, h * 0.72f, w * 0.5f, h * 0.79f)
            quadraticTo(w * 0.75f, h * 0.86f, w, h * 0.77f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(wave, p[4])
    }
}

/** Cover from the ICON0 cache when present, else [GameArt]. */
@Composable
fun GameCover(seed: String, coverPath: String?, modifier: Modifier = Modifier) {
    Box(modifier) {
        GameArt(seed, Modifier.fillMaxSize())
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Library grid card: 1:1 art, format badge, title, meta. */
@Composable
fun GameCard(
    title: String,
    meta: String,
    format: String,
    seed: String,
    coverPath: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(RnRadius.lg))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(2.dp, RoundedCornerShape(RnRadius.lg))
                .clip(RoundedCornerShape(RnRadius.lg))
                .background(RnTheme.colors.surfaceSunken),
        ) {
            GameCover(seed, coverPath, Modifier.fillMaxSize())
            RnBadge(format, Modifier.padding(8.dp))
        }
        Text(title, style = RnTheme.type.subtitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)), color = RnTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(meta, style = RnTheme.type.caption, color = RnTheme.colors.inkMuted, maxLines = 1, modifier = Modifier.offset(y = (-4).dp))
    }
}

/** Library "Continue playing" hero card (180 dp, navy, progress in accent, ▶ button). */
@Composable
fun ContinueCard(
    eyebrow: String,
    title: String,
    meta: String,
    progress: Float,
    seed: String,
    coverPath: String?,
    playDescription: String,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(180.dp)
            .shadow(6.dp, RoundedCornerShape(RnRadius.xl))
            .clip(RoundedCornerShape(RnRadius.xl))
            .background(RnTheme.colors.primaryDeep)
            .clickable(role = Role.Button, onClick = onOpen),
    ) {
        GameCover(seed, coverPath, Modifier.fillMaxSize())
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0x000A0706), Color(0xD10A0706))))
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(eyebrow.uppercase(), style = RnTheme.type.overline, color = Color(0xFFBFD8FF))
                Text(title, style = RnTheme.type.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = RnTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = Color.White.copy(alpha = 0.82f), maxLines = 1)
                Spacer(Modifier.height(8.dp))
                RnProgress(progress)
            }
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = Color(0xFF6AAEFF),
                modifier = Modifier.size(52.dp).semantics { contentDescription = playDescription },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RnIcon(R.drawable.ms_play_arrow_fill, tint = Color(0xFF04122B), size = 30.dp)
                }
            }
        }
    }
}

/** Save slot row: 96×54 thumb, title, meta; AUTO-SAVE label in accent for the auto slot. */
@Composable
fun SaveSlotRow(
    title: String,
    meta: String,
    seed: String,
    thumbPath: String?,
    isAuto: Boolean,
    autoLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(RnRadius.sm)).background(RnTheme.colors.surfaceSunken),
        ) {
            if (thumbPath != null) {
                AsyncImage(model = File(thumbPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                GameArt(seed, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            if (isAuto) Text(autoLabel.uppercase(), style = RnTheme.type.overline.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.6f, androidx.compose.ui.unit.TextUnitType.Sp)), color = RnTheme.colors.accent)
            Text(title, style = RnTheme.type.subtitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)), color = RnTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = RnTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = RnTheme.colors.inkMuted, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/** Paywall plan card: radio, name + note, price + per, optional badge. */
@Composable
fun RnPlanCard(
    name: String,
    note: String,
    price: String,
    per: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val c = RnTheme.colors
    Box(modifier.fillMaxWidth().padding(top = if (badge != null) 10.dp else 0.dp)) {
        Surface(
            shape = RoundedCornerShape(RnRadius.lg),
            color = if (selected) c.tint else c.surface,
            border = BorderStroke(1.5.dp, if (selected) c.primaryStrong else c.line),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RnRadius.lg))
                .selectable(selected, role = Role.RadioButton, onClick = onClick),
        ) {
            Row(
                Modifier.heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .then(Modifier.background(Color.Transparent)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(22.dp)) {
                        val stroke = 2.dp.toPx()
                        drawCircle(
                            if (selected) c.primaryStrong else c.inkMuted,
                            radius = size.minDimension / 2 - stroke / 2,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                        )
                        if (selected) drawCircle(c.primaryStrong, radius = 5.dp.toPx())
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = RnTheme.type.subtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.ink)
                    Text(note, style = RnTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.inkMuted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(price, style = RnTheme.type.subtitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = c.ink)
                    Text(per, style = RnTheme.type.caption, color = c.inkMuted)
                }
            }
        }
        if (badge != null) {
            RnBadge(
                badge,
                Modifier.align(Alignment.TopEnd).offset(x = (-16).dp, y = (-10).dp),
                container = c.primaryStrong,
                content = c.onPrimary,
            )
        }
    }
}

/** Dark hero block (navy) used by Homebrew and fallback paywall art. */
@Composable
fun RnHeroCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RnRadius.xl))
            .background(RnTheme.colors.primaryDeep)
            .padding(20.dp),
        content = content,
    )
}

/** Width helper for placeholders. */
@Composable
fun RnSpacer(height: androidx.compose.ui.unit.Dp) = Spacer(Modifier.height(height).width(1.dp))
