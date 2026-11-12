package app.letostudio.gameemulator.crash

import android.content.Context
import android.content.SharedPreferences
import app.letostudio.gameemulator.BuildConfig
import app.letostudio.gameemulator.ads.ConsentManager
import app.letostudio.gameemulator.analytics.AnalyticsManager
import app.letostudio.gameemulator.billing.EntitlementManager
import app.letostudio.gameemulator.data.repository.SettingsRepository
import app.letostudio.gameemulator.domain.model.PremiumSource
import app.letostudio.gameemulator.domain.model.PremiumStatus
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
 * The consent gate (same outcome as analytics, nothing written before consent resolves,
 * unsent reports dropped only on an in-session withdrawal) and the non-fatal path
 * (collection check, routine-failure filter, burst cap, redaction).
 *
 * Gate assertions that depend on the SDK switch are guarded by CRASHLYTICS_ENABLED, the
 * AnalyticsManagerGateTest precedent, so flipping the dev switch never fails the suite.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CrashReportingManagerTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(
        val analytics: AnalyticsManager,
        val crash: CrashReportingManager,
        val crashlytics: FirebaseCrashlytics,
        val optOut: MutableStateFlow<Boolean>,
        val premium: MutableStateFlow<PremiumStatus>,
    )

    private fun fixture(consentGranted: Boolean, sdkCollecting: Boolean = true): Fixture {
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
        val optOut = MutableStateFlow(false)
        val settings = mockk<SettingsRepository> { every { analyticsOptOut } returns optOut }
        val premium = MutableStateFlow(PremiumStatus.FREE)
        val entitlement = mockk<EntitlementManager> { every { premiumStatus } returns premium }
        val analytics = AnalyticsManager(
            context, mockk<FirebaseAnalytics>(relaxed = true), consentManager, settings, entitlement,
        )
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true) {
            every { isCrashlyticsCollectionEnabled } returns sdkCollecting
        }
        val crash = CrashReportingManager(crashlytics, analytics, entitlement)
        return Fixture(analytics, crash, crashlytics, optOut, premium)
    }

    // ---- gate ---------------------------------------------------------------------

    @Test
    fun `nothing is written to the persisted switch before consent resolves`() {
        val f = fixture(consentGranted = true)
        verify(exactly = 0) { f.crashlytics.isCrashlyticsCollectionEnabled = any() }
    }

    @Test
    fun `granted consent enables collection`() = runTest {
        val f = fixture(consentGranted = true)
        f.analytics.onConsentResolved()
        assertEquals(BuildConfig.CRASHLYTICS_ENABLED, f.crash.collectionEnabled.first())
        verify { f.crashlytics.isCrashlyticsCollectionEnabled = BuildConfig.CRASHLYTICS_ENABLED }
    }

    @Test
    fun `denied consent writes false but keeps last session's unsent reports`() = runTest {
        val f = fixture(consentGranted = false)
        f.analytics.onConsentResolved()
        assertFalse(f.crash.collectionEnabled.first())
        verify { f.crashlytics.isCrashlyticsCollectionEnabled = false }
        verify(exactly = 0) { f.crashlytics.deleteUnsentReports() }
    }

    @Test
    fun `opting out mid-session disables collection and drops unsent reports`() = runTest {
        if (!BuildConfig.CRASHLYTICS_ENABLED) return@runTest
        val f = fixture(consentGranted = true)
        f.analytics.onConsentResolved()
        f.optOut.value = true
        verify { f.crashlytics.isCrashlyticsCollectionEnabled = false }
        verify(exactly = 1) { f.crashlytics.deleteUnsentReports() }
    }

    @Test
    fun `premium segments reports but never moves the gate`() = runTest {
        val f = fixture(consentGranted = true)
        f.analytics.onConsentResolved()
        verify { f.crashlytics.setCustomKey("is_premium", false) }
        f.premium.value = PremiumStatus(isPremium = true, source = PremiumSource.LIFETIME)
        verify { f.crashlytics.setCustomKey("is_premium", true) }
        assertEquals(BuildConfig.CRASHLYTICS_ENABLED, f.crash.collectionEnabled.first())
    }

    // ---- non-fatals ---------------------------------------------------------------

    @Test
    fun `non-fatal is dropped while the sdk is not collecting`() {
        val f = fixture(consentGranted = true, sdkCollecting = false)
        f.crash.recordNonFatal("billing.query", IllegalStateException("boom"))
        verify(exactly = 0) { f.crashlytics.recordException(any()) }
    }

    @Test
    fun `routine network failures are not reported, even when wrapped`() {
        val f = fixture(consentGranted = true)
        f.crash.recordNonFatal("net", UnknownHostException("api.example.com"))
        f.crash.recordNonFatal("net", RuntimeException("wrapped", SocketTimeoutException()))
        f.crash.recordNonFatal("net", CancellationException("left screen"))
        f.crash.recordNonFatal("net", IOException("Canceled"))
        verify(exactly = 0) { f.crashlytics.recordException(any()) }
    }

    @Test
    fun `bursts are capped per area`() {
        val f = fixture(consentGranted = true)
        repeat(10) { f.crash.recordNonFatal("feed.parse", IllegalStateException("gap $it")) }
        f.crash.recordNonFatal("billing.query", IllegalStateException("other area"))
        verify(exactly = CrashReportingManager.MAX_REPORTS_PER_AREA + 1) {
            f.crashlytics.recordException(any())
        }
    }

    @Test
    fun `messages, causes and breadcrumbs are redacted but the stack is kept`() {
        val f = fixture(consentGranted = true)
        val cause = IOException("GET https://h/live?user=a&password=hunter2 failed")
        val error = IllegalStateException("token=abc123 rejected", cause)
        val sent = slot<Throwable>()
        val crumb = slot<String>()
        every { f.crashlytics.recordException(capture(sent)) } returns Unit
        every { f.crashlytics.log(capture(crumb)) } returns Unit

        f.crash.recordNonFatal("player.open", error, mapOf("url" to "x?api_key=k"))

        assertEquals("java.lang.IllegalStateException: token=*** rejected", sent.captured.message)
        assertFalse(sent.captured.cause!!.message!!.contains("hunter2"))
        assertTrue(sent.captured.cause!!.message!!.contains("password=***"))
        assertEquals(error.stackTrace.toList(), sent.captured.stackTrace.toList())
        assertEquals("player.open url=x?api_key=***", crumb.captured)
    }
}
