package app.letostudio.gameemulator.billing

import app.letostudio.gameemulator.BuildConfig
import app.letostudio.gameemulator.billing.model.BillingPeriod
import com.android.billingclient.api.BillingFlowParams
import java.util.concurrent.ConcurrentHashMap

/**
 * Single configuration point for the billing integration (FR-029): the in-code product
 * catalogue. Which products each purchase screen lists, in which order and which one is
 * "Best value" comes from Remote Config `subscription_stores_config` ([StoreRemoteConfig],
 * KAN-151); this catalogue is its fallback (blank or unreadable value, fresh install) and
 * the Remote Config in-code default. To reuse this module in another app, change only
 * this file (plus the products in Google Play Console and the console value).
 *
 * Play Console setup expected by these defaults: three standalone subscription
 * products — weekly trial (weekly base plan with a free-trial offer), monthly and
 * yearly. No one-time products are configured; [INAPP_PRODUCT_IDS] may be filled in
 * later (e.g. a lifetime product) without touching billing logic (FR-030).
 *
 * Trial handling (the IPTV app's rule): every configured product is always listed.
 * Configure the trial offer in Play Console with "New customer acquisition"
 * eligibility — Play then returns the trial product without its free pricing phase to
 * users who already had a subscription, and the paywall shows it as a plain weekly plan
 * ([app.letostudio.gameemulator.domain.model.SubscriptionPlan.offersTrial] drives the
 * badge, period copy and CTA). The app adds a local hint from its purchase history so
 * the trial copy stays honest even if Play still hands a free phase back. There is no
 * separate non-trial weekly product and nothing is hidden.
 */
object BillingConfig {

    // RetroNova catalogue (features.md > Pricing, owner change 2026-11-12): one product per
    // plan — two subscriptions (`..._yearly` base plan `yearly` + offer `trial-3d`;
    // `..._monthly` base plan `monthly`) and one non-consumable lifetime product.
    const val PRO_YEARLY = "gameemulator_pro_yearly"
    const val PRO_MONTHLY = "gameemulator_pro_monthly"
    const val PRO_LIFETIME = "gameemulator_pro_lifetime"
    const val BASE_PLAN_YEARLY = "yearly"
    const val BASE_PLAN_MONTHLY = "monthly"
    const val OFFER_TRIAL = "trial-3d"

    /** The product carrying the free-trial offer (read by StoreConfigParser's default). */
    const val PRODUCT_WEEKLY_TRIAL = PRO_YEARLY

    /** All subscription product IDs to query, in paywall display order. */
    val SUBS_PRODUCT_IDS: List<String> = listOf(PRO_YEARLY, PRO_MONTHLY)

    /** All one-time (INAPP) product IDs to query. */
    val INAPP_PRODUCT_IDS: List<String> = listOf(PRO_LIFETIME)

    /** Subset of [INAPP_PRODUCT_IDS] that are consumable. Lifetime is not. */
    val CONSUMABLE_PRODUCT_IDS: Set<String> = emptySet()

    /** Product highlighted with the "Best value" badge. */
    const val BEST_VALUE_PRODUCT_ID = PRO_YEARLY

    /** Billing cadence per product. */
    fun periodOf(productId: String): BillingPeriod? = when (productId) {
        PRO_YEARLY -> BillingPeriod.YEARLY
        PRO_MONTHLY -> BillingPeriod.MONTHLY
        PRO_LIFETIME -> BillingPeriod.LIFETIME
        else -> null
    }

    /** Replacement mode applied when switching between subscription plans (FR-016). */
    const val SUBSCRIPTION_REPLACEMENT_MODE =
        BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION

    /** Delay before the paywall close button becomes visible (FR-036). */
    const val PAYWALL_CLOSE_DELAY_MS = 0L // RetroNova S04: close is always available

    /**
     * How many of the app's metered items a free user may create/import. A metered
     * gate shows the paywall only on the action that would EXCEED the limit; an
     * outright premium feature should be gated with the entitlement check instead.
     */
    const val FREE_ITEM_LIMIT = 3 // RetroNova: 3 manual save slots per game for Free

    /**
     * Paywall footer links (FR-045). TEMPLATE: these must be real, reachable URLs
     * before the first Play upload — Play rejects a subscription paywall without
     * working Terms and Privacy links, and the Data safety form is checked against
     * the privacy policy.
     */
    const val PRIVACY_POLICY_URL = "https://letostudio.app/legal/privacy"
    const val TERMS_OF_SERVICE_URL = "https://letostudio.app/legal/terms"

    /** Play's subscription management page for this app (FR-044). */
    fun manageSubscriptionUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions?package=$packageName"

    fun isConsumable(productId: String): Boolean = productId in CONSUMABLE_PRODUCT_IDS

    /**
     * Subscription products Play reported at runtime (product details or SUBS purchase
     * queries) that this catalogue does not list: products added only through Remote
     * Config `subscription_stores_config`. Keeps their entitlements typed as subscriptions.
     */
    private val runtimeSubscriptionIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun recordSubscription(productId: String) {
        if (productId !in SUBS_PRODUCT_IDS) runtimeSubscriptionIds += productId
    }

    fun isSubscription(productId: String): Boolean =
        productId in SUBS_PRODUCT_IDS || productId in runtimeSubscriptionIds
}
