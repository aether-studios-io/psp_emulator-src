package app.letostudio.gameemulator.ui.game

import android.app.Activity
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.core.SmartSetup
import app.letostudio.gameemulator.data.db.GameDao
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.data.db.SaveSlotEntity
import app.letostudio.gameemulator.data.library.LibraryRepository
import app.letostudio.gameemulator.data.prefs.EmulatorDefaults
import app.letostudio.gameemulator.data.prefs.SettingsStore
import app.letostudio.gameemulator.data.saves.SaveRepository
import app.letostudio.gameemulator.data.saves.SlotQuota
import app.letostudio.gameemulator.monetization.AdPlacements
import app.letostudio.gameemulator.monetization.ProGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameDetailUiState(
    val loading: Boolean = true,
    val game: GameEntity? = null,
    val slots: List<SaveSlotEntity> = emptyList(),
    val quota: SlotQuota = SlotQuota(0, 3),
    val isPro: Boolean = false,
    val showRewardedRow: Boolean = false,
    val graphicsCustom: Boolean = false,
    val effective: EmulatorDefaults = EmulatorDefaults(),
    val tierKnown: Boolean = false,
)

sealed interface GameDetailEvent {
    data object Removed : GameDetailEvent
    data object RewardGranted : GameDetailEvent
    data object RewardUnavailable : GameDetailEvent
}

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val library: LibraryRepository,
    private val gameDao: GameDao,
    saves: SaveRepository,
    settingsStore: SettingsStore,
    proGate: ProGate,
    private val ads: AdPlacements,
) : ViewModel() {
    val gameId: Long = savedState.get<Long>("gameId") ?: savedState.get<String>("gameId")?.toLongOrNull() ?: -1

    private val _events = Channel<GameDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<GameDetailUiState> = combine(
        library.game(gameId),
        saves.slots(gameId),
        proGate.state,
        settingsStore.emulatorDefaults,
        settingsStore.deviceTier,
    ) { game, slots, pro, defaults, tier ->
        if (game == null) return@combine GameDetailUiState(loading = false)
        val quota = saves.quota(game, slots, pro.isPro)
        GameDetailUiState(
            loading = false,
            game = game,
            slots = slots,
            quota = quota,
            isPro = pro.isPro,
            showRewardedRow = !pro.isPro && quota.isFull,
            graphicsCustom = game.resolution != null || game.frameSkip != null || game.effectsOn != null,
            effective = SmartSetup.effectiveFor(game, defaults),
            tierKnown = tier != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameDetailUiState())

    init {
        viewModelScope.launch {
            state.collect { if (it.showRewardedRow) ads.preloadRewarded() }
        }
    }

    fun toggleFavorite() {
        val g = state.value.game ?: return
        viewModelScope.launch { library.setFavorite(g.id, !g.favorite) }
    }

    fun rename(title: String) {
        viewModelScope.launch { library.rename(gameId, title) }
    }

    fun remove() {
        viewModelScope.launch {
            library.remove(gameId)
            _events.send(GameDetailEvent.Removed)
        }
    }

    /** Readable location for "Show file location" — UI only, never logged. */
    fun fileLocation(): String {
        val g = state.value.game ?: return ""
        val uri = Uri.parse(g.uri)
        val doc = uri.lastPathSegment.orEmpty()
        return doc.substringAfter(':', doc).ifBlank { g.fileName }.let { path ->
            val vol = doc.substringBefore(':').let { if (it.equals("primary", true)) "Internal storage" else it }
            "$vol/$path"
        }
    }

    /** R1: explicit tap on "Watch an ad for 1 extra slot" → +1 slot for 24 h. */
    fun watchAdForSlot(activity: Activity) {
        viewModelScope.launch {
            val earned = ads.showRewarded(activity, "save_slot")
            if (earned) {
                gameDao.get(gameId)?.let { gameDao.update(it.copy(extraSlotUntil = System.currentTimeMillis() + 24 * 3600_000L)) }
                _events.send(GameDetailEvent.RewardGranted)
            } else if (!ads.isRewardedReady()) {
                _events.send(GameDetailEvent.RewardUnavailable)
            }
        }
    }
}
