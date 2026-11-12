package app.letostudio.gameemulator.domain.model

import app.letostudio.gameemulator.billing.model.BillingPeriod

/** Display model for one selectable paywall plan, built from Play ProductDetails (FR-042). */
data class SubscriptionPlan(
    val productId: String,
    /** Base plan ID for subscriptions; null for the lifetime one-time product. */
    val basePlanId: String?,
    /** Offer token required to launch a subscription purchase; null for INAPP. */
    val offerToken: String?,
    val name: String,
    val billingPeriod: BillingPeriod,
    /** Localized price string straight from Google Play — never hardcoded (SC-010). */
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
    val isBestOffer: Boolean = false,
    val isCurrentPlan: Boolean = false,
    /** True when the selected Play offer starts with a free-trial pricing phase. */
    val hasFreeTrial: Boolean = false,
    /**
     * Local hint mirroring Play's own eligibility rule: false once this install has any
     * recorded or active purchase (a user who bought monthly cannot trial weekly). Play
     * enforces the real rule at checkout; this only keeps the trial copy honest when
     * Play still returns a free phase to a returning subscriber.
     */
    val isTrialEligible: Boolean = true,
) {
    /** The card sells the free trial only when Play offers it AND this user can use it. */
    val offersTrial: Boolean get() = hasFreeTrial && isTrialEligible

    /** Stable selection key — product + base plan uniquely identify a card. */
    val key: String get() = "$productId:${basePlanId.orEmpty()}"
}
