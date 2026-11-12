package app.letostudio.gameemulator.crash

import app.letostudio.gameemulator.util.redactSecrets
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RetroNova Crashlytics context (analytics.md §6): custom keys, breadcrumbs and the install
 * user id. Lives in crash/ because the Konsist rule keeps the SDK here; the consent gate is
 * still [CrashReportingManager]'s — keys and logs are only uploaded with a report, and no
 * report leaves the device while collection is off.
 *
 * Breadcrumb text is fixed vocabulary + enums/counts only — never titles, paths or URLs.
 */
@Singleton
class CrashContext @Inject constructor(
    private val crashlytics: FirebaseCrashlytics,
) {
    fun setUserId(installUuid: String) = crashlytics.setUserId(installUuid)

    fun set(key: String, value: String) = crashlytics.setCustomKey(key, value)
    fun set(key: String, value: Boolean) = crashlytics.setCustomKey(key, value)
    fun set(key: String, value: Int) = crashlytics.setCustomKey(key, value)

    fun breadcrumb(area: String, message: String) = crashlytics.log("[$area] ${redactSecrets(message)}")

    object Keys {
        const val SCREEN = "screen"
        const val IS_PRO = "is_pro"
        const val GAME_HASH = "game_hash"
        const val BACKEND = "backend"
        const val RESOLUTION = "resolution"
        const val CORE_VERSION = "core_version"
        const val DEVICE_TIER = "device_tier"
        const val IN_GAME = "in_game"
    }
}
