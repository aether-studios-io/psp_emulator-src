package app.letostudio.gameemulator.ui.importer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.letostudio.gameemulator.R
import app.letostudio.gameemulator.data.library.ScanItem
import app.letostudio.gameemulator.data.library.ScanState
import app.letostudio.gameemulator.ui.components.RnButton
import app.letostudio.gameemulator.ui.components.RnButtonStyle
import app.letostudio.gameemulator.ui.components.RnCardGroup
import app.letostudio.gameemulator.ui.components.RnDivider
import app.letostudio.gameemulator.ui.components.RnIcon
import app.letostudio.gameemulator.ui.components.RnIconTile
import app.letostudio.gameemulator.ui.components.RnNativeAd
import app.letostudio.gameemulator.ui.components.RnNote
import app.letostudio.gameemulator.ui.components.RnOverline
import app.letostudio.gameemulator.ui.components.RnTextLink
import app.letostudio.gameemulator.ui.components.RnTopBar
import app.letostudio.gameemulator.ui.theme.RnTheme
import app.letostudio.gameemulator.util.Formats
import com.google.android.gms.ads.nativead.NativeAd

@Composable
fun ImportRoute(onBack: () -> Unit, onDone: () -> Unit, snackbar: SnackbarHostState, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ad by viewModel.ad.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree(), viewModel::onFolderPicked)
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::onFilePicked)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is ImportEvent.FileAdded -> snackbar.showSnackbar(res.getString(R.string.import_file_added, e.title))
                ImportEvent.FileFailed -> snackbar.showSnackbar(res.getString(R.string.import_file_failed))
            }
        }
    }
    ImportScreen(
        state = state,
        ad = ad,
        onBack = onBack,
        onChooseFolder = { folderPicker.launch(null) },
        onAddFile = { filePicker.launch(arrayOf("application/octet-stream", "application/x-iso9660-image", "*/*")) },
        onDone = onDone,
    )
}

@Composable
fun ImportScreen(
    state: ImportUiState,
    ad: NativeAd?,
    onBack: () -> Unit,
    onChooseFolder: () -> Unit,
    onAddFile: () -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        RnTopBar(stringResource(R.string.import_title), large = false, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.folderName == null) {
                FirstTime(onChooseFolder, onAddFile)
                return@Column
            }
            FolderCard(state.folderName, onChooseFolder)
            when (val scan = state.scan) {
                is ScanState.Scanning -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = RnTheme.colors.primary, strokeWidth = 3.dp)
                    Text(stringResource(R.string.import_scanning, scan.checked), style = RnTheme.type.subtitle, color = RnTheme.colors.ink)
                }
                is ScanState.Done -> {
                    ResultHeader(scan.result.added, scan.result.totalFiles, scan.result.skipped)
                    if (scan.result.items.isNotEmpty()) {
                        RnCardGroup {
                            scan.result.items.forEachIndexed { i, item ->
                                if (i > 0) RnDivider()
                                FileRow(item)
                            }
                        }
                    }
                }
                is ScanState.Failed -> RnNote(
                    stringResource(if (scan.reason == "permission_lost") R.string.import_fail_permission else R.string.import_fail_io),
                    icon = R.drawable.ms_error,
                )
                ScanState.Idle -> Unit
            }
            RnNote(stringResource(R.string.import_note))
            RnButton(stringResource(R.string.import_done), onDone, Modifier.fillMaxWidth(), enabled = state.scan !is ScanState.Scanning)
            RnButton(stringResource(R.string.import_add_file), onAddFile, Modifier.fillMaxWidth(), style = RnButtonStyle.Ghost, leadingIcon = R.drawable.ms_upload_file)
            // N2 Native Small under Done, after the scan finished.
            if (state.scan is ScanState.Done) RnNativeAd(ad, medium = false)
        }
    }
}

@Composable
private fun FirstTime(onChooseFolder: () -> Unit, onAddFile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(24.dp))
        RnIconTile(R.drawable.ms_create_new_folder, size = 72.dp, iconSize = 36.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            stringResource(R.string.import_first_hint),
            style = RnTheme.type.body,
            color = RnTheme.colors.inkMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RnButton(stringResource(R.string.import_choose_folder), onChooseFolder, Modifier.fillMaxWidth(), leadingIcon = R.drawable.ms_folder_open)
        RnButton(stringResource(R.string.import_add_file), onAddFile, Modifier.fillMaxWidth(), style = RnButtonStyle.Secondary, leadingIcon = R.drawable.ms_upload_file)
        RnNote(stringResource(R.string.import_note))
    }
}

@Composable
private fun FolderCard(name: String, onChange: () -> Unit) {
    RnCardGroup {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RnIconTile(R.drawable.ms_folder_open, size = 44.dp, iconSize = 24.dp)
            Column(Modifier.weight(1f)) {
                RnOverline(stringResource(R.string.import_folder_label))
                Text(name, style = RnTheme.type.subtitle, color = RnTheme.colors.ink)
            }
            RnTextLink(stringResource(R.string.import_change), onChange, color = RnTheme.colors.primaryStrong)
        }
    }
}

@Composable
private fun ResultHeader(added: Int, total: Int, skipped: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(RnTheme.colors.tint), contentAlignment = Alignment.Center) {
            RnIcon(R.drawable.ms_check_circle_fill, tint = RnTheme.colors.success, size = 28.dp)
        }
        Column {
            Text(
                if (added == 1) stringResource(R.string.import_added_one) else stringResource(R.string.import_added, added),
                style = RnTheme.type.headline,
                color = RnTheme.colors.ink,
            )
            Text(
                stringResource(if (skipped == 1) R.string.import_scanned else R.string.import_scanned_many, total, skipped),
                style = RnTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = RnTheme.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun FileRow(item: ScanItem) {
    val c = RnTheme.colors
    val icon = when (item.ext) {
        "iso", "cso" -> R.drawable.ms_album
        "pbp" -> R.drawable.ms_folder_zip
        else -> R.drawable.ms_draft
    }
    val sub = when {
        item.added -> stringResource(R.string.import_file_meta, item.ext.uppercase(), Formats.size(item.sizeBytes))
        item.reason == "unreadable" -> stringResource(R.string.import_unreadable, item.ext.uppercase())
        else -> stringResource(R.string.import_not_game, item.ext.uppercase())
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RnIconTile(icon, container = c.surfaceSunken, tint = c.inkMuted)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = RnTheme.type.subtitle, color = c.ink, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(sub, style = RnTheme.type.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = c.inkMuted)
        }
        if (item.added) {
            RnIcon(R.drawable.ms_check_circle, tint = c.success)
        } else {
            RnIcon(R.drawable.ms_error, tint = c.danger)
        }
    }
}

@Suppress("unused")
private fun Uri.short(): String = lastPathSegment.orEmpty()
