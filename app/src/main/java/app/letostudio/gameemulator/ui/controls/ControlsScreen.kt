package app.letostudio.gameemulator.ui.controls

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.input.GamepadManager
import app.letostudio.gameemulator.input.KeyMapper
import app.letostudio.gameemulator.input.PadButton
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnButtonStyle
import app.letostudio.gameemulator.ui.components.RnCardGroup
import app.letostudio.gameemulator.ui.components.RnDivider
import app.letostudio.gameemulator.ui.components.RnListRow
import app.letostudio.gameemulator.ui.components.RnNote
import app.letostudio.gameemulator.ui.components.RnOverline
import app.letostudio.gameemulator.ui.components.RnSlider
import app.letostudio.gameemulator.ui.components.RnToggle
import app.letostudio.gameemulator.ui.components.RnTopBar
import app.letostudio.gameemulator.ui.theme.RnTheme
import app.letostudio.gameemulator.data.prefs.PAD_SIZE_MAX
import app.letostudio.gameemulator.data.prefs.PAD_SIZE_MIN
import app.letostudio.gameemulator.data.prefs.PAD_SIZE_STEPS
import kotlin.math.roundToInt

/** Controls (F08 opacity + haptics, F09 remap). The drag-and-drop editor (S12) is v1. */
@Composable
fun ControlsRoute(onBack: () -> Unit, viewModel: ControlsViewModel = hiltViewModel()) {
    val pad by viewModel.pad.collectAsStateWithLifecycle(initialValue = app.letostudio.gameemulator.data.prefs.PadPrefs())
    val mapping by viewModel.mapping.collectAsStateWithLifecycle(initialValue = emptyMap())
    val pads by viewModel.pads.collectAsStateWithLifecycle()
    var opacity by remember(pad.opacity) { mutableFloatStateOf(pad.opacity) }
    var size by remember(pad.size) { mutableFloatStateOf(pad.size) }
    var capturing by remember { mutableStateOf<PadButton?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(capturing) { if (capturing != null) focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val target = capturing
                val native = e.nativeKeyEvent
                if (target != null && native.action == KeyEvent.ACTION_DOWN && GamepadManager.isGamepadEvent(native)) {
                    viewModel.assign(target, native.keyCode)
                    capturing = null
                    true
                } else {
                    false
                }
            },
    ) {
        RnTopBar(stringResource(R.string.controls_title), large = false, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RnCardGroup {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.controls_opacity), style = RnTheme.type.subtitle, color = RnTheme.colors.ink, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.controls_opacity_value, (opacity * 100).toInt()), style = RnTheme.type.label, color = RnTheme.colors.inkMuted)
                    }
                    RnSlider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.2f..1f,
                        onValueChangeFinished = { viewModel.setOpacity(opacity); viewModel.logOpacity(opacity) },
                    )
                }
                RnDivider()
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.controls_size), style = RnTheme.type.subtitle, color = RnTheme.colors.ink, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.controls_opacity_value, (size * 100).roundToInt()), style = RnTheme.type.label, color = RnTheme.colors.inkMuted)
                    }
                    RnSlider(
                        value = size,
                        onValueChange = { size = it },
                        valueRange = PAD_SIZE_MIN..PAD_SIZE_MAX,
                        steps = PAD_SIZE_STEPS,
                        onValueChangeFinished = { viewModel.setSize(size) },
                    )
                }
                RnDivider()
                RnListRow(stringResource(R.string.controls_haptics), subtitle = stringResource(R.string.controls_haptics_sub)) {
                    RnToggle(pad.haptics, viewModel::setHaptics)
                }
            }
            RnNote(stringResource(R.string.controls_editor_soon))

            RnOverline(stringResource(R.string.controls_gamepad), Modifier.padding(top = 8.dp, start = 4.dp))
            if (pads.isEmpty()) {
                RnNote(stringResource(R.string.controls_gamepad_none), icon = R.drawable.ms_bluetooth)
            } else {
                Text(stringResource(R.string.settings_controls_pad, pads.first().name), style = RnTheme.type.subtitle, color = RnTheme.colors.ink)
            }
            Text(stringResource(R.string.controls_remap_sub), style = RnTheme.type.caption, color = RnTheme.colors.inkMuted)
            RnCardGroup {
                PadButton.entries.forEachIndexed { i, b ->
                    if (i > 0) RnDivider()
                    val physical = KeyMapper.physicalFor(mapping, b)
                    RnListRow(
                        b.label,
                        subtitle = if (capturing == b) stringResource(R.string.controls_remap_wait, b.label) else null,
                        trailingText = KeyEvent.keyCodeToString(physical).removePrefix("KEYCODE_").replace('_', ' '),
                        onClick = { capturing = b },
                    )
                }
            }
            RnButton(stringResource(R.string.controls_reset), { viewModel.reset() }, Modifier.fillMaxWidth(), style = RnButtonStyle.Secondary)
        }
    }
}
