package app.letostudio.gameemulator.data.prefs

import app.letostudio.gameemulator.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RetroNova feature flags. The template's AdsRemoteConfig owns the single fetch and the
 * defaults; a key without a default reads false, which is the safe value for every flag here.
 */
@Singleton
class AppRemoteFlags @Inject constructor() {
    /** Hidden debug screen in release builds (internal testers only, via an RC condition). */
    val debugMenuInRelease: Boolean get() = runCatching { Firebase.remoteConfig.getBoolean("rn_debug_menu") }.getOrDefault(false)

    val debugMenuAllowed: Boolean get() = BuildConfig.DEBUG || debugMenuInRelease

    companion object {
        /** v1 features stay behind flags until they ship (spec CLAUDE.md §4). */
        const val CHEATS_ENABLED = false
        const val CONTROLS_EDITOR_ENABLED = false
        const val HOMEBREW_LIST_ENABLED = false
        const val DRIVE_BACKUP_ENABLED = false
    }
}
