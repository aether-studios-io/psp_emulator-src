package app.letostudio.gameemulator.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.ui.components.ContinueCard
import app.letostudio.gameemulator.ui.components.GameArt
import app.letostudio.gameemulator.ui.components.GameCard
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnButtonStyle
import app.letostudio.gameemulator.ui.components.RnChip
import app.letostudio.gameemulator.ui.components.RnChipRow
import app.letostudio.gameemulator.ui.components.RnIconButton
import app.letostudio.gameemulator.ui.components.RnIconButtonStyle
import app.letostudio.gameemulator.ui.components.RnNativeAd
import app.letostudio.gameemulator.ui.components.RnSearchField
import app.letostudio.gameemulator.ui.components.RnSectionHeader
import app.letostudio.gameemulator.ui.components.RnTopBar
import app.letostudio.gameemulator.ui.theme.RnTheme
import app.letostudio.gameemulator.util.Formats
import com.google.android.gms.ads.nativead.NativeAd

@Composable
fun LibraryRoute(
    onOpenGame: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onAddGames: () -> Unit,
    onOpenHomebrew: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ad by viewModel.libraryAd.collectAsStateWithLifecycle()
    LibraryScreen(
        state = state,
        ad = ad,
        onFilter = viewModel::onFilter,
        onQuery = viewModel::onQuery,
        onSearchOpen = viewModel::onSearchOpen,
        onRefresh = viewModel::onRefresh,
        onOpenGame = onOpenGame,
        onPlay = onPlay,
        onAddGames = onAddGames,
        onOpenHomebrew = onOpenHomebrew,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    ad: NativeAd?,
    onFilter: (LibraryFilter) -> Unit,
    onQuery: (String) -> Unit,
    onSearchOpen: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenGame: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onAddGames: () -> Unit,
    onOpenHomebrew: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        if (state.searchOpen) {
            Box(Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)) {
                Column {
                    RnTopBar(stringResource(R.string.library_title), modifier = Modifier.padding(start = 0.dp))
                    RnSearchField(
                        value = state.query,
                        onValueChange = onQuery,
                        placeholder = stringResource(R.string.library_search_hint),
                        onClose = { onSearchOpen(false) },
                        closeDescription = stringResource(R.string.cd_close),
                    )
                }
            }
        } else {
            RnTopBar(stringResource(R.string.library_title)) {
                if (state.totalGames > 0) {
                    RnIconButton(R.drawable.ms_search, stringResource(R.string.cd_search), { onSearchOpen(true) })
                }
                RnIconButton(R.drawable.ms_add, stringResource(R.string.cd_add_games), onAddGames, style = RnIconButtonStyle.Filled)
            }
        }

        if (!state.loading && state.totalGames == 0) {
            EmptyLibrary(onAddGames, onOpenHomebrew)
            return@Column
        }

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val cont = state.continueGame
                if (cont != null && state.query.isBlank()) {
                    item(span = { GridItemSpan(2) }, key = "continue") {
                        val meta = if (cont.playSeconds > 0) {
                            stringResource(
                                R.string.library_continue_meta,
                                Formats.ago(cont.lastPlayedAt ?: 0),
                                Formats.playTime(context, cont.playSeconds),
                            )
                        } else {
                            stringResource(R.string.library_continue_meta_new, Formats.ago(cont.lastPlayedAt ?: 0))
                        }
                        ContinueCard(
                            eyebrow = stringResource(R.string.library_continue),
                            title = cont.displayTitle,
                            meta = meta,
                            progress = continueProgress(cont.playSeconds),
                            seed = cont.title,
                            coverPath = cont.coverPath,
                            playDescription = stringResource(R.string.cd_resume_game, cont.displayTitle),
                            onPlay = { onPlay(cont.id) },
                            onOpen = { onOpenGame(cont.id) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                item(span = { GridItemSpan(2) }, key = "chips") {
                    RnChipRow {
                        FilterChipItem(LibraryFilter.ALL, R.string.filter_all, R.drawable.ms_grid_view, state.filter, onFilter)
                        FilterChipItem(LibraryFilter.RECENT, R.string.filter_recent, R.drawable.ms_history, state.filter, onFilter)
                        FilterChipItem(LibraryFilter.FAVORITES, R.string.filter_favorites, R.drawable.ms_star, state.filter, onFilter)
                        FilterChipItem(LibraryFilter.HOMEBREW, R.string.filter_homebrew, R.drawable.ms_code, state.filter, onFilter)
                    }
                }
                item(span = { GridItemSpan(2) }, key = "header") {
                    val title = when (state.filter) {
                        LibraryFilter.ALL -> stringResource(R.string.library_section_all, state.games.size)
                        LibraryFilter.RECENT -> stringResource(R.string.library_section_recent, state.games.size)
                        LibraryFilter.FAVORITES -> stringResource(R.string.library_section_favorites, state.games.size)
                        LibraryFilter.HOMEBREW -> stringResource(R.string.library_section_homebrew, state.games.size)
                    }
                    RnSectionHeader(title, action = stringResource(R.string.library_add_games), onAction = onAddGames)
                }
                if (state.games.isEmpty()) {
                    item(span = { GridItemSpan(2) }, key = "none") {
                        Text(
                            if (state.query.isNotBlank()) {
                                stringResource(R.string.library_no_results, state.query)
                            } else {
                                stringResource(R.string.library_filter_empty)
                            },
                            style = RnTheme.type.body,
                            color = RnTheme.colors.inkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                }
                state.games.forEachIndexed { index, game ->
                    item(key = game.id) {
                        GameCard(
                            title = game.displayTitle,
                            meta = gameMeta(game),
                            format = game.format,
                            seed = game.title,
                            coverPath = game.coverPath,
                            onClick = { onOpenGame(game.id) },
                        )
                    }
                    // N1: after the 4th card (row 2), spanning both columns; Free only, >= 4 games.
                    if (index == 3 && state.showLibraryAd && ad != null) {
                        item(span = { GridItemSpan(2) }, key = "ad_n1") {
                            RnNativeAd(ad, medium = true, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(f: LibraryFilter, label: Int, icon: Int, selected: LibraryFilter, onFilter: (LibraryFilter) -> Unit) {
    RnChip(stringResource(label), selected == f, { onFilter(f) }, icon = icon)
}

@Composable
private fun gameMeta(game: GameEntity): String {
    val context = LocalContext.current
    return when {
        game.isHomebrew && game.playSeconds > 0 ->
            stringResource(R.string.library_homebrew_meta, Formats.playTime(context, game.playSeconds))
        game.playSeconds > 0 -> stringResource(R.string.library_played, Formats.playTime(context, game.playSeconds))
        else -> stringResource(R.string.library_never_played)
    }
}

/** Continue-card bar: play time against a 20 h horizon (no real game progress is knowable). */
private fun continueProgress(seconds: Long): Float = (seconds / (20f * 3600f)).coerceIn(0.04f, 1f)

@Composable
private fun EmptyLibrary(onAddGames: () -> Unit, onOpenHomebrew: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(160.dp).padding(8.dp)) {
            GameArt("RetroNova", Modifier.fillMaxSize().then(Modifier))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.library_empty_title), style = RnTheme.type.headline, color = RnTheme.colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.library_empty_body),
            style = RnTheme.type.body,
            color = RnTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(24.dp))
        RnButton(stringResource(R.string.library_add_games), onAddGames, leadingIcon = R.drawable.ms_add)
        Spacer(Modifier.height(8.dp))
        RnButton(stringResource(R.string.library_empty_homebrew), onOpenHomebrew, style = RnButtonStyle.Ghost)
    }
}
