package app.letostudio.gameemulator.ui.game

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.data.db.SaveSlotEntity
import app.letostudio.gameemulator.ui.components.GameCover
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnCardGroup
import app.letostudio.gameemulator.ui.components.RnContentDialog
import app.letostudio.gameemulator.ui.components.RnDialog
import app.letostudio.gameemulator.ui.components.RnDivider
import app.letostudio.gameemulator.ui.components.RnIcon
import app.letostudio.gameemulator.ui.components.RnIconButton
import app.letostudio.gameemulator.ui.components.RnIconButtonStyle
import app.letostudio.gameemulator.ui.components.RnListRow
import app.letostudio.gameemulator.ui.components.RnMenu
import app.letostudio.gameemulator.ui.components.RnMenuItem
import app.letostudio.gameemulator.ui.components.RnSectionHeader
import app.letostudio.gameemulator.ui.components.RnTextField
import app.letostudio.gameemulator.ui.components.SaveSlotRow
import app.letostudio.gameemulator.ui.theme.RnRadius
import app.letostudio.gameemulator.ui.theme.RnTheme
import app.letostudio.gameemulator.util.Formats

@Composable
fun GameDetailRoute(
    onBack: () -> Unit,
    onPlay: (gameId: Long, slot: Int?) -> Unit,
    onGraphics: (Long) -> Unit,
    onControls: (Long) -> Unit,
    onPaywall: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: GameDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                GameDetailEvent.Removed -> onBack()
                GameDetailEvent.RewardGranted -> snackbar.showSnackbar(res.getString(R.string.game_rewarded_granted))
                GameDetailEvent.RewardUnavailable -> snackbar.showSnackbar(res.getString(R.string.game_rewarded_unavailable))
            }
        }
    }
    GameDetailScreen(
        state = state,
        onBack = onBack,
        onPlay = { slot -> onPlay(viewModel.gameId, slot) },
        onFavorite = viewModel::toggleFavorite,
        onRename = viewModel::rename,
        onRemove = viewModel::remove,
        fileLocation = viewModel::fileLocation,
        onWatchAd = { (context as? Activity)?.let(viewModel::watchAdForSlot) },
        onGraphics = { onGraphics(viewModel.gameId) },
        onControls = { onControls(viewModel.gameId) },
        onPaywall = onPaywall,
    )
}

