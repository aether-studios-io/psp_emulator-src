package app.letostudio.gameemulator.crash

import app.letostudio.gameemulator.BuildConfig
import app.letostudio.gameemulator.analytics.AnalyticsManager
import app.letostudio.gameemulator.billing.EntitlementManager
import app.letostudio.gameemulator.util.redactSecrets
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Crash reporting. Mirrors [AnalyticsManager]: one @Singleton, the gate enforced INSIDE
 * by driving the SDK's own collection switch, an app-scoped Main scope. Kept out of
 * AnalyticsManager on purpose: that class is the entry point for *measurement*, and a
 * crash report is not a measurement.
 *
 * Gate: `CRASHLYTICS_ENABLED && AnalyticsManager.collectionAllowedByPolicy`. It reuses the
 * analytics policy flow instead of re-deriving consent, so there is never a second consent
 * mechanism to drift. It follows the Settings privacy-options form and the local opt-out
 * automatically (both feed that flow), so revoking stops uploads with no restart.
 * Premium is deliberately NOT in the gate, for the same reason as analytics.
 *
 * Accepted consequences of gating:
 *  - On the FIRST launch ever, a crash before consent resolves is never reported (splash,
 *    onboarding). Later launches have no such window, see the init block.
 *  - Users who decline are never reported on at all.
 *
 * Non-fatals ([recordNonFatal]) are rebuilt as [RedactedThrowable] before upload, because
 * Crashlytics uploads exception messages verbatim and OkHttp/Media3 messages can embed a
 * URL with a `token=`/`password=` query. Ported from iptv_v3 (gate) + WeatherForecast
 * (non-fatal filtering, burst cap, breadcrumbs).
 */
@Singleton
class CrashReportingManager @Inject constructor(
    private val crashlytics: FirebaseCrashlytics,
    private val analyticsManager: AnalyticsManager,
    entitlementManager: EntitlementManager,
) : CrashReporter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Build switch AND the shared consent policy. Separate from the policy for the
     * ADS_ENABLED / ANALYTICS_ENABLED reason: the gate stays meaningful in tests.
     */
    val collectionEnabled: Flow<Boolean> =
        analyticsManager.collectionAllowedByPolicy.map { BuildConfig.CRASHLYTICS_ENABLED && it }

    /** Per-area burst guard for non-fatals. Resets with the process; not a rate limit. */
    private val reportsPerArea = ConcurrentHashMap<String, AtomicInteger>()

    init {
        // Segmenting keys only. No PII, no identifiers.
        crashlytics.setCustomKey("build_type", if (BuildConfig.DEBUG) "debug" else "release")

        // The SDK's own switch is the enforcement point, and it PERSISTS across launches
        // (unlike setAnalyticsCollectionEnabled). So write NOTHING until consent has been
        // evaluated: collectionEnabled starts false because consent is merely unresolved,
        // and writing that false would turn collection off for the splash + UMP window on
        // every launch, for exactly the users who DID consent, dropping the startup
        // crashes this class exists to catch.
        scope.launch {
            analyticsManager.consentResolved.first { it }
            var previous = false
            collectionEnabled.collect { enabled ->
                crashlytics.isCrashlyticsCollectionEnabled = enabled
                // Withdrawn DURING this session: drop reports captured but not yet sent
                // (the analogue of AnalyticsManager's resetAnalyticsData). NOT fired for a
                // session that merely starts denied: a consent request that failed on a
                // network blip lands there too, and deleting would destroy last session's
                // crash. Nothing uploads while disabled either way.
                if (previous && !enabled) crashlytics.deleteUnsentReports()
                previous = enabled
            }
        }

        // Lets crash-free rates be read per tier. Segments reports only, never the gate.
        scope.launch {
            entitlementManager.premiumStatus.collect { status ->
                crashlytics.setCustomKey("is_premium", status.isPremium)
            }
        }
    }

    override fun recordNonFatal(area: String, error: Throwable, context: Map<String, String>) {
        // Disabled Crashlytics still writes non-fatals to disk and sends them once enabled.
        // Reading the SDK's persisted switch keeps a declined user's reports off the device
        // while still covering a returning consented user before UMP resolves.
        if (!crashlytics.isCrashlyticsCollectionEnabled) return
        if (isRoutine(error)) return
        if (!claimBudget(area)) return
        crashlytics.log(breadcrumb(area, context))
        crashlytics.recordException(RedactedThrowable.of(error))
    }

    private fun claimBudget(area: String): Boolean =
        reportsPerArea.getOrPut(area) { AtomicInteger() }.incrementAndGet() <= MAX_REPORTS_PER_AREA

    internal companion object {
        const val MAX_REPORTS_PER_AREA = 3

        /**
         * Failures that are the network's fault, not the app's: offline, DNS, timeouts,
         * TLS on captive portals, and coroutine cancellation. Reporting them buries real
         * issues. Walks the cause chain because Retrofit/OkHttp wrap them.
         */
        fun isRoutine(error: Throwable): Boolean =
            generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).any {
                it is CancellationException ||
                    it is UnknownHostException ||
                    it is SocketTimeoutException ||
                    it is ConnectException ||
                    it is SSLException ||
                    (it is IOException && it.message?.contains("canceled", ignoreCase = true) == true)
            }

        fun breadcrumb(area: String, context: Map<String, String>): String = redactSecrets(
            buildString {
                append(area)
                context.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
            },
        )
    }
}

internal const val MAX_CAUSE_DEPTH = 8

/**
 * A copy of a throwable whose message (and every cause's message) went through
 * [redactSecrets]. Keeps the original class name in the message and the original stack
 * trace, so Crashlytics still groups issues by where they were thrown.
 */
internal class RedactedThrowable private constructor(
    message: String,
    cause: Throwable?,
) : Throwable(message, cause) {

    companion object {
        fun of(error: Throwable, depth: Int = 0): RedactedThrowable {
            val cause = error.cause
                ?.takeIf { depth + 1 < MAX_CAUSE_DEPTH && it !== error }
                ?.let { of(it, depth + 1) }
            val text = error.message?.let { ": " + redactSecrets(it) }.orEmpty()
            return RedactedThrowable(error.javaClass.name + text, cause).apply {
                stackTrace = error.stackTrace
            }
        }
    }
}
