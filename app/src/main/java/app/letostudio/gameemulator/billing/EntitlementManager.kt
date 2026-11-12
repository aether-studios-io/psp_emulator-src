package app.letostudio.gameemulator.billing

import app.letostudio.gameemulator.billing.data.EntitlementDao
import app.letostudio.gameemulator.billing.data.EntitlementEntity
import app.letostudio.gameemulator.domain.model.PremiumSource
import app.letostudio.gameemulator.domain.model.PremiumStatus
import com.android.billingclient.api.Purchase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Derives entitlements and premium status from purchases (US1/US2/US4) and reconciles
 * the local Room state against Google Play's query results on every launch (FR-049).
 *
 * Client-side only: without RTDN the observable subscription states are ACTIVE
 * (returned + auto-renewing), CANCELLED (returned + not auto-renewing) and
 * EXPIRED/absent. Grace-period/on-hold/paused constants are still modeled so the
 * derivation handles them if a backend feeds them in later.
 */
@Singleton
class EntitlementManager @Inject constructor(
    private val entitlementDao: EntitlementDao,
) {

    /** Reactive premium status; the paywall and any premium gate observe this. */
    val premiumStatus: Flow<PremiumStatus> =
        entitlementDao.observeAll().map { derivePremiumStatus(it) }

    /** One-shot read of the cached status for the cold-start paywall decision (FR-048). */
    suspend fun currentPremiumStatus(): PremiumStatus =
        derivePremiumStatus(entitlementDao.getAll())

    suspend fun hasSubscriptionHistory(): Boolean =
        entitlementDao.hasAnyOfType(EntitlementEntity.TYPE_SUBSCRIPTION)

    /** Grants entitlements for a PURCHASED purchase (step 1 of the pipeline, FR-010). */
    suspend fun grant(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val now = System.currentTimeMillis()
        purchase.products.forEach { productId ->
            entitlementDao.upsert(
                EntitlementEntity(
                    productId = productId,
                    entitlementType = entitlementType(productId),
                    isGranted = true,
                    purchaseToken = purchase.purchaseToken,
                    grantedAt = entitlementDao.getByProductId(productId)?.grantedAt ?: now,
                    expiresAt = null,
                    subscriptionState = subscriptionStateOf(productId, purchase),
                    updatedAt = now,
                ),
            )
        }
    }

    /** Consumables are single-use: revoke the entitlement once the credit is applied. */
    suspend fun revokeConsumed(productId: String) {
        entitlementDao.delete(productId)
    }

    /**
     * Reconciliation per data-model.md: upsert entitlements for every purchase Google
     * Play returns, then revoke local entitlements Google Play no longer reports
     * (expired/revoked subscriptions, refunded products). Consumables are skipped —
     * consumed items never restore (US3).
     */
    suspend fun reconcile(playPurchases: List<Purchase>) {
        val now = System.currentTimeMillis()
        val ownedProductIds = mutableSetOf<String>()

        playPurchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    grant(purchase)
                    ownedProductIds += purchase.products
                }
                // PENDING purchases grant nothing (FR-013) but are not revocations.
                else -> ownedProductIds += purchase.products
            }
        }

        entitlementDao.getAll()
            .filter { it.entitlementType != EntitlementEntity.TYPE_CONSUMABLE }
            .filter { it.isGranted && it.productId !in ownedProductIds }
            .forEach { stale ->
                val state = if (stale.entitlementType == EntitlementEntity.TYPE_SUBSCRIPTION) {
                    EntitlementEntity.STATE_EXPIRED
                } else {
                    EntitlementEntity.STATE_REVOKED
                }
                entitlementDao.revoke(stale.productId, state, now)
            }
    }

    private fun entitlementType(productId: String): String = when {
        BillingConfig.isSubscription(productId) -> EntitlementEntity.TYPE_SUBSCRIPTION
        BillingConfig.isConsumable(productId) -> EntitlementEntity.TYPE_CONSUMABLE
        else -> EntitlementEntity.TYPE_NON_CONSUMABLE
    }

    private fun subscriptionStateOf(productId: String, purchase: Purchase): String? {
        if (!BillingConfig.isSubscription(productId)) return null
        return if (purchase.isAutoRenewing) {
            EntitlementEntity.STATE_ACTIVE
        } else {
            EntitlementEntity.STATE_CANCELLED
        }
    }

    companion object {
        /** Premium derivation rules from data-model.md — pure and unit-testable. */
        fun derivePremiumStatus(entitlements: List<EntitlementEntity>): PremiumStatus {
            val granted = entitlements.filter { it.isGranted }

            val lifetime = granted.firstOrNull {
                it.entitlementType == EntitlementEntity.TYPE_NON_CONSUMABLE
            }
            if (lifetime != null) {
                return PremiumStatus(
                    isPremium = true,
                    source = PremiumSource.LIFETIME,
                    activeProductId = lifetime.productId,
                )
            }

            val premiumSub = granted.firstOrNull {
                it.entitlementType == EntitlementEntity.TYPE_SUBSCRIPTION &&
                    it.subscriptionState in PREMIUM_SUB_STATES
            }
            if (premiumSub != null) {
                return PremiumStatus(
                    isPremium = true,
                    source = PremiumSource.SUBSCRIPTION,
                    activeProductId = premiumSub.productId,
                    expiresAt = premiumSub.expiresAt,
                    subscriptionState = premiumSub.subscriptionState,
                    showPaymentWarning =
                        premiumSub.subscriptionState == EntitlementEntity.STATE_GRACE_PERIOD,
                )
            }

            return PremiumStatus.FREE
        }

        private val PREMIUM_SUB_STATES = setOf(
            EntitlementEntity.STATE_ACTIVE,
            EntitlementEntity.STATE_CANCELLED,
            EntitlementEntity.STATE_GRACE_PERIOD,
        )
    }
}
