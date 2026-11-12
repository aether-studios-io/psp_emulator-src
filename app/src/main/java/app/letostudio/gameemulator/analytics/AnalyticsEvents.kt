package app.letostudio.gameemulator.analytics

import app.letostudio.gameemulator.util.AppError

/**
 * The complete, CLOSED event taxonomy (contracts/analytics-events.md). Adding an event
 * means editing the contract first, then this file. Call sites never write string
 * literals (R8) — they call the typed helpers on [AnalyticsManager], whose signatures
 * only accept these enums / bounded numbers, so no URL, credential, title, or query
 * text can reach an event even by mistake (R7 — the hard privacy rule).
 *
 * TEMPLATE: what ships here is the monetization + lifecycle spine that every app
 * needs. Add feature events as name constants + param constants + a typed helper on
 * AnalyticsManager, and register them in [ALL_EVENT_NAMES] / [ALL_PARAM_NAMES] so
 * AnalyticsEventsTest keeps checking them against GA4's naming limits.
 */
object AnalyticsEvents {

    // Event names (screen_view and ad_impression are GA4 reserved names, used with
    // GA4's own semantics; in_app_purchase is Play-link automatic and NEVER logged here).
    const val PAYWALL_VIEW = "paywall_view"
    const val PAYWALL_DISMISS = "paywall_dismiss"
    const val ONBOARDING_COMPLETE = "onboarding_complete"

    // Parameter names.
    const val PARAM_RESULT = "result"
    const val PARAM_ERROR_KIND = "error_kind"
    const val PARAM_TRIGGER = "trigger"
    const val PARAM_SKIPPED = "skipped"
    const val PARAM_SLIDES_SEEN = "slides_seen"

    // Generic result values.
    const val RESULT_SUCCESS = "success"
    const val RESULT_ERROR = "error"

    /** All app-logged event names — the closed set AnalyticsEventsTest sweeps. */
    val ALL_EVENT_NAMES = listOf(
        PAYWALL_VIEW, PAYWALL_DISMISS, ONBOARDING_COMPLETE,
    )

    /** All parameter names — likewise swept for GA4 limits. */
    val ALL_PARAM_NAMES = listOf(
        PARAM_RESULT, PARAM_ERROR_KIND, PARAM_TRIGGER, PARAM_SKIPPED, PARAM_SLIDES_SEEN,
    )

    /**
     * Derived from the existing [AppError] classification rather than redeclared, so
     * the analytics error class can never drift from what the user was shown.
     */
    enum class ErrorKind(val wire: String) {
        NETWORK("network"),
        HTTP("http"),
        PARSE("parse"),
        AUTH("auth"),
        UNKNOWN("unknown"),
        ;

        companion object {
            fun from(error: AppError): ErrorKind = when (error) {
                is AppError.Network -> NETWORK
                is AppError.Http -> HTTP
                is AppError.Parse -> PARSE
                is AppError.Auth -> AUTH
                is AppError.Unknown -> UNKNOWN
            }
        }
    }

    enum class PaywallTrigger(val wire: String) {
        COLD_START("cold_start"),
        ONBOARDING("onboarding"),
        SETTINGS("settings"),

        /** Every in-app upsell entry — one honest catch-all instead of mislabelling. */
        IN_APP("in_app"),
        ;

        companion object {
            /** Route-arg wire value → trigger; unknown values fall to [IN_APP]. */
            fun fromWire(value: String?): PaywallTrigger =
                entries.firstOrNull { it.wire == value } ?: IN_APP
        }
    }

    enum class AdFormat(val wire: String) {
        BANNER("banner"),
        INTERSTITIAL("interstitial"),
        APP_OPEN("app_open"),
        NATIVE("native"),
        REWARDED("rewarded"),
    }

    /**
     * Bucket lower bounds for a count of user-owned items — an exact count is a
     * fingerprint of one specific user's data; the bucket answers the same product
     * question. Use this rather than logging raw counts.
     */
    fun bucketCount(count: Int): Long = when {
        count >= 10_000 -> 10_000L
        count >= 1_000 -> 1_000L
        count >= 100 -> 100L
        count >= 10 -> 10L
        count >= 1 -> 1L
        else -> 0L
    }

    /** Query length bucket (lower bound); the text itself is never logged (R7). */
    fun bucketQueryLength(length: Int): Long = when {
        length >= 10 -> 10L
        length >= 5 -> 5L
        length >= 3 -> 3L
        length >= 2 -> 2L
        else -> 1L
    }
}
