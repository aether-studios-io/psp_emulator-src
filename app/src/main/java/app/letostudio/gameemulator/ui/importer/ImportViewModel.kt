package app.letostudio.gameemulator.ui.importer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.data.library.LibraryRepository
import app.letostudio.gameemulator.data.library.ScanState
import app.letostudio.gameemulator.data.library.ScanTrigger
import app.letostudio.gameemulator.monetization.NativePlacement
import app.letostudio.gameemulator.monetization.PlacementNativeAds
import com.google.android.gms.ads.nativead.NativeAd
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportUiState(
    val folderName: String? = null,
    val scan: ScanState = ScanState.Idle,
)

sealed interface ImportEvent {
    data class FileAdded(val title: String) : ImportEvent
    data object FileFailed : ImportEvent
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val analytics: Analytics,
    private val crash: CrashContext,
    private val nativeAds: PlacementNativeAds,
) : ViewModel() {

    val ad: StateFlow<NativeAd?> = nativeAds.ad(NativePlacement.IMPORT)

    val state: StateFlow<ImportUiState> = combine(library.folderUri, library.scanState) { folder, scan ->
        ImportUiState(library.folderDisplayName(folder), scan)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ImportUiState())

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // A finished result from an earlier visit is not this visit's result.
        if (library.scanState.value is ScanState.Done || library.scanState.value is ScanState.Failed) library.resetScanState()
        viewModelScope.launch {
            library.scanState.collect { if (it is ScanState.Done) nativeAds.request(NativePlacement.IMPORT) }
        }
    }

    fun onFolderPicked(uri: Uri?) {
        if (uri == null) {
            analytics.log(AppEvents.PERMISSION_DENIED, mapOf(AppParams.KIND to "folder"))
            crash.breadcrumb("saf", "folder picker cancelled")
            return
        }
        viewModelScope.launch { library.onFolderPicked(uri) }
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) {
            analytics.log(AppEvents.PERMISSION_DENIED, mapOf(AppParams.KIND to "file"))
            return
        }
        viewModelScope.launch {
            library.addSingleFile(uri)
                .onSuccess { _events.send(ImportEvent.FileAdded(it.displayTitle)) }
                .onFailure { _events.send(ImportEvent.FileFailed) }
        }
    }

    fun onRescan() {
        viewModelScope.launch { library.scan(ScanTrigger.MANUAL) }
    }
}
