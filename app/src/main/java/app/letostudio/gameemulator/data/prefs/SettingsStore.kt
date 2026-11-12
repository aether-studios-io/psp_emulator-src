package app.letostudio.gameemulator.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.rnDataStore: DataStore<Preferences> by preferencesDataStore(name = "retronova")

/** Global emulator defaults (S15). Per-game overrides live in Room ([GameEntity]). */
data class EmulatorDefaults(
    val smartSetupOn: Boolean = true,
    /** Internal resolution multiplier: 1 or 2 (3/4 are Pro upscaling, v2). */
    val resolution: Int = 2,
    /** 0 = off, 1 = auto (PPSSPP frame skipping). */
    val frameSkip: Int = 0,
    val effectsOn: Boolean = true,
    val showFps: Boolean = false,
    val soundOn: Boolean = true,
    /** low | normal | high */
    val audioLatency: String = "normal",
    val fixLagSteps: Int = 0,
)

data class PadPrefs(
    val opacity: Float = 0.9f,
    val haptics: Boolean = true,
)

/**
 * RetroNova preferences (tech_stacks.md › data/prefs). Theme and the onboarding flag stay in
 * the template's SettingsRepository; everything app-specific lives here.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.rnDataStore

    private object K {
        val installUuid = stringPreferencesKey("install_uuid")
        val installedAt = longPreferencesKey("installed_at")
        val folderUri = stringPreferencesKey("folder_uri")
        val firstGameAdded = booleanPreferencesKey("first_game_added_logged")
        val firstGameStarted = booleanPreferencesKey("first_game_start_logged")
        val tutorialBeginLogged = booleanPreferencesKey("tutorial_begin_logged")
        val paywallShownOnce = booleanPreferencesKey("first_paywall_shown")
        val smartSetupOn = booleanPreferencesKey("emu_smart_setup")
        val resolution = intPreferencesKey("emu_resolution")
        val frameSkip = intPreferencesKey("emu_frame_skip")
        val effectsOn = booleanPreferencesKey("emu_effects")
        val showFps = booleanPreferencesKey("emu_show_fps")
        val soundOn = booleanPreferencesKey("emu_sound")
        val audioLatency = stringPreferencesKey("emu_audio_latency")
        val fixLagSteps = intPreferencesKey("emu_fix_lag_steps")
        val deviceTier = stringPreferencesKey("device_tier")
        val gpuRenderer = stringPreferencesKey("gpu_renderer")
        val padOpacity = floatPreferencesKey("pad_opacity")
        val padHaptics = booleanPreferencesKey("pad_haptics")
        val usesGamepad = booleanPreferencesKey("uses_gamepad")
        val debugTaps = intPreferencesKey("debug_unlocked")
    }

    /** Random per-install id for Crashlytics `setUserId` — never an account or ad id. */
    suspend fun installUuid(): String {
        store.data.first()[K.installUuid]?.let { return it }
        val id = UUID.randomUUID().toString()
        store.edit {
            if (it[K.installUuid] == null) {
                it[K.installUuid] = id
                it[K.installedAt] = System.currentTimeMillis()
            }
        }
        return store.data.first()[K.installUuid] ?: id
    }

    suspend fun secondsSinceInstall(): Long {
        val at = store.data.first()[K.installedAt] ?: return 0
        return ((System.currentTimeMillis() - at) / 1000).coerceAtLeast(0)
    }

    val folderUri: Flow<String?> = store.data.map { it[K.folderUri] }
    suspend fun setFolderUri(uri: String?) = store.edit {
        if (uri == null) it.remove(K.folderUri) else it[K.folderUri] = uri
    }

    /** Returns true exactly once per install for the given one-shot flag. */
    private suspend fun once(key: Preferences.Key<Boolean>): Boolean {
        var first = false
        store.edit {
            if (it[key] != true) {
                it[key] = true
                first = true
            }
        }
        return first
    }

    suspend fun markFirstGameAdded() = once(K.firstGameAdded)
    suspend fun markFirstGameStarted() = once(K.firstGameStarted)
    suspend fun markTutorialBegin() = once(K.tutorialBeginLogged)
    suspend fun markFirstPaywallShown() = once(K.paywallShownOnce)

    val emulatorDefaults: Flow<EmulatorDefaults> = store.data.map { p ->
        val d = EmulatorDefaults()
        EmulatorDefaults(
            smartSetupOn = p[K.smartSetupOn] ?: d.smartSetupOn,
            resolution = p[K.resolution] ?: d.resolution,
            frameSkip = p[K.frameSkip] ?: d.frameSkip,
            effectsOn = p[K.effectsOn] ?: d.effectsOn,
            showFps = p[K.showFps] ?: d.showFps,
            soundOn = p[K.soundOn] ?: d.soundOn,
            audioLatency = p[K.audioLatency] ?: d.audioLatency,
            fixLagSteps = p[K.fixLagSteps] ?: d.fixLagSteps,
        )
    }

    suspend fun updateEmulatorDefaults(transform: (EmulatorDefaults) -> EmulatorDefaults) {
        val next = transform(emulatorDefaults.first())
        store.edit {
            it[K.smartSetupOn] = next.smartSetupOn
            it[K.resolution] = next.resolution
            it[K.frameSkip] = next.frameSkip
            it[K.effectsOn] = next.effectsOn
            it[K.showFps] = next.showFps
            it[K.soundOn] = next.soundOn
            it[K.audioLatency] = next.audioLatency
            it[K.fixLagSteps] = next.fixLagSteps
        }
    }

    val deviceTier: Flow<String?> = store.data.map { it[K.deviceTier] }
    val gpuRenderer: Flow<String?> = store.data.map { it[K.gpuRenderer] }
    suspend fun setDeviceProfile(tier: String, renderer: String) = store.edit {
        it[K.deviceTier] = tier
        it[K.gpuRenderer] = renderer
    }

    val padPrefs: Flow<PadPrefs> = store.data.map {
        PadPrefs(opacity = it[K.padOpacity] ?: 0.9f, haptics = it[K.padHaptics] ?: true)
    }
    suspend fun setPadOpacity(value: Float) = store.edit { it[K.padOpacity] = value.coerceIn(0.2f, 1f) }
    suspend fun setPadHaptics(value: Boolean) = store.edit { it[K.padHaptics] = value }

    val usesGamepad: Flow<Boolean> = store.data.map { it[K.usesGamepad] ?: false }
    suspend fun setUsesGamepad() = store.edit { it[K.usesGamepad] = true }

    /** Base plan ("yearly" / "monthly") bought on this install — Purchase has no base plan id. */
    val debugUnlocked: Flow<Boolean> = store.data.map { (it[K.debugTaps] ?: 0) > 0 }
    suspend fun unlockDebug() = store.edit { it[K.debugTaps] = 1 }
}
