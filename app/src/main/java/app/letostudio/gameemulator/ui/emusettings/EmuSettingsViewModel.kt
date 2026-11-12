package app.letostudio.gameemulator.ui.emusettings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.AppScreens
import app.letostudio.gameemulator.core.FixLagStep
import app.letostudio.gameemulator.core.SmartSetup
import app.letostudio.gameemulator.data.db.GameDao
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.data.prefs.EmulatorDefaults
import app.letostudio.gameemulator.data.prefs.SettingsStore
import app.letostudio.gameemulator.monetization.NativePlacement
import app.letostudio.gameemulator.monetization.PlacementNativeAds
import app.letostudio.gameemulator.monetization.ProGate
import com.google.android.gms.ads.nativead.NativeAd
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EmuSettingsUiState(
    val game: GameEntity? = null,
    val settings: EmulatorDefaults = EmulatorDefaults(),
    val tier: String? = null,
    val isPro: Boolean = false,
    val nextFixLag: FixLagStep? = FixLagStep.RESOLUTION,
)

sealed interface EmuEvent {
    data class Toast(val step: FixLagStep) : EmuEvent
}

/** S15 (F12/F13). Opened with a gameId → that game's overrides; otherwise global defaults. */
@HiltViewModel
class EmuSettingsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val settingsStore: SettingsStore,
    private val gameDao: GameDao,
    private val analytics: Analytics,
    private val smartSetup: SmartSetup,
    private val nativeAds: PlacementNativeAds,
    proGate: ProGate,
) : ViewModel() {
    private val gameId: Long? = (savedState.get<Long>("gameId") ?: savedState.get<String>("gameId")?.toLongOrNull())?.takeIf { it > 0 }

    val sheetAd: StateFlow<NativeAd?> = nativeAds.ad(NativePlacement.SHEET)

    val state: StateFlow<EmuSettingsUiState> = combine(
        settingsStore.emulatorDefaults,
        gameId?.let { gameDao.observe(it) } ?: flowOf(null),
        settingsStore.deviceTier,
        proGate.state,
    ) { defaults, game, tier, pro ->
        val eff = SmartSetup.effectiveFor(game, defaults)
        EmuSettingsUiState(game, eff, tier, pro.isPro, SmartSetup.nextFixLag(eff))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmuSettingsUiState())

    private val _events = Channel<EmuEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        analytics.screen(AppScreens.EMULATOR_SETTINGS)
        viewModelScope.launch { smartSetup.ensureProfiled() }
    }

    private fun update(key: String, value: Any, transform: (EmulatorDefaults) -> EmulatorDefaults) {
        analytics.log(AppEvents.SETTING_CHANGE, mapOf(AppParams.KEY to key, AppParams.VALUE to value.toString()))
        viewModelScope.launch {
            val id = gameId
            if (id == null) {
                settingsStore.updateEmulatorDefaults(transform)
            } else {
                val g = gameDao.get(id) ?: return@launch
                val next = transform(state.value.settings)
                gameDao.update(g.copy(resolution = next.resolution, frameSkip = next.frameSkip, effectsOn = next.effectsOn, fixLagSteps = next.fixLagSteps))
                // Global-only switches still go to defaults.
                settingsStore.updateEmulatorDefaults { it.copy(smartSetupOn = next.smartSetupOn, showFps = next.showFps, soundOn = next.soundOn, audioLatency = next.audioLatency) }
            }
        }
    }

    fun setSmartSetup(on: Boolean) = update("smart_setup", on) { it.copy(smartSetupOn = on) }
    fun setResolution(r: Int) = update("resolution", r) { it.copy(resolution = r) }
    fun setFrameSkip(f: Int) = update("frame_skip", f) { it.copy(frameSkip = f) }
    fun setEffects(on: Boolean) = update("effects", on) { it.copy(effectsOn = on) }
    fun setShowFps(on: Boolean) = update("show_fps", on) { it.copy(showFps = on) }
    fun setSound(on: Boolean) = update("sound", on) { it.copy(soundOn = on) }
    fun setLatency(l: String) = update("audio_latency", l) { it.copy(audioLatency = l) }

    fun fixLag() {
        val step = SmartSetup.nextFixLag(state.value.settings) ?: return
        analytics.log(AppEvents.FIX_LAG_TAP, mapOf(AppParams.STEP to state.value.settings.fixLagSteps + 1, AppParams.SCOPE to if (gameId != null) "game" else "global"))
        update("fix_lag", step.name.lowercase()) { SmartSetup.applyFixLag(it, step) }
        viewModelScope.launch { _events.send(EmuEvent.Toast(step)) }
    }

    fun onSheetOpen() {
        analytics.log(AppEvents.FIX_LAG_SHEET_OPEN)
        analytics.screen(AppScreens.FIX_LAG_SHEET)
        nativeAds.request(NativePlacement.SHEET)
    }
}
