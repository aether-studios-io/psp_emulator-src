package app.letostudio.gameemulator.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.theme.RnRadius
import app.letostudio.gameemulator.ui.theme.RnTheme

/** Two-button dialog. */
@Composable
fun RnDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String? = null,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RnTheme.colors.surface,
        shape = RoundedCornerShape(RnRadius.xl),
        title = { Text(title, style = RnTheme.type.title, color = RnTheme.colors.ink) },
        text = { Text(text, style = RnTheme.type.body, color = RnTheme.colors.inkMuted) },
        confirmButton = {
            RnButton(confirm, onConfirm, small = true, style = if (destructive) RnButtonStyle.Danger else RnButtonStyle.Primary)
        },
        dismissButton = dismiss?.let { { RnButton(it, onDismiss, small = true, style = RnButtonStyle.Ghost) } },
    )
}

/** Dialog with custom content (text fields, slot picker). */
@Composable
fun RnContentDialog(
    title: String,
    confirm: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String? = null,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RnTheme.colors.surface,
        shape = RoundedCornerShape(RnRadius.xl),
        title = { Text(title, style = RnTheme.type.title, color = RnTheme.colors.ink) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content) },
        confirmButton = { if (confirm != null) RnButton(confirm, onConfirm, small = true, enabled = confirmEnabled) },
        dismissButton = dismiss?.let { { RnButton(it, onDismiss, small = true, style = RnButtonStyle.Ghost) } },
    )
}

/** Modal bottom sheet on surface with 24 dp top radius. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RnSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = RnTheme.colors.surface,
        shape = RoundedCornerShape(topStart = RnRadius.xl, topEnd = RnRadius.xl),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Overflow menu entry. */
data class RnMenuItem(val label: String, @DrawableRes val icon: Int?, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun RnMenu(expanded: Boolean, onDismiss: () -> Unit, items: List<RnMenuItem>) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = RnTheme.colors.surface) {
        items.forEach { item ->
            val color = if (item.destructive) RnTheme.colors.danger else RnTheme.colors.ink
            DropdownMenuItem(
                text = { Text(item.label, style = RnTheme.type.body, color = color) },
                leadingIcon = item.icon?.let { { RnIcon(it, tint = color, size = 20.dp) } },
                onClick = { onDismiss(); item.onClick() },
            )
        }
    }
}

/** Brand slider (opacity, size). */
@Composable
fun RnSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = RnTheme.colors.primary,
            activeTrackColor = RnTheme.colors.primary,
            inactiveTrackColor = RnTheme.colors.line,
        ),
    )
}

/** Single-choice list (resolution, latency pickers). */
@Composable
fun <T> RnChoiceList(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RnRadius.md))
                    .selectable(isSel, role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 8.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RnIcon(
                    if (isSel) R.drawable.ms_check_circle_fill else R.drawable.ms_check,
                    tint = if (isSel) RnTheme.colors.primaryStrong else androidx.compose.ui.graphics.Color.Transparent,
                    size = 22.dp,
                )
                Text(label, style = RnTheme.type.body, color = RnTheme.colors.ink)
            }
        }
    }
}

/** Bottom navigation item model. */
data class RnNavItem(val label: String, @DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int)

/** Bottom nav: 56×30 pill on tint when selected (design_token › bottomNav). */
@Composable
fun RnBottomNav(items: List<RnNavItem>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val c = RnTheme.colors
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        RnDivider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 8.dp)) {
            items.forEachIndexed { i, item ->
                val sel = i == selectedIndex
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(RnRadius.md))
                        .selectable(sel, role = Role.Tab) { onSelect(i) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .clip(RoundedCornerShape(RnRadius.pill))
                            .background(if (sel) c.tint else androidx.compose.ui.graphics.Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        RnIcon(if (sel) item.selectedIcon else item.icon, tint = if (sel) c.primaryStrong else c.inkMuted)
                    }
                    Text(item.label, style = RnTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = if (sel) c.primaryStrong else c.inkMuted)
                }
            }
        }
    }
}
