package app.letostudio.gameemulator.billing

import app.letostudio.gameemulator.billing.data.EntitlementEntity
import app.letostudio.gameemulator.domain.model.PremiumSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Premium derivation rules from data-model.md (FR-017, FR-035, SC-006): which
 * combinations of entitlement rows make the user premium, and when the payment
 * warning is shown.
 */
class EntitlementManagerTest {

    private fun entitlement(
        productId: String = "premium",
        type: String = EntitlementEntity.TYPE_SUBSCRIPTION,
        granted: Boolean = true,
        state: String? = EntitlementEntity.STATE_ACTIVE,
    ) = EntitlementEntity(
        productId = productId,
        entitlementType = type,
        isGranted = granted,
        purchaseToken = "token",
        grantedAt = 1L,
        expiresAt = null,
        subscriptionState = state,
        updatedAt = 1L,
    )

    @Test
    fun `no entitlements means free`() {
        val status = EntitlementManager.derivePremiumStatus(emptyList())
        assertFalse(status.isPremium)
        assertNull(status.source)
    }

    @Test
    fun `active subscription grants premium`() {
        val status = EntitlementManager.derivePremiumStatus(
            listOf(entitlement(state = EntitlementEntity.STATE_ACTIVE)),
        )
        assertTrue(status.isPremium)
        assertEquals(PremiumSource.SUBSCRIPTION, status.source)
        assertFalse(status.showPaymentWarning)
    }

    @Test
    fun `cancelled subscription keeps premium until period end`() {
        val status = EntitlementManager.derivePremiumStatus(
            listOf(entitlement(state = EntitlementEntity.STATE_CANCELLED)),
        )
        assertTrue(status.isPremium)
    }

    @Test
    fun `grace period keeps premium but shows the payment warning`() {
        val status = EntitlementManager.derivePremiumStatus(
            listOf(entitlement(state = EntitlementEntity.STATE_GRACE_PERIOD)),
        )
        assertTrue(status.isPremium)
        assertTrue(status.showPaymentWarning)
    }

    @Test
    fun `on hold, paused, expired and revoked subscriptions are not premium`() {
        listOf(
            EntitlementEntity.STATE_ON_HOLD,
            EntitlementEntity.STATE_PAUSED,
            EntitlementEntity.STATE_EXPIRED,
            EntitlementEntity.STATE_REVOKED,
        ).forEach { state ->
            val status = EntitlementManager.derivePremiumStatus(listOf(entitlement(state = state)))
            assertFalse("state $state must not be premium", status.isPremium)
        }
    }

    @Test
    fun `ungranted entitlements never count`() {
        val status = EntitlementManager.derivePremiumStatus(
            listOf(
                entitlement(granted = false, state = EntitlementEntity.STATE_ACTIVE),
                entitlement(
                    productId = "premium_lifetime",
                    type = EntitlementEntity.TYPE_NON_CONSUMABLE,
                    granted = false,
                    state = null,
                ),
            ),
        )
        assertFalse(status.isPremium)
    }

    @Test
    fun `lifetime purchase grants premium permanently`() {
        val status = EntitlementManager.derivePremiumStatus(
            listOf(
                entitlement(
                    productId = "premium_lifetime",
                    type = EntitlementEntity.TYPE_NON_CONSUMABLE,
                    state = null,
                ),
            ),
        )
        assertTrue(status.isPremium)
        assertEquals(PremiumSource.LIFETIME, status.source)
        assertEquals("premium_lifetime", status.activeProductId)
        assertNull(status.expiresAt)
    }

    @Test
    fun `lifetime wins over an expired subscription`() {
        val status = EntitlementManager.derivePremiumStatus(
            listOf(
                entitlement(state = EntitlementEntity.STATE_EXPIRED),
                entitlement(
                    productId = "premium_lifetime",
                    type = EntitlementEntity.TYPE_NON_CONSUMABLE,
                    state = null,
                ),
            ),
        )
        assertTrue(status.isPremium)
        assertEquals(PremiumSource.LIFETIME, status.source)
    }

    @Test
    fun `consumable entitlements do not grant premium`() {
        val status = EntitlementManager.derivePremiumStatus(
            listOf(
                entitlement(
                    productId = "coins_100",
                    type = EntitlementEntity.TYPE_CONSUMABLE,
                    state = null,
                ),
            ),
        )
        assertFalse(status.isPremium)
    }
}
