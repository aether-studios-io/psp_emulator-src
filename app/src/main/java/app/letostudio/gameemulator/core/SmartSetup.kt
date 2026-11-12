package app.letostudio.gameemulator.core

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.AppUserProps
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.data.prefs.EmulatorDefaults
import app.letostudio.gameemulator.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

enum class DeviceTier(val wire: String) { LOW("low"), MID("mid"), HIGH("high") }

/** Raw device signals for [SmartSetup.tierOf]. */
data class DeviceSignals(val cores: Int, val ramMb: Long, val gpuRenderer: String, val benchScore: Long)

/** One "Fix lag" step (S15). */
enum class FixLagStep { RESOLUTION, FRAME_SKIP, EFFECTS }

/**
 * Smart setup + Fix lag (F12). Pure decisions live in the companion so they are unit-tested;
 * the instance runs the one-time device probe (cores, RAM, GL renderer, 3 s benchmark).
 */
@Singleton
class SmartSetup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val analytics: Analytics,
    private val crash: CrashContext,
) {
    /** Runs the probe once (first game launch); later calls return the stored tier. */
    suspend fun ensureProfiled(): DeviceTier {
        settingsStore.deviceTier.first()?.let { stored -> return DeviceTier.entries.first { it.wire == stored } }
        val start = System.currentTimeMillis()
        val signals = withContext(Dispatchers.Default) {
            DeviceSignals(
                cores = Runtime.getRuntime().availableProcessors(),
                ramMb = totalRamMb(),
                gpuRenderer = glRenderer(),
                benchScore = benchmark(BENCH_MS),
            )
        }
        val tier = tierOf(signals)
        settingsStore.setDeviceProfile(tier.wire, signals.gpuRenderer.take(64))
        val defaults = defaultsFor(tier)
        settingsStore.updateEmulatorDefaults {
            if (it.smartSetupOn) it.copy(resolution = defaults.resolution, frameSkip = defaults.frameSkip, effectsOn = defaults.effectsOn, fixLagSteps = 0) else it
        }
        analytics.log(
            AppEvents.SMART_SETUP_RUN,
            mapOf(
                AppParams.TIER to tier.wire,
                AppParams.BACKEND to BACKEND,
                AppParams.RESOLUTION to "${defaults.resolution}x",
                AppParams.DURATION_MS to System.currentTimeMillis() - start,
            ),
        )
        analytics.setUserProperty(AppUserProps.DEVICE_TIER, tier.wire)
        crash.set(CrashContext.Keys.DEVICE_TIER, tier.wire)
        return tier
    }

    private fun totalRamMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return info.totalMem / 1_048_576
    }

    /** GL_RENDERER from a throwaway 1×1 pbuffer context (GLES 3 required by the manifest). */
    private fun glRenderer(): String = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(
            display,
            intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40 /* EGL_OPENGL_ES3_BIT */, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE),
            0, configs, 0, 1, num, 0,
        )
        val ctx = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(0x3098, 3, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surface, surface, ctx)
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, ctx)
        EGL14.eglTerminate(display)
        renderer
    }.getOrDefault("")

    companion object {
        const val BACKEND = "opengl"
        const val BENCH_MS = 3_000L

        /** Synthetic single-thread float benchmark: iterations completed in [millis]. */
        fun benchmark(millis: Long): Long {
            val end = System.nanoTime() + millis * 1_000_000
            var x = 1.0001
            var n = 0L
            while (System.nanoTime() < end) {
                repeat(1_000) { x = x * 1.0000001 + 0.0000001; if (x > 2) x = 1.0001 }
                n += 1_000
            }
            return if (x > 0) n else n + 1
        }

        /** Tier mapping (unit-tested). Score is iterations per 3 s on one core. */
        fun tierOf(s: DeviceSignals): DeviceTier {
            val gpu = s.gpuRenderer.lowercase()
            val weakGpu = Regex("""adreno \(tm\) (3\d\d|4\d\d|50\d)|mali-(t|4|g5[12])|powervr""").containsMatchIn(gpu)
            val strongGpu = Regex("""adreno \(tm\) (6[4-9]\d|7\d\d|8\d\d)|mali-g(7[1-9]|71\d|72\d|9\d\d)|immortalis|xclipse""").containsMatchIn(gpu)
            var points = 0
            points += when { s.cores >= 8 -> 2; s.cores >= 6 -> 1; else -> 0 }
            points += when { s.ramMb >= 7_000 -> 2; s.ramMb >= 3_500 -> 1; else -> 0 }
            points += when { s.benchScore >= 120_000_000 -> 2; s.benchScore >= 50_000_000 -> 1; else -> 0 }
            if (strongGpu) points += 2
            if (weakGpu) points -= 2
            return when {
                points >= 6 -> DeviceTier.HIGH
                points >= 3 -> DeviceTier.MID
                else -> DeviceTier.LOW
            }
        }

        fun defaultsFor(tier: DeviceTier): EmulatorDefaults = when (tier) {
            DeviceTier.HIGH -> EmulatorDefaults(resolution = 2, frameSkip = 0, effectsOn = true)
            DeviceTier.MID -> EmulatorDefaults(resolution = 2, frameSkip = 0, effectsOn = true)
            DeviceTier.LOW -> EmulatorDefaults(resolution = 1, frameSkip = 0, effectsOn = true)
        }

        fun compatFor(tier: DeviceTier?): String? = when (tier) {
            DeviceTier.HIGH, DeviceTier.MID -> "great"
            DeviceTier.LOW -> "tweaks"
            null -> null
        }

        /** Global defaults with this game's overrides applied. */
        fun effectiveFor(game: GameEntity?, d: EmulatorDefaults): EmulatorDefaults = if (game == null) d else d.copy(
            resolution = game.resolution ?: d.resolution,
            frameSkip = game.frameSkip ?: d.frameSkip,
            effectsOn = game.effectsOn ?: d.effectsOn,
            fixLagSteps = game.fixLagSteps ?: d.fixLagSteps,
        )

        /** Next Fix-lag step for [s], or null at "Lowest settings". */
        fun nextFixLag(s: EmulatorDefaults): FixLagStep? = when {
            s.resolution > 1 -> FixLagStep.RESOLUTION
            s.frameSkip == 0 -> FixLagStep.FRAME_SKIP
            s.effectsOn -> FixLagStep.EFFECTS
            else -> null
        }

        fun applyFixLag(s: EmulatorDefaults, step: FixLagStep): EmulatorDefaults = when (step) {
            FixLagStep.RESOLUTION -> s.copy(resolution = 1, fixLagSteps = s.fixLagSteps + 1)
            FixLagStep.FRAME_SKIP -> s.copy(frameSkip = 1, fixLagSteps = s.fixLagSteps + 1)
            FixLagStep.EFFECTS -> s.copy(effectsOn = false, fixLagSteps = s.fixLagSteps + 1)
        }

        /** PPSSPP libretro core options for these settings. */
        fun coreVariables(s: EmulatorDefaults): List<Pair<String, String>> = listOf(
            "ppsspp_backend" to "opengl",
            "ppsspp_internal_resolution" to when (s.resolution) {
                1 -> "480x272"
                3 -> "1440x816"
                4 -> "1920x1088"
                else -> "960x544"
            },
            "ppsspp_auto_frameskip" to if (s.frameSkip > 0) "enabled" else "disabled",
            "ppsspp_frameskip" to if (s.frameSkip > 0) "1" else "disabled",
            "ppsspp_skip_buffer_effects" to if (s.effectsOn) "disabled" else "enabled",
            "ppsspp_lower_resolution_for_effects" to if (s.effectsOn) "disabled" else "aggressive",
            "ppsspp_texture_scaling_level" to "disabled",
            "ppsspp_cpu_core" to "JIT",
            "ppsspp_fast_memory" to "enabled",
            "ppsspp_language" to "English",
            "ppsspp_button_preference" to "Cross",
            "ppsspp_enable_wlan" to "disabled",
        )
    }
}
