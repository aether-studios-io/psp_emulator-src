package app.letostudio.gameemulator.ads

import app.letostudio.gameemulator.billing.EntitlementManager
import app.letostudio.gameemulator.domain.model.PremiumSource
import app.letostudio.gameemulator.domain.model.PremiumStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The single most important ads rule: nothing shows for premium or no-consent users. */
class AdsManagerGateTest {

    private fun adsManager(premium: Boolean, canRequestAds: Boolean): AdsManager {
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
        return AdsManager(
            mockk(relaxed = true), consentManager, entitlementManager,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            FullScreenAdState(),
        )
    }

    // The policy gate is asserted directly rather than through `adsEnabled`, which also
    // carries the dev-flippable build switch: with that switch off, every assertFalse
    // below would pass vacuously even if the premium/consent rule were deleted.

    @Test
    fun `ads allowed only for free user with consent`() = runTest {
        assertTrue(adsManager(premium = false, canRequestAds = true).adsAllowedByPolicy.first())
    }

    @Test
    fun `ads disallowed for premium user even with consent`() = runTest {
        assertFalse(adsManager(premium = true, canRequestAds = true).adsAllowedByPolicy.first())
    }

    @Test
    fun `ads disallowed without consent even for free user`() = runTest {
        assertFalse(adsManager(premium = false, canRequestAds = false).adsAllowedByPolicy.first())
    }

    /**
     * `adsEnabled` is the policy gate ANDed with the build switch. Asserted against the
     * switch's current value rather than a hardcoded one, so temporarily silencing ads
     * during development doesn't turn this red.
     */
    @Test
    fun `ads enabled follows the build switch for an otherwise eligible user`() = runTest {
        assertEquals(
            AdsConfig.ADS_ENABLED,
            adsManager(premium = false, canRequestAds = true).adsEnabled.first(),
        )
    }
}
