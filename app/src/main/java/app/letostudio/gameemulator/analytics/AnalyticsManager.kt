package app.letostudio.gameemulator.analytics

import android.content.Context
import androidx.core.os.bundleOf
import app.letostudio.gameemulator.BuildConfig
import app.letostudio.gameemulator.ads.ConsentManager
import app.letostudio.gameemulator.billing.EntitlementManager
import app.letostudio.gameemulator.data.repository.SettingsRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The single entry point for all measurement (contracts/analytics-manager.md). Mirrors
 * [app.letostudio.gameemulator.ads.AdsManager] deliberately: one @Singleton, gates
 * enforced INSIDE rather than at call sites, an app-scoped Main scope, a sibling config
 * object. A reviewer who knows ads/ needs no orientation here.
 *
 * Gate: `ANALYTICS_ENABLED && analyticsStorage == GRANTED && !localOptOut` — enforced
 * by driving the SDK's own `setAnalyticsCollectionEnabled`, so an event racing consent
 * resolution is dropped by the SDK rather than queued and flushed later. Premium is
 * deliberately NOT in the gate (FR-005): premium gates ads; analytics is orthogonal,
 * and leaking it in would silently stop measuring paying users.
 *
 * Write-only by contract (R12): no getter, no readable flow of analytics data, and no
 * public raw `logEvent(name, bundle)` — that absence is what keeps the taxonomy closed
 * and the no-PII rule structural. Typed helpers arrive with their user stories.
 *
 * Holds no Activity — unlike BannerAdManager, needs no destroy().
 */
