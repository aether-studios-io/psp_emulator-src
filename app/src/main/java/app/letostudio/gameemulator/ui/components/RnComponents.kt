package app.letostudio.gameemulator.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.ui.theme.RnRadius
import app.letostudio.gameemulator.ui.theme.RnTheme

// RetroNova component library (design_token.json › components, styles/app.css). Screens are
// built only from these + layout primitives (Konsist rule).

/** Material Symbols Rounded icon from res/drawable/ms_*.xml (tools/fetch_icons.py). */
@Composable
fun RnIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tint: Color = RnTheme.colors.ink,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Icon(painterResource(icon), contentDescription, modifier.size(size), tint = tint)
}

enum class RnButtonStyle { Primary, Secondary, Ghost, Danger }

/** Pill button: 52 dp (40 dp small), primary = primaryStrong fill with glow. */
@Composable
fun RnButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RnButtonStyle = RnButtonStyle.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
) {
    val c = RnTheme.colors
    val (container, content, border) = when (style) {
        RnButtonStyle.Primary -> Triple(c.primaryStrong, c.onPrimary, null)
        RnButtonStyle.Secondary -> Triple(c.surface, c.ink, BorderStroke(1.dp, c.line))
        RnButtonStyle.Ghost -> Triple(Color.Transparent, c.primaryStrong, null)
        RnButtonStyle.Danger -> Triple(c.surface, c.danger, BorderStroke(1.dp, c.line))
    }
    val shape = RoundedCornerShape(RnRadius.pill)
    val height = if (small) 40.dp else 52.dp
    val glow = if (style == RnButtonStyle.Primary && enabled) {
        Modifier.shadow(10.dp, shape, ambientColor = c.primary, spotColor = c.primary)
    } else {
        Modifier
    }
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = shape,
        color = if (enabled) container else container.copy(alpha = if (style == RnButtonStyle.Primary) 0.4f else 1f),
        contentColor = if (enabled) content else content.copy(alpha = 0.5f),
        border = border,
        modifier = modifier.then(glow).heightIn(min = height).semantics { },
    ) {
        Row(
            Modifier.padding(horizontal = if (small) 16.dp else 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
            } else {
                if (leadingIcon != null) {
                    RnIcon(leadingIcon, tint = if (enabled) content else content.copy(alpha = 0.5f), size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text,
                    style = if (small) RnTheme.type.label else RnTheme.type.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

enum class RnIconButtonStyle { Plain, Filled, Glass, Brand }

/** 44 dp icon button (48 dp touch target). */
@Composable
fun RnIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RnIconButtonStyle = RnIconButtonStyle.Plain,
    tint: Color? = null,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
) {
    val c = RnTheme.colors
    val (bg, fg, border) = when (style) {
        RnIconButtonStyle.Plain -> Triple(Color.Transparent, c.ink, null)
        RnIconButtonStyle.Filled -> Triple(c.surface, c.ink, BorderStroke(1.dp, c.line))
        RnIconButtonStyle.Glass -> Triple(c.glass, c.onGlass, null)
        RnIconButtonStyle.Brand -> Triple(c.primaryStrong, c.onPrimary, null)
    }
    Box(
        modifier
            .size(maxOf(size, 48.dp))
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = bg,
            contentColor = tint ?: fg,
            border = border,
            modifier = Modifier.size(size),
        ) {
            Box(contentAlignment = Alignment.Center) {
                RnIcon(icon, tint = tint ?: fg, size = iconSize)
            }
        }
    }
}

/** Filter chip: 36 dp pill; selected = tint fill, primary border, primaryStrong content. */
@Composable
fun RnChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val c = RnTheme.colors
    val bg by animateColorAsState(if (selected) c.tint else c.surface, label = "chipBg")
    val fg = if (selected) c.primaryStrong else c.ink
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(RnRadius.pill))
            .background(bg)
            .border(1.dp, if (selected) c.primary else c.line, RoundedCornerShape(RnRadius.pill))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RnIcon(icon, tint = fg, size = 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = RnTheme.type.label, color = fg, maxLines = 1)
    }
}

@Composable
fun RnChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** 36 dp rounded icon tile on tint (list rows, benefits). */
@Composable
fun RnIconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    container: Color = RnTheme.colors.tint,
    tint: Color = RnTheme.colors.primaryStrong,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(container),
        contentAlignment = Alignment.Center,
    ) { RnIcon(icon, tint = tint, size = iconSize) }
}

/** Settings-style row: icon tile · title/sub · trailing. min 56 dp. */
@Composable
fun RnListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color? = null,
    trailingText: String? = null,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = RnTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) RnIconTile(icon, tint = iconTint ?: c.primaryStrong)
        Column(Modifier.weight(1f)) {
            Text(title, style = RnTheme.type.subtitle, color = if (enabled) c.ink else c.inkMuted)
            if (subtitle != null) Text(subtitle, style = RnTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.inkMuted)
        }
        if (trailingText != null) Text(trailingText, style = RnTheme.type.body.copy(fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c.inkMuted)
        trailing?.invoke()
        if (showChevron) RnIcon(app.letostudio.gameemulator.R.drawable.ms_chevron_right, tint = c.inkMuted, size = 20.dp)
    }
}

