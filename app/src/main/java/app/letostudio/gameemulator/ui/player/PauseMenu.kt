package app.letostudio.gameemulator.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.core.SmartSetup
import app.letostudio.gameemulator.data.db.SaveSlotEntity
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnChoiceList
import app.letostudio.gameemulator.ui.components.RnContentDialog
import app.letostudio.gameemulator.ui.components.RnDivider
import app.letostudio.gameemulator.ui.components.RnSlider
import app.letostudio.gameemulator.ui.components.RnToggle
import app.letostudio.gameemulator.ui.components.SaveSlotRow
import app.letostudio.gameemulator.ui.theme.RnTheme
import app.letostudio.gameemulator.util.Formats

/** S10 Pause menu (landscape overlay): info left, 380 dp action sheet right. */
@Composable
fun PauseMenu(
    state: PlayerUiState,
    onResume: () -> Unit,
    onSave: () -> Unit,
    onLoad: (SaveSlotEntity) -> Unit,
    onSpeed: (Int) -> Unit,
    onScreenshot: () -> Unit,
    onExit: () -> Unit,
    onPadOpacity: (Float) -> Unit,
    onPadHaptics: (Boolean) -> Unit,
    onFixLag: () -> Unit,
    onResolution: (Int) -> Unit,
    onShowFps: (Boolean) -> Unit,
) {
    val game = state.game ?: return
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    val c = RnTheme.colors
    Row(Modifier.fillMaxSize().background(c.scrim)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.pause_eyebrow).uppercase(), style = RnTheme.type.overline, color = Color(0xFFBFD8FF))
            Text(game.displayTitle, style = RnTheme.type.headline, color = Color.White)
            Text(
                state.lastAutoSaveAt?.let { stringResource(R.string.pause_autosaved, Formats.ago(it)) } ?: stringResource(R.string.pause_not_saved),
                style = RnTheme.type.caption,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Column(
            Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(c.surface, RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
        ) {
            RnButton(stringResource(R.string.pause_resume), onResume, Modifier.fillMaxWidth().padding(horizontal = 16.dp), small = true, leadingIcon = R.drawable.ms_play_arrow_fill)
            Box(Modifier.padding(top = 8.dp))
            PauseRow(stringResource(R.string.pause_save), icon = R.drawable.ms_save, onClick = onSave)
            PauseRow(stringResource(R.string.pause_load), icon = R.drawable.ms_settings_backup_restore, enabled = state.slots.isNotEmpty(), onClick = { dialog = "load" })
            PauseRow(stringResource(R.string.pause_controls), icon = R.drawable.ms_sports_esports, onClick = { dialog = "controls" })
            PauseRow(
                stringResource(if (state.speed == 2) R.string.pause_speed_1 else R.string.pause_speed_2),
                icon = R.drawable.ms_fast_forward,
                onClick = { onSpeed(if (state.speed == 2) 1 else 2) },
            )
            PauseRow(stringResource(R.string.pause_graphics), icon = R.drawable.ms_tune, onClick = { dialog = "graphics" })
            PauseRow(stringResource(R.string.pause_screenshot), icon = R.drawable.ms_screenshot, onClick = onScreenshot)
            RnDivider()
            PauseRow(stringResource(R.string.pause_exit), icon = R.drawable.ms_logout, iconTint = c.danger, onClick = onExit)
            Text(
                stringResource(R.string.pause_exit_caption),
                style = RnTheme.type.caption,
                color = c.inkMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }

    when (dialog) {
        "load" -> RnContentDialog(stringResource(R.string.load_pick_title), null, {}, { dialog = null }, dismiss = stringResource(R.string.action_cancel)) {
            state.slots.forEach { slot ->
                SaveSlotRow(
                    title = if (slot.isAuto) "Slot A" else stringResource(R.string.game_slot_name, slot.slotIndex),
                    meta = Formats.dateTime(context, slot.savedAt),
                    seed = game.title + slot.slotIndex,
                    thumbPath = slot.thumbPath,
                    isAuto = slot.isAuto,
                    autoLabel = stringResource(R.string.game_slot_auto),
                    onClick = { dialog = null; onLoad(slot) },
                )
            }
        }
        "controls" -> {
            var opacity by remember { mutableFloatStateOf(state.pad.opacity) }
            RnContentDialog(stringResource(R.string.controls_title), stringResource(R.string.action_ok), { dialog = null }, { dialog = null }) {
                Text(stringResource(R.string.controls_opacity) + " · " + stringResource(R.string.controls_opacity_value, (opacity * 100).toInt()), style = RnTheme.type.label, color = c.ink)
                RnSlider(opacity, { opacity = it }, 0.2f..1f, onValueChangeFinished = { onPadOpacity(opacity) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.controls_haptics), style = RnTheme.type.label, color = c.ink, modifier = Modifier.weight(1f))
                    RnToggle(state.pad.haptics, onPadHaptics)
                }
            }
        }
        "graphics" -> RnContentDialog(stringResource(R.string.pause_graphics), stringResource(R.string.action_ok), { dialog = null }, { dialog = null }) {
            val next = SmartSetup.nextFixLag(state.settings)
            RnButton(
                stringResource(if (next == null) R.string.emu_fix_lag_lowest else R.string.emu_fix_lag),
                onFixLag,
                small = true,
                enabled = next != null,
                leadingIcon = R.drawable.ms_speed,
            )
            Text(stringResource(R.string.emu_resolution), style = RnTheme.type.label, color = c.ink)
            RnChoiceList(listOf(1 to stringResource(R.string.emu_res_value, 1), 2 to stringResource(R.string.emu_res_value, 2)), state.settings.resolution, onResolution)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.emu_show_fps), style = RnTheme.type.label, color = c.ink, modifier = Modifier.weight(1f))
                RnToggle(state.settings.showFps, onShowFps)
            }
        }
    }
}

/** Dense row for the landscape pause sheet (48 dp, icon 20 dp). */
@Composable
private fun PauseRow(
    title: String,
    icon: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconTint: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        app.letostudio.gameemulator.ui.components.RnIcon(icon, tint = iconTint ?: RnTheme.colors.primaryStrong, size = 22.dp)
        Text(title, style = RnTheme.type.label, color = if (enabled) RnTheme.colors.ink else RnTheme.colors.inkMuted)
    }
}