@Singleton
class AnalyticsManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val firebaseAnalytics: FirebaseAnalytics,
    private val consentManager: ConsentManager,
    settingsRepository: SettingsRepository,
    entitlementManager: EntitlementManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Set by [applyConsent]; false until the first consent resolution of the session. */
    private val analyticsStorageGranted = MutableStateFlow(false)

    private val _consentResolved = MutableStateFlow(false)

    /**
     * True once [applyConsent] has run at least once this process, whatever the outcome.
     * From that point a `false` from [collectionAllowedByPolicy] means "denied" rather
     * than "not asked yet". Analytics does not need the distinction (its SDK switch does
     * not persist), but CrashReportingManager does: Crashlytics' switch DOES persist, so
     * writing the pre-consent false would clobber a grant from a previous launch.
     */
    internal val consentResolved: StateFlow<Boolean> = _consentResolved.asStateFlow()

    /**
     * The policy rule alone (consent AND !optOut) — kept separate from the build switch
     * so it stays meaningful in unit tests whatever the switch is set to (the
     * AdsManager.adsAllowedByPolicy precedent).
     */
    internal val collectionAllowedByPolicy: Flow<Boolean> = combine(
        analyticsStorageGranted,
        settingsRepository.analyticsOptOut,
    ) { granted, optOut -> granted && !optOut }

    /** True when the SDK should collect: the build switch AND the policy gate. */
    val collectionEnabled: Flow<Boolean> =
        collectionAllowedByPolicy.map { AnalyticsConfig.ANALYTICS_ENABLED && it }

    init {
        // build_type user property, once at init (FR-019): lets debug traffic be
        // excluded from production reports. Held by the SDK until collection enables.
        firebaseAnalytics.setUserProperty(
            "build_type",
            if (BuildConfig.DEBUG) "debug" else "release",
        )

        // The SDK's own switch is the enforcement point (contract: Gating). On every
        // enable->disable transition the stored identifiers are cleared too (US6).
        scope.launch {
            var previous = false
            collectionEnabled.collect { enabled ->
                firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
                if (previous && !enabled) firebaseAnalytics.resetAnalyticsData()
                previous = enabled
            }
        }

        // is_premium user property (FR-013): mirrors every entitlement change so funnels
        // can segment. It SEGMENTS reports only — it never touches the gate (FR-005).
        scope.launch {
            entitlementManager.premiumStatus.collect { status ->
                firebaseAnalytics.setUserProperty("is_premium", status.isPremium.toString())
            }
        }
    }

    /**
     * Map the UMP outcome → setConsent → apply the gate. Called from MainActivity right
     * after `consentManager.gatherConsent()`, beside `adsManager.onConsentResolved()` —
     * already off the first-frame path (SC-007).
     */
    suspend fun onConsentResolved() {
        applyConsent()
    }

    /** Re-evaluate after the Settings UMP privacy-options form (FR-006) — no restart. */
    fun onPrivacyOptionsFormDismissed() {
        scope.launch { applyConsent() }
    }

    private fun applyConsent() {
        val consent = AnalyticsConsentMapper.map(
            ConsentInput(
                consentInfoUpdated = consentManager.consentInfoUpdated,
                formRequired = consentManager.privacyOptionsRequired.value,
                tcfPurposeConsents = readTcfPurposeConsents(),
            ),
        )
        firebaseAnalytics.setConsent(
            mapOf(
                ConsentType.ANALYTICS_STORAGE to consent.analyticsStorage,
                ConsentType.AD_STORAGE to consent.adStorage,
                ConsentType.AD_USER_DATA to consent.adUserData,
                ConsentType.AD_PERSONALIZATION to consent.adPersonalization,
            ),
        )
        analyticsStorageGranted.value = consent.analyticsStorage == ConsentStatus.GRANTED
        _consentResolved.value = true
    }

    /**
     * `screen_view` with a stable, argument-free name from [ScreenNames]. The single
     * caller is AppNavHost (research R5) — the one documented composable-layer
     * exception, named in the Konsist rule. Fire-and-forget like every helper: the
     * SDK's collection switch is the gate, so this is always safe to call.
     */
    fun logScreenView(screenName: String) {
        firebaseAnalytics.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            bundleOf(
                FirebaseAnalytics.Param.SCREEN_NAME to screenName,
                // Single-activity Compose: the SDK's own class value is useless here.
                FirebaseAnalytics.Param.SCREEN_CLASS to "AppNavHost",
            ),
        )
    }

    // ---- Typed event helpers (contracts/analytics-events.md) ----------------------
    // Fire-and-forget: no return value, no exception, no suspension. Signatures accept
    // only enums / bounded numbers / booleans — the structural no-PII rule (R7). The
    // SDK's collection switch is the gate, so every helper is always safe to call.

    fun logPaywallView(trigger: AnalyticsEvents.PaywallTrigger) {
        firebaseAnalytics.logEvent(
            AnalyticsEvents.PAYWALL_VIEW,
            bundleOf(AnalyticsEvents.PARAM_TRIGGER to trigger.wire),
        )
    }

    /** Fires only on dismissal WITHOUT purchase — in_app_purchase covers the rest. */
    fun logPaywallDismiss(trigger: AnalyticsEvents.PaywallTrigger) {
        firebaseAnalytics.logEvent(
            AnalyticsEvents.PAYWALL_DISMISS,
            bundleOf(AnalyticsEvents.PARAM_TRIGGER to trigger.wire),
        )
    }

    /** ad_impression, GA4 reserved semantics, from OnPaidEventListener (R6). */
    fun logAdImpression(
        format: AnalyticsEvents.AdFormat,
        valueMicros: Long,
        currencyCode: String,
        precisionType: Int,
    ) {
        firebaseAnalytics.logEvent(
            FirebaseAnalytics.Event.AD_IMPRESSION,
            bundleOf(
                FirebaseAnalytics.Param.AD_PLATFORM to "AdMob",
                FirebaseAnalytics.Param.AD_FORMAT to format.wire,
                FirebaseAnalytics.Param.VALUE to valueMicros / 1_000_000.0,
                FirebaseAnalytics.Param.CURRENCY to currencyCode,
                "precision" to precisionName(precisionType),
            ),
        )
    }

    fun logOnboardingComplete(skipped: Boolean, slidesSeen: Int) {
        firebaseAnalytics.logEvent(
            AnalyticsEvents.ONBOARDING_COMPLETE,
            bundleOf(
                AnalyticsEvents.PARAM_SKIPPED to skipped,
                AnalyticsEvents.PARAM_SLIDES_SEEN to slidesSeen.coerceIn(1, 3).toLong(),
            ),
        )
    }

    /** AdValue.PrecisionType int → contract wire value. */
    private fun precisionName(precisionType: Int): String = when (precisionType) {
        1 -> "estimated"
        2 -> "publisher_provided"
        3 -> "precise"
        else -> "unknown"
    }

    /**
     * UMP writes the TC string to the DEFAULT SharedPreferences file. Read directly
     * (not via the preference library) — a cheap in-memory read after first access,
     * and it only ever happens in the consent-resolved path, never at app start.
     */
    private fun readTcfPurposeConsents(): String? =
        context
            .getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
            .getString("IABTCF_PurposeConsents", null)
}
