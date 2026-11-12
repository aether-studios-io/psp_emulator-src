package app.letostudio.gameemulator.ads

import app.letostudio.gameemulator.billing.EntitlementManager
import app.letostudio.gameemulator.domain.model.PremiumSource
import app.letostudio.gameemulator.domain.model.PremiumStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Native ads obey the same policy gate as every other format: nothing loads for a
 * premium or no-consent user. The pool never fills for a blocked user, so [adForSlot]
 * always returns null. (Actual ad rendering is verified on a device with the test unit.)
 *
 * The manager's internal scope runs on [Dispatchers.Main]; an unconfined test dispatcher
 * makes [NativeAdManager.ensureLoaded]'s early-return gate run synchronously, so it never
 * reaches a real AdLoader for a blocked user.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NativeAdManagerGateTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun nativeAdManager(premium: Boolean, canRequestAds: Boolean): NativeAdManager {
        val entitlementManager = mockk<EntitlementManager> {
            every { premiumStatus } returns flowOf(
                if (premium) {
                    PremiumStatus(isPremium = true, source = PremiumSource.LIFETIME)
                } else {
                    PremiumStatus.FREE
                },
            )
        }
        val consentManager = mockk<ConsentManager> {
            every { this@mockk.canRequestAds } returns MutableStateFlow(canRequestAds)
        }
        return NativeAdManager(mockk(relaxed = true), consentManager, entitlementManager)
    }

    @Test
    fun `no ad served to a premium user`() {
        val manager = nativeAdManager(premium = true, canRequestAds = true)
        manager.ensureLoaded()
        assertNull(manager.adForSlot(0))
    }

    @Test
    fun `no ad served without consent`() {
        val manager = nativeAdManager(premium = false, canRequestAds = false)
        manager.ensureLoaded()
        assertNull(manager.adForSlot(0))
    }

    @Test
    fun `empty pool returns null for any slot`() {
        // Nothing loaded yet: every slot index maps to no ad rather than crashing.
        val manager = nativeAdManager(premium = false, canRequestAds = true)
        assertNull(manager.adForSlot(0))
        assertNull(manager.adForSlot(7))
    }

    @Test
    fun `acquire and unbalanced release stay safe for a blocked user`() {
        // Two hosts overlap during a nav transition, then both leave; an extra release
        // (racing disposes) must clamp at zero instead of corrupting the refcount.
        val manager = nativeAdManager(premium = true, canRequestAds = true)
        manager.acquire()
        manager.acquire()
        manager.release()
        manager.release()
        manager.release()
        assertNull(manager.adForSlot(0))
    }
}
