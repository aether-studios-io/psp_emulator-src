package app.letostudio.gameemulator.analytics

import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * RetroNova tracking seam (analytics.md §1). Event and param names come from [AppEvents] /
 * [AppParams]; values are enums, counts, booleans or [game hashes][GameHash] — never titles,
 * file names, paths, typed text or emails.
 *
 * Collection is still governed by the template's consent gate: [AnalyticsManager] owns the
 * SDK's global collection switch (UMP consent, Consent Mode v2, debug build switch), so
 * events logged here while collection is off are dropped by the SDK itself.
 */
interface Analytics {
    fun log(event: String, params: Map<String, Any?> = emptyMap())
    fun setUserProperty(name: String, value: String?)

    /** Manual `screen_view` with `screen_class` = MainActivity (analytics.md §2). */
    fun screen(screenName: String) = log(
        AppEvents.SCREEN_VIEW,
        mapOf(AppParams.SCREEN_NAME to screenName, AppParams.SCREEN_CLASS to "MainActivity"),
    )
}

/** Sends to Firebase Analytics. */
class FirebaseTracker(private val firebase: FirebaseAnalytics) : Analytics {
    override fun log(event: String, params: Map<String, Any?>) {
        firebase.logEvent(event, params.toBundle())
    }

    override fun setUserProperty(name: String, value: String?) {
        firebase.setUserProperty(name, value)
    }
}

/** Mirrors every call to Logcat (tag `RN-Analytics`) — debug builds only. */
class DebugTracker : Analytics {
    override fun log(event: String, params: Map<String, Any?>) {
        Log.d(TAG, if (params.isEmpty()) event else "$event $params")
    }

    override fun setUserProperty(name: String, value: String?) {
        Log.d(TAG, "user_property $name=$value")
    }

    private companion object {
        const val TAG = "RN-Analytics"
    }
}

/** Fans one call out to several trackers. */
class CompositeTracker(private val trackers: List<Analytics>) : Analytics {
    override fun log(event: String, params: Map<String, Any?>) = trackers.forEach { it.log(event, params) }
    override fun setUserProperty(name: String, value: String?) = trackers.forEach { it.setUserProperty(name, value) }
}

internal fun Map<String, Any?>.toBundle(): Bundle = Bundle().also { bundle ->
    forEach { (key, value) ->
        when (value) {
            null -> Unit
            is String -> bundle.putString(key, value.take(AnalyticsConfig.MAX_PARAM_VALUE_LENGTH))
            is Int -> bundle.putLong(key, value.toLong())
            is Long -> bundle.putLong(key, value)
            is Double -> bundle.putDouble(key, value)
            is Float -> bundle.putDouble(key, value.toDouble())
            // GA4 has no boolean type: send as "true"/"false" strings so reports read them.
            is Boolean -> bundle.putString(key, value.toString())
            is Enum<*> -> bundle.putString(key, value.name.lowercase())
            else -> bundle.putString(key, value.toString().take(AnalyticsConfig.MAX_PARAM_VALUE_LENGTH))
        }
    }
}
