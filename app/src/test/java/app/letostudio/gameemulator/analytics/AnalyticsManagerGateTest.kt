package app.letostudio.gameemulator.analytics

import android.content.Context
import android.content.SharedPreferences
import app.letostudio.gameemulator.ads.ConsentManager
import app.letostudio.gameemulator.billing.EntitlementManager
import app.letostudio.gameemulator.data.repository.SettingsRepository
import app.letostudio.gameemulator.domain.model.PremiumSource
import app.letostudio.gameemulator.domain.model.PremiumStatus
import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors [app.letostudio.gameemulator.ads.AdsManagerGateTest]: collection is enabled iff
 * consent AND !optOut AND the build switch — and premium status NEVER moves the gate
 * (FR-005; premium gates ads, analytics is orthogonal).
 *
 * Asserted against `collectionAllowedByPolicy` (not `collectionEnabled`) for the same
 * reason as the ads test: the latter carries the dev-flippable switch, which would make
 * every assertFalse pass vacuously when it is off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsManagerGateTest {

    // AnalyticsManager's scope dispatches on Main (like NativeAdManager's tests).
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val manager: AnalyticsManager,
        val firebase: FirebaseAnalytics,
        val optOut: MutableStateFlow<Boolean>,
        val premium: MutableStateFlow<PremiumStatus>,
    )

    /**
     * consentGranted=true drives truth-table row 2 (update ok, no form required — the
     * non-EEA path); false drives row 3 (form required, no TC string — fail closed).
     */
    private fun fixture(consentGranted: Boolean, optOutInitially: Boolean = false): Fixture {
        val prefs = mockk<SharedPreferences> {
            every { getString("IABTCF_PurposeConsents", null) } returns null
        }
        val context = mockk<Context> {
            every { packageName } returns "app.test"
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        val consentManager = mockk<ConsentManager> {
            every { consentInfoUpdated } returns true
            every { privacyOptionsRequired } returns MutableStateFlow(!consentGranted)
        }
        val optOut = MutableStateFlow(optOutInitially)
        val settingsRepository = mockk<SettingsRepository> {
            every { analyticsOptOut } returns optOut
        }
        val premium = MutableStateFlow(PremiumStatus.FREE)
        val entitlementManager = mockk<EntitlementManager> {
            every { premiumStatus } returns premium
        }
        val firebase = mockk<FirebaseAnalytics>(relaxed = true)
        val manager = AnalyticsManager(
            context, firebase, consentManager, settingsRepository, entitlementManager,
        )
        return Fixture(manager, firebase, optOut, premium)
    }

    @Test
    fun `collection allowed once consent grants analytics storage and no opt-out`() = runTest {
        val f = fixture(consentGranted = true)
        assertFalse(f.manager.collectionAllowedByPolicy.first()) // nothing before resolve
        f.manager.onConsentResolved()
        assertTrue(f.manager.collectionAllowedByPolicy.first())
    }

    @Test
    fun `collection stays off when consent is denied`() = runTest {
        val f = fixture(consentGranted = false)
        f.manager.onConsentResolved()
        assertFalse(f.manager.collectionAllowedByPolicy.first())
    }

    @Test
    fun `opt-out subtracts from granted consent`() = runTest {
        val f = fixture(consentGranted = true)
        f.manager.onConsentResolved()
        assertTrue(f.manager.collectionAllowedByPolicy.first())
        f.optOut.value = true
        assertFalse(f.manager.collectionAllowedByPolicy.first())
    }

    @Test
    fun `opt-out off never adds to denied consent`() = runTest {
        val f = fixture(consentGranted = false, optOutInitially = false)
        f.manager.onConsentResolved()
        assertFalse(f.manager.collectionAllowedByPolicy.first())
    }

    @Test
    fun `premium status never moves the gate in either direction`() = runTest {
        val f = fixture(consentGranted = true)
        f.manager.onConsentResolved()
        assertTrue(f.manager.collectionAllowedByPolicy.first())
        f.premium.value = PremiumStatus(isPremium = true, source = PremiumSource.LIFETIME)
        assertTrue(f.manager.collectionAllowedByPolicy.first())

        val denied = fixture(consentGranted = false)
        denied.manager.onConsentResolved()
        denied.premium.value = PremiumStatus(isPremium = true, source = PremiumSource.LIFETIME)
        assertFalse(denied.manager.collectionAllowedByPolicy.first())
    }

    @Test
    fun `disable via opt-out resets analytics data`() = runTest {
        val f = fixture(consentGranted = true)
        f.manager.onConsentResolved()
        assertTrue(f.manager.collectionAllowedByPolicy.first())
        f.optOut.value = true
        if (AnalyticsConfig.ANALYTICS_ENABLED) {
            verify { f.firebase.setAnalyticsCollectionEnabled(false) }
            verify { f.firebase.resetAnalyticsData() }
        }
    }

    @Test
    fun `collectionEnabled follows the build switch for an otherwise eligible user`() = runTest {
        val f = fixture(consentGranted = true)
        f.manager.onConsentResolved()
        assertEquals(
            AnalyticsConfig.ANALYTICS_ENABLED,
            f.manager.collectionEnabled.first(),
        )
    }
    /** FR-013 + FR-005: the property mirrors every change; the gate never moves. */
    @Test
    fun `premium changes update the is_premium user property without touching the gate`() = runTest {
        val f = fixture(consentGranted = true)
        f.manager.onConsentResolved()
        verify { f.firebase.setUserProperty("is_premium", "false") }
        f.premium.value = PremiumStatus(isPremium = true, source = PremiumSource.LIFETIME)
        verify { f.firebase.setUserProperty("is_premium", "true") }
        assertTrue(f.manager.collectionAllowedByPolicy.first())
    }

}
