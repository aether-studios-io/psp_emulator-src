package app.letostudio.gameemulator.core

import android.content.Context
import android.os.ParcelFileDescriptor
import app.letostudio.gameemulator.crash.CrashReporter
import app.letostudio.gameemulator.data.db.GameEntity
import app.letostudio.gameemulator.data.prefs.EmulatorDefaults
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import com.swordfish.libretrodroid.VirtualFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bundled PPSSPP core (built by tools/build_core.sh, GPL-2.0+) and its assets. The `.so` is
 * packaged in the APK/AAB (legacy packaging → extracted to nativeLibraryDir); nothing native
 * is ever downloaded. No BIOS: PPSSPP uses HLE.
 */
@Singleton
class CoreAssets @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crashReporter: CrashReporter,
) {
    val corePath: String get() = File(context.applicationInfo.nativeLibraryDir, CORE_LIB).absolutePath
    val systemDir: File get() = File(context.filesDir, "system")
    val savesDir: File get() = File(context.filesDir, "memstick").apply { mkdirs() }

    /** "v1.20.4 <sha>" from assets/ppsspp/CORE_VERSION (Crashlytics core_version key). */
    val coreVersion: String by lazy {
        runCatching { context.assets.open("ppsspp/CORE_VERSION").bufferedReader().readText().trim().substringBefore(' ') }
            .getOrDefault("unknown")
    }

    fun coreInstalled(): Boolean = File(corePath).exists()

    /** Copies assets/ppsspp → filesDir/system/PPSSPP once per core version. */
    suspend fun ensureInstalled() = withContext(Dispatchers.IO) {
        val target = File(systemDir, "PPSSPP")
        val marker = File(target, ".installed")
        val stamp = "$coreVersion#$ASSET_REV"
        if (marker.exists() && marker.readText() == stamp) return@withContext
        runCatching {
            copyAssetDir("ppsspp", target)
            marker.writeText(stamp)
        }.onFailure { crashReporter.recordNonFatal("core_assets", it) }
    }

    private fun copyAssetDir(path: String, dest: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            dest.parentFile?.mkdirs()
            context.assets.open(path).use { input -> dest.outputStream().use { input.copyTo(it) } }
            return
        }
        dest.mkdirs()
        children.forEach { copyAssetDir("$path/$it", File(dest, it)) }
    }

    /**
     * Frontend config for one game. The game is handed to the core as a libretro virtual file
     * over the SAF file descriptor ([useVirtualFile]) or, as a fallback, as /proc/self/fd/N.
     */
    fun retroViewData(
        game: GameEntity,
        pfd: ParcelFileDescriptor,
        settings: EmulatorDefaults,
        useVirtualFile: Boolean,
    ): GLRetroViewData = GLRetroViewData(context).apply {
        coreFilePath = corePath
        if (useVirtualFile) {
            gameVirtualFiles = listOf(VirtualFile("/rn/game.${game.format}", pfd))
        } else {
            gameFilePath = "/proc/self/fd/${pfd.fd}"
        }
        systemDirectory = systemDir.absolutePath
        savesDirectory = savesDir.absolutePath
        variables = SmartSetup.coreVariables(settings).map { (k, v) -> Variable(k, v) }.toTypedArray()
        shader = ShaderConfig.Default
        preferLowLatencyAudio = settings.audioLatency == "low"
        rumbleEventsEnabled = false
        skipDuplicateFrames = false
    }

    companion object {
        const val CORE_LIB = "libppsspp_libretro_android.so"

        /** Bump when tools/build_core.sh copies a different asset set. */
        const val ASSET_REV = 2
    }
}
