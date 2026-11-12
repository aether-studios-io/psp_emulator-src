package app.letostudio.gameemulator.ui.emusettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.core.FixLagStep
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnButtonStyle
import app.letostudio.gameemulator.ui.components.RnCardGroup
import app.letostudio.gameemulator.ui.components.RnChip
import app.letostudio.gameemulator.ui.components.RnChipRow
import app.letostudio.gameemulator.ui.components.RnChoiceList
import app.letostudio.gameemulator.ui.components.RnContentDialog
import app.letostudio.gameemulator.ui.components.RnDivider
import app.letostudio.gameemulator.ui.components.RnIconTile
import app.letostudio.gameemulator.ui.components.RnListRow
import app.letostudio.gameemulator.ui.components.RnNativeAd
import app.letostudio.gameemulator.ui.components.RnOverline
import app.letostudio.gameemulator.ui.components.RnProBadge
import app.letostudio.gameemulator.ui.components.RnSheet
import app.letostudio.gameemulator.ui.components.RnToggle
import app.letostudio.gameemulator.ui.components.RnTopBar
import app.letostudio.gameemulator.ui.theme.RnTheme

@Composable
fun EmuSettingsRoute(onBack: () -> Unit, onPaywall: () -> Unit, snackbar: SnackbarHostState, viewModel: EmuSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ad by viewModel.sheetAd.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    var sheet by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            if (e is EmuEvent.Toast) {
                snackbar.showSnackbar(
                    res.getString(
                        when (e.step) {
                            FixLagStep.RESOLUTION -> R.string.emu_toast_res
                            FixLagStep.FRAME_SKIP -> R.string.emu_toast_frameskip
                            FixLagStep.EFFECTS -> R.string.emu_toast_effects
                        },
                    ),
                )
            }
        }
    }
    val s = state.settings
    val tierLabel = stringResource(
        when (state.tier) {
            "low" -> R.string.tier_low
            "mid" -> R.string.tier_mid
            "high" -> R.string.tier_high
            else -> R.string.tier_unknown
        },
    )
    Column(Modifier.fillMaxSize()) {
        RnTopBar(
            stringResource(R.string.emu_title),
            large = false,
            onBack = onBack,
            subtitle = state.game?.let { stringResource(R.string.emu_for_game, it.displayTitle) },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RnCardGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RnIconTile(R.drawable.ms_bolt_fill, size = 44.dp, iconSize = 24.dp, container = RnTheme.colors.primary, tint = RnTheme.colors.onPrimary)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(if (s.smartSetupOn) R.string.emu_smart_on else R.string.emu_smart_off), style = RnTheme.type.subtitle, color = RnTheme.colors.ink)
                            Text(stringResource(R.string.emu_smart_sub, tierLabel, stringResource(R.string.emu_backend_gles)), style = RnTheme.type.caption, color = RnTheme.colors.inkMuted)
                        }
                        RnToggle(s.smartSetupOn, viewModel::setSmartSetup)
                    }
                    RnChipRow {
                        RnChip(stringResource(R.string.emu_res_value, s.resolution), false, {})
                        RnChip(stringResource(R.string.emu_backend_gles), false, {})
                        RnChip(stringResource(if (s.frameSkip > 0) R.string.emu_frame_skip_auto else R.string.emu_frame_skip_off), false, {})
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RnButton(
                            stringResource(if (state.nextFixLag == null) R.string.emu_fix_lag_lowest else R.string.emu_fix_lag),
                            viewModel::fixLag,
                            small = true,
                            enabled = state.nextFixLag != null,
                            leadingIcon = R.drawable.ms_speed,
                        )
                        RnButton(stringResource(R.string.emu_what), { sheet = true; viewModel.onSheetOpen() }, small = true, style = RnButtonStyle.Ghost)
                    }
                }
            }

            RnOverline(stringResource(R.string.emu_group_graphics), Modifier.padding(top = 8.dp, start = 4.dp))
            RnCardGroup {
                RnListRow(stringResource(R.string.emu_backend), trailingText = stringResource(R.string.emu_backend_gles))
                RnDivider()
                RnListRow(stringResource(R.string.emu_resolution), trailingText = stringResource(R.string.emu_res_value, s.resolution), showChevron = true, onClick = { picker = "res" })
                RnDivider()
                RnListRow(
                    stringResource(R.string.emu_frame_skip),
                    trailingText = stringResource(if (s.frameSkip > 0) R.string.emu_frame_skip_auto else R.string.emu_frame_skip_off),
                    showChevron = true,
                    onClick = { picker = "skip" },
                )
                RnDivider()
                RnListRow(stringResource(R.string.emu_effects)) { RnToggle(s.effectsOn, viewModel::setEffects) }
                RnDivider()
                RnListRow(stringResource(R.string.emu_show_fps)) { RnToggle(s.showFps, viewModel::setShowFps) }
                RnDivider()
                RnListRow(stringResource(R.string.emu_shaders), subtitle = if (state.isPro) stringResource(R.string.coming_soon) else null, onClick = if (state.isPro) null else onPaywall, trailing = { RnProBadge() }, showChevron = !state.isPro)
                RnDivider()
                RnListRow(stringResource(R.string.emu_upscaling), subtitle = if (state.isPro) stringResource(R.string.coming_soon) else null, onClick = if (state.isPro) null else onPaywall, trailing = { RnProBadge() }, showChevron = !state.isPro)
            }

            RnOverline(stringResource(R.string.emu_group_audio), Modifier.padding(top = 8.dp, start = 4.dp))
            RnCardGroup {
                RnListRow(stringResource(R.string.emu_sound)) { RnToggle(s.soundOn, viewModel::setSound) }
                RnDivider()
                RnListRow(stringResource(R.string.emu_latency), trailingText = latencyLabel(s.audioLatency), showChevron = true, onClick = { picker = "latency" })
            }
        }
    }

    when (picker) {
        "res" -> RnContentDialog(stringResource(R.string.emu_resolution), null, {}, { picker = null }, dismiss = stringResource(R.string.action_close)) {
            RnChoiceList(listOf(1 to stringResource(R.string.emu_res_value, 1), 2 to stringResource(R.string.emu_res_value, 2)), s.resolution) { viewModel.setResolution(it); picker = null }
        }
        "skip" -> RnContentDialog(stringResource(R.string.emu_frame_skip), null, {}, { picker = null }, dismiss = stringResource(R.string.action_close)) {
            RnChoiceList(listOf(0 to stringResource(R.string.emu_frame_skip_off), 1 to stringResource(R.string.emu_frame_skip_auto)), s.frameSkip) { viewModel.setFrameSkip(it); picker = null }
        }
        "latency" -> RnContentDialog(stringResource(R.string.emu_latency), null, {}, { picker = null }, dismiss = stringResource(R.string.action_close)) {
            RnChoiceList(
                listOf("low" to stringResource(R.string.emu_latency_low), "normal" to stringResource(R.string.emu_latency_normal), "high" to stringResource(R.string.emu_latency_high)),
                s.audioLatency,
            ) { viewModel.setLatency(it); picker = null }
        }
    }

    if (sheet) {
        RnSheet(onDismiss = { sheet = false }) {
            Text(stringResource(R.string.emu_sheet_title), style = RnTheme.type.title, color = RnTheme.colors.ink)
            Text(stringResource(R.string.emu_sheet_body), style = RnTheme.type.body, color = RnTheme.colors.inkMuted)
            RnButton(stringResource(R.string.action_close), { sheet = false }, Modifier.fillMaxWidth())
            // N5 Native Small under Close.
            RnNativeAd(ad, medium = false)
        }
    }
}

@Composable
private fun latencyLabel(v: String) = stringResource(
    when (v) {
        "low" -> R.string.emu_latency_low
        "high" -> R.string.emu_latency_high
        else -> R.string.emu_latency_normal
    },
)