@Composable
fun GameDetailScreen(
    state: GameDetailUiState,
    onBack: () -> Unit,
    onPlay: (slot: Int?) -> Unit,
    onFavorite: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    fileLocation: () -> String,
    onWatchAd: () -> Unit,
    onGraphics: () -> Unit,
    onControls: () -> Unit,
    onPaywall: () -> Unit,
) {
    val game = state.game
    if (game == null) {
        if (!state.loading) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.game_not_found), style = RnTheme.type.body, color = RnTheme.colors.inkMuted)
                RnButton(stringResource(R.string.player_back_to_library), onBack, Modifier.padding(top = 16.dp))
            }
        }
        return
    }
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }
    var showLocation by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            GameCover(game.title, game.coverPath, Modifier.fillMaxSize())
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                RnIconButton(R.drawable.ms_arrow_back, stringResource(R.string.cd_back), onBack, style = RnIconButtonStyle.Glass)
                Box(Modifier.weight(1f))
                RnIconButton(
                    if (game.favorite) R.drawable.ms_star_fill else R.drawable.ms_star,
                    stringResource(if (game.favorite) R.string.cd_unfavorite else R.string.cd_favorite),
                    onFavorite,
                    style = RnIconButtonStyle.Glass,
                )
            }
        }
        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(game.displayTitle, style = RnTheme.type.headline.copy(fontSize = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)), color = RnTheme.colors.ink)
                val parts = buildList {
                    add(game.format.uppercase())
                    add(Formats.size(game.sizeBytes))
                    if (game.playSeconds > 0) add(stringResource(R.string.library_played, Formats.playTime(context, game.playSeconds)))
                    add(
                        when {
                            game.lastPlayedAt == null -> stringResource(R.string.game_meta_never)
                            Formats.isToday(game.lastPlayedAt) -> stringResource(R.string.game_meta_last_today)
                            else -> stringResource(R.string.game_meta_last, Formats.ago(game.lastPlayedAt))
                        },
                    )
                }
                Text(parts.joinToString(" · "), style = RnTheme.type.caption.copy(fontWeight = FontWeight.Normal, fontSize = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)), color = RnTheme.colors.inkMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val hasAuto = state.slots.any { it.isAuto }
                RnButton(
                    stringResource(if (hasAuto) R.string.game_resume else R.string.game_play),
                    { onPlay(if (hasAuto) SaveSlotEntity.AUTO_SLOT else null) },
                    Modifier.weight(1f),
                    leadingIcon = R.drawable.ms_play_arrow_fill,
                )
                Box {
                    RnIconButton(R.drawable.ms_more_horiz, stringResource(R.string.cd_more), { menu = true }, style = RnIconButtonStyle.Filled, size = 52.dp)
                    RnMenu(
                        expanded = menu,
                        onDismiss = { menu = false },
                        items = listOf(
                            RnMenuItem(stringResource(R.string.game_menu_rename), R.drawable.ms_edit) { renaming = true },
                            RnMenuItem(stringResource(R.string.game_menu_location), R.drawable.ms_folder_open) { showLocation = true },
                            RnMenuItem(stringResource(R.string.game_menu_remove), R.drawable.ms_delete, destructive = true) { removing = true },
                        ),
                    )
                }
            }
            CompatNote(state)

            RnSectionHeader(
                stringResource(R.string.game_slots_header),
                action = state.quota.limit?.let { stringResource(R.string.game_slots_used, state.quota.used, it) }
                    ?: stringResource(R.string.game_slots_used_unlimited, state.quota.used),
                onAction = null,
            )
            // Header trailing text (non-clickable) — rendered by RnSectionHeader only with onAction; show it here.
            if (state.slots.isEmpty()) {
                Text(stringResource(R.string.game_slot_empty), style = RnTheme.type.body, color = RnTheme.colors.inkMuted)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.slots.forEach { slot ->
                    SlotCard {
                        SaveSlotRow(
                            title = if (slot.isAuto) "Slot A" else stringResource(R.string.game_slot_name, slot.slotIndex),
                            meta = Formats.dateTime(context, slot.savedAt) + (slot.note?.let { " · $it" } ?: ""),
                            seed = game.title + slot.slotIndex,
                            thumbPath = slot.thumbPath,
                            isAuto = slot.isAuto,
                            autoLabel = stringResource(R.string.game_slot_auto),
                            onClick = { onPlay(slot.slotIndex) },
                        )
                    }
                }
                if (state.showRewardedRow) {
                    Surface(
                        onClick = onWatchAd,
                        shape = RoundedCornerShape(RnRadius.lg),
                        color = RnTheme.colors.surface,
                        border = BorderStroke(1.dp, RnTheme.colors.line),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(96.dp, 54.dp).clip(RoundedCornerShape(RnRadius.sm)).background(RnTheme.colors.surfaceSunken), contentAlignment = Alignment.Center) {
                                RnIcon(R.drawable.ms_smart_display, tint = RnTheme.colors.primaryStrong, size = 28.dp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.game_rewarded_title), style = RnTheme.type.subtitle, color = RnTheme.colors.ink)
                                Text(stringResource(R.string.game_rewarded_sub), style = RnTheme.type.caption, color = RnTheme.colors.inkMuted)
                            }
                        }
                    }
                    app.letostudio.gameemulator.ui.components.RnTextLink(stringResource(R.string.settings_upgrade), onPaywall)
                }
            }

            RnCardGroup {
                RnListRow(
                    stringResource(R.string.game_row_graphics),
                    icon = R.drawable.ms_tune,
                    trailingText = stringResource(if (state.graphicsCustom) R.string.game_graphics_custom else R.string.game_graphics_auto),
                    showChevron = true,
                    onClick = onGraphics,
                )
                RnDivider()
                RnListRow(
                    stringResource(R.string.game_row_controls),
                    icon = R.drawable.ms_sports_esports,
                    trailingText = stringResource(R.string.game_controls_default),
                    showChevron = true,
                    onClick = onControls,
                )
            }
        }
    }

    if (renaming) {
        var text by rememberSaveable { mutableStateOf(game.displayTitle) }
        RnContentDialog(
            title = stringResource(R.string.game_rename_title),
            confirm = stringResource(R.string.action_save),
            onConfirm = { onRename(text); renaming = false },
            onDismiss = { renaming = false },
            dismiss = stringResource(R.string.action_cancel),
            confirmEnabled = text.isNotBlank(),
        ) { RnTextField(text, { text = it.take(80) }, stringResource(R.string.game_rename_hint)) }
    }
    if (removing) {
        RnDialog(
            title = stringResource(R.string.game_remove_title),
            text = stringResource(R.string.game_remove_body),
            confirm = stringResource(R.string.game_remove_confirm),
            onConfirm = { removing = false; onRemove() },
            onDismiss = { removing = false },
            dismiss = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
    if (showLocation) {
        RnDialog(
            title = stringResource(R.string.game_location_title),
            text = fileLocation(),
            confirm = stringResource(R.string.action_ok),
            onConfirm = { showLocation = false },
            onDismiss = { showLocation = false },
        )
    }
}

@Composable
private fun SlotCard(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(RnRadius.lg), color = RnTheme.colors.surface, border = BorderStroke(1.dp, RnTheme.colors.line)) { content() }
}

@Composable
private fun CompatNote(state: GameDetailUiState) {
    val c = RnTheme.colors
    val compat = state.game?.compat
    val (icon, tint, title) = when (compat) {
        "great" -> Triple(R.drawable.ms_verified, c.success, stringResource(R.string.game_compat_great_title))
        "tweaks" -> Triple(R.drawable.ms_warning, c.ember, stringResource(R.string.game_compat_tweaks_title))
        "poor" -> Triple(R.drawable.ms_error, c.danger, stringResource(R.string.game_compat_poor_title))
        else -> Triple(R.drawable.ms_bolt, c.primaryStrong, null)
    }
    val e = state.effective
    val body = if (title == null) {
        stringResource(R.string.game_compat_unknown)
    } else {
        stringResource(R.string.game_compat_body, stringResource(R.string.emu_backend_gles), "${e.resolution}×", if (e.frameSkip > 0) "30–60" else "60")
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(RnRadius.lg)).background(c.tint).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RnIcon(icon, tint = tint)
        Text(
            buildAnnotatedString {
                if (title != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
                    append(" ")
                }
                append(body)
            },
            style = RnTheme.type.body,
            color = c.ink,
        )
    }
}
