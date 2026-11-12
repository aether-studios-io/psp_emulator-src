package app.letostudio.gameemulator.analytics

import app.letostudio.gameemulator.BuildConfig

/**
 * Single configuration point for analytics (the [app.letostudio.gameemulator.ads.AdsConfig]
 * role): the build switch plus the GA4 platform limits the taxonomy tests enforce.
 * GA4 truncates or drops limit violations SILENTLY server-side, so a local test against
 * these constants is the only feedback that exists (research R8).
 */
object AnalyticsConfig {

    /**
     * Build-level master switch, ANDed into the collection gate. Mirrors ADS_ENABLED
     * (research R9) and is deliberately separate from the consent/opt-out policy gate
     * ([AnalyticsManager.collectionAllowedByPolicy]) so the policy stays testable
     * whatever this is set to. Flip the debug field in app/build.gradle.kts to false
     * to stop analytics while developing.
     */
    val ANALYTICS_ENABLED: Boolean get() = BuildConfig.ANALYTICS_ENABLED

    // GA4 limits (contracts/analytics-events.md) — enforced by AnalyticsEventsTest.
    const val MAX_EVENT_NAME_LENGTH = 40
    const val MAX_PARAM_NAME_LENGTH = 40
    const val MAX_PARAM_VALUE_LENGTH = 100
    const val MAX_PARAMS_PER_EVENT = 25
    const val MAX_USER_PROPERTY_VALUE_LENGTH = 36
}
