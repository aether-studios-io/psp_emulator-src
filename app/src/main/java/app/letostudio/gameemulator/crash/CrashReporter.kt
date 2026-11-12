package app.letostudio.gameemulator.crash

/**
 * Non-fatal reporting seam. Inject this, never `FirebaseCrashlytics` (a Konsist rule
 * keeps the SDK inside crash/ and di/), so repositories and ViewModels stay mockable.
 *
 * Use it for failures the app SWALLOWS but you would want to know about: a Play Billing
 * call that keeps failing, a parse that drops data, a branch that "cannot happen".
 * Uncaught crashes need nothing: Crashlytics catches them on its own.
 *
 * Fire-and-forget and always safe to call. The implementation drops the report when
 * collection is off, skips routine network failures, caps bursts per [area] and redacts
 * secrets from every message before upload.
 */
interface CrashReporter {

    /**
     * @param area stable, low-cardinality name of where it failed (`"billing.query"`,
     *   `"feed.parse"`). Used for the burst cap and as the breadcrumb prefix.
     * @param context extra breadcrumb fields. Enums, counts, codes only: no user text,
     *   no identifiers, no URLs. Values are still redacted, but that is a net, not a licence.
     */
    fun recordNonFatal(area: String, error: Throwable, context: Map<String, String> = emptyMap())
}
