package app.letostudio.gameemulator.ads

import android.util.Log
import app.letostudio.gameemulator.BuildConfig
import app.letostudio.gameemulator.billing.StoreRemoteConfig
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Remote Config, scoped to ADS KILL-SWITCHES ONLY (tech_stacks.md §1): every
 * param here gates an ad placement and nothing else — no feature may ever branch on a
 * remote value through this class.
 *
 * Reads are synchronous against the last ACTIVATED config (in-code defaults before the
 * first successful fetch). [fetchInBackground] is kicked from MainActivity.onCreate, so
 * on most launches a flipped param takes effect the NEXT launch. The one racy case is
 * the value both params exist for — the FIRST launch, whose ads fire seconds after
 * install: there the fetch must win a ~2-5s race against the splash/onboarding to apply,
 * otherwise the in-code [DEFAULTS] decide. That partial reach is inherent to
 * remote-configuring first-launch behavior; accepted knowingly.
 *
 * Debug builds drop the fetch throttle (production keeps the SDK's 12h default) so a
 * console flip can be verified by simply relaunching.
 *
 * This class also owns the single fetch and the single `setDefaultsAsync` call for the
 * whole app (the call replaces every default), so it registers the billing store
 * config default ([app.letostudio.gameemulator.billing.StoreRemoteConfig.DEFAULTS]) too.
 * Reading that value is [app.letostudio.gameemulator.billing.StoreRemoteConfig]'s job, not
 * this class's: no feature branches on a remote value through here.
 */
@Singleton
class AdsRemoteConfig @Inject constructor() {

    private val remoteConfig = Firebase.remoteConfig

    init {
        if (BuildConfig.DEBUG) {
            remoteConfig.setConfigSettingsAsync(
                remoteConfigSettings { minimumFetchIntervalInSeconds = 0 },
            )
        }
        remoteConfig.setDefaultsAsync(DEFAULTS + StoreRemoteConfig.DEFAULTS)
    }

    /** Fire-and-forget fetch+activate; failures just leave the previous values active. */
    fun fetchInBackground() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            // The one observability point for the console params — says what the fetch
            // did and what the ACTIVE values are (defaults until a fetch has landed).
            val outcome = if (task.isSuccessful) "activatedNew=${task.result}" else "failed=${task.exception?.message}"
            Log.d(
                TAG,
                "fetchAndActivate $outcome, active: firstLaunchAppOpen=$firstLaunchAppOpenEnabled " +
                    "nativeFullscreen=$nativeFullscreenAdsEnabled",
            )
        }
    }

    /** Cold-start app-open ad on the FIRST launch (before onboarding) — MainActivity. */
    val firstLaunchAppOpenEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_FIRST_LAUNCH_APP_OPEN)

    /** Full-screen native ads between onboarding slides — NativeFullscreenAdManager. */
    val nativeFullscreenAdsEnabled: Boolean
        get() = remoteConfig.getBoolean(KEY_NATIVE_FULLSCREEN)

    private companion object {
        const val TAG = "AdsRemoteConfig"
        const val KEY_FIRST_LAUNCH_APP_OPEN = "ads_first_launch_app_open_enabled"
        const val KEY_NATIVE_FULLSCREEN = "ads_native_fullscreen_enabled"

        // First-launch app-open defaults OFF (product decision 2026-07-19): given the
        // fetch race, most first launches only ever see this default — so the placement
        // is opt-in via console, not opt-out. Native fullscreen stays ON by default.
        val DEFAULTS: Map<String, Any> = mapOf(
            KEY_FIRST_LAUNCH_APP_OPEN to false,
            KEY_NATIVE_FULLSCREEN to true,
        )
    }
}
