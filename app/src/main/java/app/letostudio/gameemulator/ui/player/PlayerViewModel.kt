package app.letostudio.gameemulator.ui.player

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.AppScreens
import app.letostudio.gameemulator.analytics.AppUserProps
import app.letostudio.gameemulator.core.CoreAssets
import app.letostudio.gameemulator.core.SmartSetup
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.crash.CrashReporter
import app.letostudio.gameemulator.data.db.GameDao
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.data.db.SaveSlotEntity
import app.letostudio.gameemulator.data.prefs.EmulatorDefaults
import app.letostudio.gameemulator.data.prefs.PadPrefs
import app.letostudio.gameemulator.data.prefs.SettingsStore
import app.letostudio.gameemulator.data.saves.SaveRepository
import app.letostudio.gameemulator.data.saves.SlotQuota
import app.letostudio.gameemulator.input.KeyMapper
import app.letostudio.gameemulator.monetization.AdPlacements
import app.letostudio.gameemulator.monetization.ProGate
import com.swordfish.libretrodroid.GLRetroView
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PlayerPhase { LOADING, RUNNING, PAUSED, ERROR, EXITING }

data class PlayerUiState(
    val phase: PlayerPhase = PlayerPhase.LOADING,
    val game: GameEntity? = null,
    val settings: EmulatorDefaults = EmulatorDefaults(),
    val pad: PadPrefs = PadPrefs(),
    val mapping: Map<Int, Int> = emptyMap(),
    val fps: Int = 0,
    val speed: Int = 1,
    val overlayVisible: Boolean = true,
    val lastAutoSaveAt: Long? = null,
    val slots: List<SaveSlotEntity> = emptyList(),
    val quota: SlotQuota = SlotQuota(0, 3),
    /** Player config is ready to create the GLRetroView. */
    val launch: PlayerLaunch? = null,
)

/** Everything the GLRetroView needs; recreated for the fd-path fallback attempt. */
data class PlayerLaunch(val pfd: ParcelFileDescriptor, val useVirtualFile: Boolean, val attempt: Int)

sealed interface PlayerEvent {
    data class Toast(val res: Int) : PlayerEvent
    data object Exit : PlayerEvent
}

