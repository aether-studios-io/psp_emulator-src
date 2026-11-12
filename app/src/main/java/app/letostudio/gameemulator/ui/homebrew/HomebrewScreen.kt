package app.letostudio.gameemulator.ui.homebrew

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.ui.components.RnCardGroup
import app.letostudio.gameemulator.ui.components.RnDivider
import app.letostudio.gameemulator.ui.components.RnHeroCard
import app.letostudio.gameemulator.ui.components.RnListRow
import app.letostudio.gameemulator.ui.components.RnNativeAd
import app.letostudio.gameemulator.ui.components.RnNote
import app.letostudio.gameemulator.ui.components.RnTopBar
import app.letostudio.gameemulator.ui.theme.RnTheme

@Composable
fun HomebrewRoute(onGuide: () -> Unit, onGameAdded: (Long) -> Unit, snackbar: SnackbarHostState, viewModel: HomebrewViewModel = hiltViewModel()) {
    val ad by viewModel.ad.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::onFilePicked)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is HomebrewEvent.Added -> {
                    snackbar.showSnackbar(res.getString(R.string.import_file_added, e.title))
                    onGameAdded(e.gameId)
                }
                HomebrewEvent.Failed -> snackbar.showSnackbar(res.getString(R.string.import_file_failed))
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        RnTopBar(stringResource(R.string.homebrew_title))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RnHeroCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.homebrew_eyebrow).uppercase(), style = RnTheme.type.overline, color = Color(0xFFBFD8FF))
                    Text(stringResource(R.string.homebrew_hero_title), style = RnTheme.type.headline, color = Color.White)
                    Text(stringResource(R.string.homebrew_hero_body), style = RnTheme.type.body, color = Color.White.copy(alpha = 0.82f))
                }
            }
            RnCardGroup {
                RnListRow(
                    stringResource(R.string.homebrew_backup_title),
                    subtitle = stringResource(R.string.homebrew_backup_sub),
                    icon = R.drawable.ms_menu_book,
                    showChevron = true,
                    onClick = onGuide,
                )
                RnDivider()
                RnListRow(
                    stringResource(R.string.homebrew_add_title),
                    subtitle = stringResource(R.string.homebrew_add_sub),
                    icon = R.drawable.ms_download,
                    showChevron = true,
                    onClick = { picker.launch(arrayOf("*/*")) },
                )
            }
            RnNote(stringResource(R.string.homebrew_soon))
            // N3 Native Small at the end of the list.
            RnNativeAd(ad, medium = false)
        }
    }
}

/** In-app guide "Back up games you own" (S08 row). */
@Composable
fun HomebrewGuideRoute(onBack: () -> Unit, viewModel: HomebrewViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.onGuideOpened() }
    Column(Modifier.fillMaxSize()) {
        RnTopBar(stringResource(R.string.guide_title), large = false, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.guide_intro), style = RnTheme.type.body, color = RnTheme.colors.inkMuted)
            listOf(
                R.string.guide_step1_title to R.string.guide_step1_body,
                R.string.guide_step2_title to R.string.guide_step2_body,
                R.string.guide_step3_title to R.string.guide_step3_body,
                R.string.guide_step4_title to R.string.guide_step4_body,
            ).forEach { (t, b) ->
                RnCardGroup {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(t), style = RnTheme.type.subtitle, color = RnTheme.colors.ink)
                        Text(stringResource(b), style = RnTheme.type.body, color = RnTheme.colors.inkMuted)
                    }
                }
            }
        }
    }
}
