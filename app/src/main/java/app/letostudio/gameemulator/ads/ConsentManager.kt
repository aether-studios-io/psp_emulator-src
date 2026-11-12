package app.letostudio.gameemulator.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import app.letostudio.gameemulator.BuildConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Wraps Google UMP (User Messaging Platform) for GDPR consent. `canRequestAds` is THE
 * gate for the whole ads stack (tech_stacks.md §1): MobileAds is never initialized and
 * no ad is ever loaded while it is false. UMP persists its own state, so on later cold
 * starts the cached value is available synchronously before any network round-trip.
 */
@Singleton
class ConsentManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    private val _canRequestAds = MutableStateFlow(consentInformation.canRequestAds())
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(privacyOptionsRequired())
    /** True when UMP requires a re-show entry point (Settings "Ad privacy" row). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** True while a UMP form is on screen — full-screen ads must not cover it. */
    @Volatile
    var isFormVisible: Boolean = false
        private set

    /**
     * True once [gatherConsent] has completed this process, whatever the outcome. Lets
     * the cold-start ad wait distinguish "consent still resolving — keep waiting" from
     * "resolved and denied — stop waiting now" ([canRequestAds] alone can't tell).
     */
    @Volatile
    var isResolvedThisSession: Boolean = false
        private set

    /**
     * Whether the last [gatherConsent] consent-info request SUCCEEDED (false = network
     * failure → analytics fails closed, truth-table row 1). Read-only widening for
     * [app.letostudio.gameemulator.analytics.AnalyticsManager] — UMP stays owned here,
     * analytics only consumes (specs/006-analytics plan, Structure Decision).
     */
    @Volatile
    var consentInfoUpdated: Boolean = false
        private set

    /**
     * Refreshes consent info and shows the consent form when required (EEA users
     * without a prior choice). Suspends until the form is dismissed or the request
     * fails — callers can safely initialize MobileAds right after. Failures are
     * non-fatal: ads simply stay disabled until the next launch retries.
     */
    suspend fun gatherConsent(activity: Activity) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (BuildConfig.DEBUG && BuildConfig.FORCE_EEA_FOR_TESTING) {
                    val debugSettings = ConsentDebugSettings.Builder(context)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    if (BuildConfig.UMP_TEST_DEVICE_ID.isNotEmpty()) {
                        debugSettings.addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_ID)
                    }
                    setConsentDebugSettings(debugSettings.build())
                }
            }
            .build()

        val updated = suspendCancellableCoroutine<Boolean> { continuation ->
            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                { continuation.resume(true) },
                { error ->
                    Log.w(TAG, "Consent info update failed: ${error.errorCode} ${error.message}")
                    continuation.resume(false)
                },
            )
        }

        consentInfoUpdated = updated
        if (updated) {
            suspendCancellableCoroutine { continuation ->
                isFormVisible = true
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    isFormVisible = false
                    if (formError != null) {
                        Log.w(TAG, "Consent form failed: ${formError.errorCode} ${formError.message}")
                    }
                    continuation.resume(Unit)
                }
            }
        }
        refreshGates()
        isResolvedThisSession = true
    }

    /**
     * Re-shows the consent form so EEA users can change their choice (GDPR requirement).
     * [onDismissed] fires after the gates refresh — analytics re-evaluates its own
     * consent there (FR-006) without ConsentManager depending on the analytics package.
     */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit = {}) {
        isFormVisible = true
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            isFormVisible = false
            if (formError != null) {
                Log.w(TAG, "Privacy options form failed: ${formError.errorCode} ${formError.message}")
            }
            refreshGates()
            onDismissed()
        }
    }

    private fun refreshGates() {
        _canRequestAds.value = consentInformation.canRequestAds()
        _privacyOptionsRequired.value = privacyOptionsRequired()
    }

    private fun privacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    private companion object {
        const val TAG = "ConsentManager"
    }
}