/** Group of rows on a surface card with 1 dp dividers. */
@Composable
fun RnCardGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = RnTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RnRadius.lg),
        color = c.surface,
        border = BorderStroke(1.dp, c.line),
    ) { Column { content() } }
}

@Composable
fun RnDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(RnTheme.colors.line))
}

/** Uppercase overline group label (GAMES, APPEARANCE, SUPPORT). */
@Composable
fun RnOverline(text: String, modifier: Modifier = Modifier, color: Color = RnTheme.colors.inkMuted) {
    Text(
        text.uppercase(),
        style = RnTheme.type.overline,
        color = color,
        modifier = modifier.semantics { heading() },
    )
}

/** 52×32 toggle, on = brand fill. */
@Composable
fun RnToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = RnTheme.colors
    val bg by animateColorAsState(if (checked) c.primary else c.line, label = "toggle")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "knob")
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(RnRadius.pill))
            .background(bg)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    ) {
        Box(
            Modifier
                .padding(4.dp)
                .offset(x = x)
                .size(24.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(if (checked) Color.White else c.surface),
        )
    }
}

/** Segmented control (Light / Dark / System, presets). */
@Composable
fun <T> RnSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = RnTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(RnRadius.pill))
            .background(c.surfaceSunken)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .let { if (isSel) it.shadow(1.dp, RoundedCornerShape(RnRadius.pill)) else it }
                    .clip(RoundedCornerShape(RnRadius.pill))
                    .background(if (isSel) c.surface else Color.Transparent)
                    .selectable(isSel, role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = RnTheme.type.label, color = if (isSel) c.ink else c.inkMuted, maxLines = 1)
            }
        }
    }
}

/** Section header "All games · 6" + trailing link. */
@Composable
fun RnSectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = RnTheme.type.title, color = RnTheme.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction == null) {
            Text(action, style = RnTheme.type.label, color = RnTheme.colors.inkMuted)
        }
        if (action != null && onAction != null) {
            Text(
                action,
                style = RnTheme.type.label,
                color = RnTheme.colors.primaryStrong,
                modifier = Modifier
                    .clip(RoundedCornerShape(RnRadius.sm))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
    }
}

/** 56 dp top bar: optional back, title (headline or title style), actions. */
@Composable
fun RnTopBar(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = true,
    onBack: (() -> Unit)? = null,
    backDescription: String = "Back",
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RnIconButton(app.letostudio.gameemulator.R.drawable.ms_arrow_back, backDescription, onBack)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (large) RnTheme.type.headline else RnTheme.type.title,
                color = RnTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) Text(subtitle, style = RnTheme.type.caption, color = RnTheme.colors.inkMuted)
        }
        actions()
    }
}

/** Info note on surfaceSunken (lifetime note, import note). */
@Composable
fun RnNote(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = app.letostudio.gameemulator.R.drawable.ms_info,
    container: Color = RnTheme.colors.surfaceSunken,
    textStyle: TextStyle = RnTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RnRadius.md))
            .background(container)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RnIcon(icon, tint = RnTheme.colors.inkMuted, size = 20.dp)
        Text(text, style = textStyle, color = RnTheme.colors.inkMuted, modifier = Modifier.weight(1f))
    }
}

/** Small glass/primary badge (ISO / CSO / PBP, PRO). */
@Composable
fun RnBadge(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = RnTheme.colors.glass,
    content: Color = RnTheme.colors.onGlass,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    Text(
        text.uppercase(),
        style = RnTheme.type.overline.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.6f, androidx.compose.ui.unit.TextUnitType.Sp)),
        color = content,
        modifier = modifier.clip(shape).background(container).padding(horizontal = 6.dp, vertical = 3.dp),
        textAlign = TextAlign.Center,
    )
}

/** PRO badge used on locked rows. */
@Composable
fun RnProBadge(modifier: Modifier = Modifier) =
    RnBadge("PRO", modifier, container = RnTheme.colors.primaryStrong, content = RnTheme.colors.onPrimary)

/** Progress bar (accent on translucent track by default). */
@Composable
fun RnProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    track: Color = Color.White.copy(alpha = 0.24f),
    fill: Color = Color(0xFFFF9A4D),
) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(4.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(fill))
    }
}

/** Plain clickable text link (Terms · Privacy). */
@Composable
fun RnTextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = RnTheme.colors.primaryStrong, underline: Boolean = false) {
    Text(
        text,
        style = RnTheme.type.caption.copy(
            fontWeight = if (underline) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.SemiBold,
            textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
        ),
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(RnRadius.sm))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .widthIn(min = 48.dp)
            .padding(horizontal = 6.dp, vertical = 14.dp),
        textAlign = TextAlign.Center,
    )
}

/** Shared screen padding: 16 dp gutters. */
val RnScreenPadding = PaddingValues(horizontal = 16.dp)
