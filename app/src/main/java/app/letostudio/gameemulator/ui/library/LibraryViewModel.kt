package app.letostudio.gameemulator.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.data.library.LibraryRepository
import app.letostudio.gameemulator.data.library.ScanState
import app.letostudio.gameemulator.data.library.ScanTrigger
import app.letostudio.gameemulator.monetization.NativePlacement
import app.letostudio.gameemulator.monetization.PlacementNativeAds
import app.letostudio.gameemulator.monetization.ProGate
import com.google.android.gms.ads.nativead.NativeAd
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter(val wire: String) { ALL("all"), RECENT("recent"), FAVORITES("favorites"), HOMEBREW("homebrew") }

data class LibraryUiState(
    val loading: Boolean = true,
    val totalGames: Int = 0,
    val games: List<GameEntity> = emptyList(),
    val continueGame: GameEntity? = null,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val query: String = "",
    val searchOpen: Boolean = false,
    val showLibraryAd: Boolean = false,
    val refreshing: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val analytics: Analytics,
    private val nativeAds: PlacementNativeAds,
    proGate: ProGate,
) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter.ALL)
    private val query = MutableStateFlow("")
    private val searchOpen = MutableStateFlow(false)

    val libraryAd: StateFlow<NativeAd?> = nativeAds.ad(NativePlacement.LIBRARY)

    private val controls = combine(filter, query, searchOpen) { f, q, s -> Triple(f, q, s) }

    val state: StateFlow<LibraryUiState> = combine(
        library.games,
        controls,
        proGate.state,
        library.scanState,
    ) { games, (f, q, s), pro, scan ->
        val visible = filterGames(games, f, q)
        LibraryUiState(
            loading = false,
            totalGames = games.size,
            games = visible,
            continueGame = games.filter { it.lastPlayedAt != null }.maxByOrNull { it.lastPlayedAt!! },
            filter = f,
            query = q,
            searchOpen = s,
            showLibraryAd = !pro.isPro && games.size >= 4,
            refreshing = scan is ScanState.Scanning,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        // F02: rescan on app start (once per process).
        viewModelScope.launch {
            if (!startScanDone) {
                startScanDone = true
                library.rescanOnStart()
            }
        }
        // library_search: debounced 1 s, result count only — never the query text.
        viewModelScope.launch {
            query.drop(1).debounce(1_000).filter { it.isNotBlank() }.distinctUntilChanged().collect {
                analytics.log(AppEvents.LIBRARY_SEARCH, mapOf(AppParams.RESULTS to state.value.games.size))
            }
        }
        viewModelScope.launch {
            state.map { it.showLibraryAd }.distinctUntilChanged().collect { if (it) nativeAds.request(NativePlacement.LIBRARY) }
        }
    }

    fun onFilter(f: LibraryFilter) {
        if (f == filter.value) return
        filter.value = f
        analytics.log(AppEvents.LIBRARY_FILTER, mapOf(AppParams.FILTER to f.wire))
    }

    fun onQuery(q: String) {
        query.value = q.take(80)
    }

    fun onSearchOpen(open: Boolean) {
        searchOpen.value = open
        if (!open) query.value = ""
    }

    fun onRefresh() {
        viewModelScope.launch { library.scan(ScanTrigger.PULL_REFRESH) }
    }

    companion object {
        @Volatile private var startScanDone = false

        fun filterGames(games: List<GameEntity>, f: LibraryFilter, q: String): List<GameEntity> {
            val base = when (f) {
                LibraryFilter.ALL -> games
                LibraryFilter.RECENT -> games.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }
                LibraryFilter.FAVORITES -> games.filter { it.favorite }
                LibraryFilter.HOMEBREW -> games.filter { it.isHomebrew }
            }
            val needle = q.trim()
            return if (needle.isEmpty()) base else base.filter { it.displayTitle.contains(needle, ignoreCase = true) }
        }
    }
}