/**
 * S09 Player + S10 Pause (F07–F11). Owns the session: start/fail analytics, Crashlytics keys,
 * auto-save on exit/background, manual slots, fast-forward, screenshots and the exit flow
 * (auto-save → I1 interstitial check → back to Game detail).
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val saves: SaveRepository,
    private val settingsStore: SettingsStore,
    private val keyMapper: KeyMapper,
    private val coreAssets: CoreAssets,
    private val smartSetup: SmartSetup,
    private val proGate: ProGate,
    private val ads: AdPlacements,
    private val analytics: Analytics,
    private val crash: CrashContext,
    private val crashReporter: CrashReporter,
) : ViewModel() {
    val gameId: Long = savedState.get<Long>("gameId") ?: savedState.get<String>("gameId")?.toLongOrNull() ?: -1
    private val startSlot: Int? = (savedState.get<Int>("slot") ?: savedState.get<String>("slot")?.toIntOrNull())?.takeIf { it >= 0 }
    private val source: String = savedState.get<String>("source") ?: if (startSlot == null) "game_detail" else "slot"

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _events = Channel<PlayerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** App-scoped: auto-save writes must outlive the ViewModel on exit/kill. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Session accounting
    private var started = false
    private var activeSince = 0L
    private var activeMs = 0L
    private var frames = 0L
    private var gamepadUsed = false
    private var stoppedAt = 0L
    private var pendingStateToLoad: ByteArray? = null

    init {
        analytics.screen(AppScreens.PLAYER)
        crash.set(CrashContext.Keys.SCREEN, AppScreens.PLAYER)
        viewModelScope.launch { prepare() }
        viewModelScope.launch { settingsStore.padPrefs.collect { p -> _state.update { it.copy(pad = p) } } }
        viewModelScope.launch { keyMapper.mapping.collect { m -> _state.update { it.copy(mapping = m) } } }
        viewModelScope.launch {
            saves.slots(gameId).collect { list ->
                val game = _state.value.game ?: gameDao.get(gameId)
                val pro = proGate.isPro()
                _state.update { s ->
                    s.copy(
                        slots = list,
                        quota = game?.let { saves.quota(it, list, pro) } ?: s.quota,
                        lastAutoSaveAt = list.firstOrNull { it.isAuto }?.savedAt ?: s.lastAutoSaveAt,
                    )
                }
            }
        }
    }

    private suspend fun prepare() {
        val game = gameDao.get(gameId)
        if (game == null) {
            fail("bad_file", null)
            return
        }
        smartSetup.ensureProfiled()
        coreAssets.ensureInstalled()
        val tier = settingsStore.deviceTier.first()
        if (game.compat == null) gameDao.update(game.copy(compat = SmartSetup.compatFor(tier?.let { t -> app.letostudio.gameemulator.core.DeviceTier.entries.firstOrNull { it.wire == t } })))
        val settings = SmartSetup.effectiveFor(game, settingsStore.emulatorDefaults.first())
        crash.set(CrashContext.Keys.GAME_HASH, game.gameHash)
        crash.set(CrashContext.Keys.BACKEND, SmartSetup.BACKEND)
        crash.set(CrashContext.Keys.RESOLUTION, "${settings.resolution}x")
        crash.set(CrashContext.Keys.CORE_VERSION, coreAssets.coreVersion)
        crash.set(CrashContext.Keys.IN_GAME, true)
        if (startSlot != null) {
            saves.slotList(gameId).firstOrNull { it.slotIndex == startSlot }?.let { pendingStateToLoad = saves.read(it, "game_detail") }
        }
        if (!coreAssets.coreInstalled()) {
            fail("core_load", IllegalStateException("core .so missing"))
            return
        }
        val pfd = openFd(game) ?: run {
            fail("bad_file", null)
            return
        }
        crash.breadcrumb("core", "load start")
        ads.preloadInterstitial()
        _state.update { it.copy(game = game, settings = settings, launch = PlayerLaunch(pfd, useVirtualFile = true, attempt = 1)) }
    }

    private fun openFd(game: GameEntity): ParcelFileDescriptor? = try {
        context.contentResolver.openFileDescriptor(Uri.parse(game.uri), "r")
    } catch (e: Exception) {
        crashReporter.recordNonFatal("core_load", e, mapOf("stage" to "open_fd"))
        null
    }

    /** First frame rendered: the core is up. Loads the requested slot, logs game_start. */
    fun onFirstFrame(view: GLRetroView) {
        if (started) return
        started = true
        crash.breadcrumb("core", "load end")
        pendingStateToLoad?.let { bytes ->
            val ok = runCatching { view.unserializeState(bytes) }.getOrDefault(false)
            if (!ok) viewModelScope.launch { _events.send(PlayerEvent.Toast(app.letostudio.gameemulator.R.string.toast_load_failed)) }
            pendingStateToLoad = null
        }
        activeSince = System.currentTimeMillis()
        _state.update { it.copy(phase = PlayerPhase.RUNNING) }
        val g = _state.value.game ?: return
        analytics.log(
            AppEvents.GAME_START,
            mapOf(
                AppParams.GAME_HASH to g.gameHash,
                AppParams.FORMAT to g.format,
                AppParams.SOURCE to source,
                AppParams.BACKEND to SmartSetup.BACKEND,
                AppParams.RESOLUTION to "${_state.value.settings.resolution}x",
            ),
        )
        viewModelScope.launch {
            if (settingsStore.markFirstGameStarted()) {
                analytics.log(AppEvents.FIRST_GAME_START, mapOf(AppParams.SECONDS_SINCE_INSTALL to settingsStore.secondsSinceInstall()))
            }
        }
    }

    fun onFrame() { frames++ }

    fun onFps(fps: Int) = _state.update { it.copy(fps = fps) }

    /** LibretroDroid error codes → retry with the fd path once, then the S09 error dialog. */
    fun onCoreError(code: Int) {
        val launch = _state.value.launch
        if (!started && code == GLRetroView.ERROR_LOAD_GAME && launch != null && launch.useVirtualFile) {
            crash.breadcrumb("core", "virtual file load failed, retry fd path")
            _state.update { it.copy(launch = launch.copy(useVirtualFile = false, attempt = 2)) }
            return
        }
        val reason = when (code) {
            GLRetroView.ERROR_LOAD_LIBRARY -> "core_load"
            GLRetroView.ERROR_LOAD_GAME -> "bad_file"
            GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "unsupported"
            else -> "core_load"
        }
        if (code == GLRetroView.ERROR_GL_NOT_COMPATIBLE) analytics.log(AppEvents.GL_ERROR, mapOf(AppParams.CODE to code))
        fail(reason, RuntimeException("core error $code"))
    }

    private fun fail(reason: String, error: Throwable?) {
        analytics.log(AppEvents.GAME_START_FAIL, mapOf(AppParams.FORMAT to (_state.value.game?.format ?: "unknown"), AppParams.REASON to reason))
        crashReporter.recordNonFatal("core_load", error ?: RuntimeException("game start failed: $reason"), mapOf("reason" to reason))
        crash.set(CrashContext.Keys.IN_GAME, false)
        _state.update { it.copy(phase = PlayerPhase.ERROR) }
    }

    fun onLowMemory() {
        if (!started) fail("oom", OutOfMemoryError("low memory during load"))
    }

    // --- pause / resume ------------------------------------------------------------------

    fun pause() {
        if (_state.value.phase != PlayerPhase.RUNNING) return
        accumulate()
        analytics.screen(AppScreens.PAUSE_MENU)
        _state.update { it.copy(phase = PlayerPhase.PAUSED) }
    }

    fun resume() {
        if (_state.value.phase != PlayerPhase.PAUSED) return
        activeSince = System.currentTimeMillis()
        analytics.screen(AppScreens.PLAYER)
        _state.update { it.copy(phase = PlayerPhase.RUNNING) }
    }

    private fun accumulate() {
        if (activeSince > 0) activeMs += System.currentTimeMillis() - activeSince
        activeSince = 0
    }

    fun setSpeed(speed: Int) {
        _state.update { it.copy(speed = speed) }
        analytics.log(AppEvents.FAST_FORWARD, mapOf(AppParams.SPEED to speed))
    }

    fun onGamepadInput(vendorId: Int, productId: Int) {
        if (_state.value.overlayVisible) _state.update { it.copy(overlayVisible = false) }
        if (!gamepadUsed) {
            gamepadUsed = true
            analytics.log(AppEvents.GAMEPAD_CONNECTED, mapOf(AppParams.VENDOR_ID to vendorId, AppParams.PRODUCT_ID to productId))
            analytics.setUserProperty(AppUserProps.USES_GAMEPAD, "true")
            viewModelScope.launch { settingsStore.setUsesGamepad() }
        }
    }

    fun onScreenTouched() {
        if (!_state.value.overlayVisible) _state.update { it.copy(overlayVisible = true) }
    }

    // --- saves -----------------------------------------------------------------------------

    /** Manual save into the next free slot (or [slot] to overwrite). */
    fun saveManual(state: ByteArray?, thumb: Bitmap?, slot: Int? = null) {
        val g = _state.value.game ?: return
        if (state == null) {
            viewModelScope.launch { _events.send(PlayerEvent.Toast(app.letostudio.gameemulator.R.string.toast_save_failed)) }
            return
        }
        val quota = _state.value.quota
        val target = slot ?: saves.nextManualSlot(_state.value.slots, quota)
        if (target == null) {
            analytics.log(AppEvents.SAVE_SLOT_LIMIT, mapOf(AppParams.GAME_HASH to g.gameHash))
            viewModelScope.launch { _events.send(PlayerEvent.Toast(app.letostudio.gameemulator.R.string.toast_slots_full)) }
            return
        }
        viewModelScope.launch {
            val ok = saves.write(g, target, state, thumb)
            _events.send(PlayerEvent.Toast(if (ok) app.letostudio.gameemulator.R.string.toast_saved else app.letostudio.gameemulator.R.string.toast_save_failed))
        }
    }

    suspend fun readSlot(slot: SaveSlotEntity): ByteArray? = saves.read(slot, "pause")

    /** Auto-save slot A (exit / background). Runs in an app scope so it completes. */
    fun autoSave(state: ByteArray?, thumb: Bitmap?, onDone: (() -> Unit)? = null) {
        val g = _state.value.game
        if (g == null || state == null || !started) {
            onDone?.invoke()
            return
        }
        ioScope.launch {
            withContext(NonCancellable) { saves.write(g, SaveSlotEntity.AUTO_SLOT, state, thumb) }
            withContext(Dispatchers.Main) { onDone?.invoke() }
        }
    }

    fun saveScreenshot(bitmap: Bitmap?) {
        bitmap ?: return
        ioScope.launch {
            val ok = runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "RetroNova_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RetroNova")
                    }
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
                context.contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.isSuccess
            if (ok) analytics.log(AppEvents.SCREENSHOT_TAKE)
            _events.send(PlayerEvent.Toast(if (ok) app.letostudio.gameemulator.R.string.toast_screenshot else app.letostudio.gameemulator.R.string.toast_save_failed))
        }
    }

    // --- lifecycle ---------------------------------------------------------------------------

    fun onStop() {
        accumulate()
        stoppedAt = System.currentTimeMillis()
    }

    fun onStart() {
        if (stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > BACKGROUND_SESSION_END_MS && started) {
            logSessionEnd("background")
            activeMs = 0
            frames = 0
        }
        stoppedAt = 0
        if (_state.value.phase == PlayerPhase.RUNNING) activeSince = System.currentTimeMillis()
    }

    /** Exit game: called after the auto-save finished. */
    fun onExitSaved(exit: String) {
        accumulate()
        if (started) logSessionEnd(exit)
        crash.set(CrashContext.Keys.IN_GAME, false)
        val g = _state.value.game
        if (g != null && started) {
            viewModelScope.launch {
                gameDao.addPlay(g.id, System.currentTimeMillis(), activeMs / 1000)
                _events.send(PlayerEvent.Exit)
            }
        } else {
            viewModelScope.launch { _events.send(PlayerEvent.Exit) }
        }
        _state.update { it.copy(phase = PlayerPhase.EXITING) }
    }

    private fun logSessionEnd(exit: String) {
        val g = _state.value.game ?: return
        val seconds = activeMs / 1000
        analytics.log(
            AppEvents.GAME_SESSION_END,
            mapOf(
                AppParams.GAME_HASH to g.gameHash,
                AppParams.DURATION_SEC to seconds,
                AppParams.FPS_AVG to if (seconds > 0) (frames / seconds).toInt().coerceAtMost(120) else 0,
                AppParams.FIX_LAG_STEPS to _state.value.settings.fixLagSteps,
                AppParams.GAMEPAD to gamepadUsed,
                AppParams.EXIT to exit,
            ),
        )
    }

    suspend fun exitAds(activity: android.app.Activity) = ads.onGameExited(activity)

    fun coreData(game: GameEntity, launch: PlayerLaunch) =
        coreAssets.retroViewData(game, launch.pfd, _state.value.settings, launch.useVirtualFile)

    // --- in-game settings from the pause menu (per-game overrides) ------------------------

    fun setPadOpacity(v: Float) {
        analytics.log(AppEvents.SETTING_CHANGE, mapOf(AppParams.KEY to "pad_opacity", AppParams.VALUE to (v * 100).toInt().toString()))
        viewModelScope.launch { settingsStore.setPadOpacity(v) }
    }

    fun setPadHaptics(on: Boolean) {
        analytics.log(AppEvents.SETTING_CHANGE, mapOf(AppParams.KEY to "pad_haptics", AppParams.VALUE to on.toString()))
        viewModelScope.launch { settingsStore.setPadHaptics(on) }
    }

    fun setShowFps(on: Boolean) {
        analytics.log(AppEvents.SETTING_CHANGE, mapOf(AppParams.KEY to "show_fps", AppParams.VALUE to on.toString()))
        _state.update { it.copy(settings = it.settings.copy(showFps = on)) }
        viewModelScope.launch { settingsStore.updateEmulatorDefaults { it.copy(showFps = on) } }
    }

    private fun applyGameSettings(next: EmulatorDefaults, apply: (Array<com.swordfish.libretrodroid.Variable>) -> Unit) {
        _state.update { it.copy(settings = next) }
        crash.set(CrashContext.Keys.RESOLUTION, "${next.resolution}x")
        apply(SmartSetup.coreVariables(next).map { (k, v) -> com.swordfish.libretrodroid.Variable(k, v) }.toTypedArray())
        viewModelScope.launch {
            gameDao.get(gameId)?.let { gameDao.update(it.copy(resolution = next.resolution, frameSkip = next.frameSkip, effectsOn = next.effectsOn, fixLagSteps = next.fixLagSteps)) }
        }
    }

    fun setResolution(r: Int, apply: (Array<com.swordfish.libretrodroid.Variable>) -> Unit) {
        analytics.log(AppEvents.SETTING_CHANGE, mapOf(AppParams.KEY to "resolution", AppParams.VALUE to r.toString()))
        applyGameSettings(_state.value.settings.copy(resolution = r), apply)
    }

    fun fixLag(apply: (Array<com.swordfish.libretrodroid.Variable>) -> Unit) {
        val s = _state.value.settings
        val step = SmartSetup.nextFixLag(s) ?: return
        analytics.log(AppEvents.FIX_LAG_TAP, mapOf(AppParams.STEP to s.fixLagSteps + 1, AppParams.SCOPE to "game"))
        applyGameSettings(SmartSetup.applyFixLag(s, step), apply)
        viewModelScope.launch {
            _events.send(
                PlayerEvent.Toast(
                    when (step) {
                        app.letostudio.gameemulator.core.FixLagStep.RESOLUTION -> app.letostudio.gameemulator.R.string.emu_toast_res
                        app.letostudio.gameemulator.core.FixLagStep.FRAME_SKIP -> app.letostudio.gameemulator.R.string.emu_toast_frameskip
                        app.letostudio.gameemulator.core.FixLagStep.EFFECTS -> app.letostudio.gameemulator.R.string.emu_toast_effects
                    },
                ),
            )
        }
    }

    override fun onCleared() {
        runCatching { _state.value.launch?.pfd?.close() }
        super.onCleared()
    }

    private companion object { const val BACKGROUND_SESSION_END_MS = 5 * 60_000L }
}
