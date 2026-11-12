package app.letostudio.gameemulator.domain.model

/** What grants the user premium access. */
enum class PremiumSource { SUBSCRIPTION, LIFETIME }

/**
 * Derived premium state (data-model.md): drives paywall visibility on cold start.
 * Premium = active/cancelled/grace-period subscription OR lifetime purchase.
 * On-hold, paused, expired and revoked subscriptions are NOT premium (FR-035).
 */
data class PremiumStatus(
    val isPremium: Boolean,
    val source: PremiumSource? = null,
    val activeProductId: String? = null,
    val expiresAt: Long? = null,
    val subscriptionState: String? = null,
    /** True while a grace-period payment issue should be surfaced to the user. */
    val showPaymentWarning: Boolean = false,
) {
    companion object {
        val FREE = PremiumStatus(isPremium = false)
    }
}
