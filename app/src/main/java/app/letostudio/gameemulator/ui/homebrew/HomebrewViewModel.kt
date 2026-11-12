package app.letostudio.gameemulator.ui.homebrew

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.AppScreens
import app.letostudio.gameemulator.data.library.LibraryRepository
import app.letostudio.gameemulator.monetization.NativePlacement
import app.letostudio.gameemulator.monetization.PlacementNativeAds
import com.google.android.gms.ads.nativead.NativeAd
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface HomebrewEvent {
    data class Added(val gameId: Long, val title: String) : HomebrewEvent
    data object Failed : HomebrewEvent
}

/** S08 (MVP: guide + add-file rows; curated list arrives in v1). */
@HiltViewModel
class HomebrewViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val analytics: Analytics,
    nativeAds: PlacementNativeAds,
) : ViewModel() {
    val ad: StateFlow<NativeAd?> = nativeAds.ad(NativePlacement.HOMEBREW)
    private val _events = Channel<HomebrewEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init { nativeAds.request(NativePlacement.HOMEBREW) }

    fun onGuideOpened() {
        analytics.log(AppEvents.HOMEBREW_GUIDE_OPEN)
        analytics.screen(AppScreens.HOMEBREW_GUIDE)
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) {
            analytics.log(AppEvents.PERMISSION_DENIED, mapOf(AppParams.KIND to "file"))
            return
        }
        viewModelScope.launch {
            library.addSingleFile(uri)
                .onSuccess { _events.send(HomebrewEvent.Added(it.id, it.displayTitle)) }
                .onFailure { _events.send(HomebrewEvent.Failed) }
        }
    }
}
