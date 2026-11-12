package app.letostudio.gameemulator.analytics

import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus

/**
 * The mapper's inputs, assembled by [AnalyticsManager] from ConsentManager and the
 * UMP-written `IABTCF_PurposeConsents` preference. A plain data class with no Android
 * dependency — what lets the truth table be tested exhaustively on the JVM.
 */
data class ConsentInput(
    val consentInfoUpdated: Boolean,
    val formRequired: Boolean,
    /** `IABTCF_PurposeConsents`: index n-1 = purpose n, '1' = granted. Null when absent. */
    val tcfPurposeConsents: String?,
)

/** The four Consent Mode v2 signals, applied via FirebaseAnalytics.setConsent. */
data class AnalyticsConsent(
    val analyticsStorage: ConsentStatus,
    val adStorage: ConsentStatus,
    val adUserData: ConsentStatus,
    val adPersonalization: ConsentStatus,
)

/**
 * UMP outcome → Consent Mode v2 signals. The legally load-bearing logic of the feature,
 * isolated as a PURE function (research R4) so contracts/consent-mapping.md's truth
 * table is exhaustively asserted by [AnalyticsConsentMapperTest].
 *
 * ⚠️ The purpose-derivation numbers mirror the contract table, which is UNVERIFIED
 * against Google's canonical purpose mapping (contract's own warning). A green unit
 * suite proves this file matches the table, not that the table is right — only the
 * granular DebugView pass (quickstart US1 / T019a-b) can settle that. If they disagree,
 * fix the CONTRACT TABLE and this file together; never loosen the mapper ad hoc.
 *
 * `canRequestAds` is deliberately not an input: it is a coarse ads-oriented signal that
 * is wrong in both directions for analytics (see the contract).
 */
object AnalyticsConsentMapper {

    // TCF v2.2 purpose numbers (1-based).
    private const val PURPOSE_STORE_ACCESS = 1
    private const val PURPOSE_PERSONALISED_ADS_PROFILE = 3
    private const val PURPOSE_USE_PERSONALISED_ADS_PROFILE = 4
    private const val PURPOSE_MEASURE_AD_PERFORMANCE = 7
    private const val PURPOSE_MEASURE_CONTENT_PERFORMANCE = 8
    private const val PURPOSE_AUDIENCE_STATISTICS = 9

    /** Purposes up to 9 are consulted; a shorter string cannot answer and fails closed. */
    private const val MIN_TC_LENGTH = 9

    fun map(input: ConsentInput): AnalyticsConsent {
        // Row 1: the consent info request failed — fail closed, next launch retries.
        if (!input.consentInfoUpdated) return allDenied()

        // Row 2: no form required → outside the EU consent policy's scope. The common
        // non-EEA path, which must not be broken by over-caution.
        if (!input.formRequired) return allGranted()

        // Rows 3+4: a form is required. Without a usable TC string the user has not
        // responded (or the string is garbage) — fail closed, never partially granted.
        val tc = input.tcfPurposeConsents?.trim().orEmpty()
        val usable = tc.length >= MIN_TC_LENGTH && tc.all { it == '0' || it == '1' }
        if (!usable) return allDenied()

        fun granted(purpose: Int): Boolean = tc[purpose - 1] == '1'

        val storeAccess = granted(PURPOSE_STORE_ACCESS)
        return AnalyticsConsent(
            analyticsStorage = status(
                storeAccess &&
                    (granted(PURPOSE_MEASURE_CONTENT_PERFORMANCE) || granted(PURPOSE_AUDIENCE_STATISTICS)),
            ),
            adStorage = status(storeAccess),
            adUserData = status(storeAccess && granted(PURPOSE_MEASURE_AD_PERFORMANCE)),
            adPersonalization = status(
                granted(PURPOSE_PERSONALISED_ADS_PROFILE) && granted(PURPOSE_USE_PERSONALISED_ADS_PROFILE),
            ),
        )
    }

    private fun status(granted: Boolean): ConsentStatus =
        if (granted) ConsentStatus.GRANTED else ConsentStatus.DENIED

    private fun allGranted() = AnalyticsConsent(
        ConsentStatus.GRANTED, ConsentStatus.GRANTED, ConsentStatus.GRANTED, ConsentStatus.GRANTED,
    )

    private fun allDenied() = AnalyticsConsent(
        ConsentStatus.DENIED, ConsentStatus.DENIED, ConsentStatus.DENIED, ConsentStatus.DENIED,
    )
}
