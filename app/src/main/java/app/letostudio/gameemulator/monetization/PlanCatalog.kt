package app.letostudio.gameemulator.monetization

import android.app.Activity
import app.letostudio.gameemulator.billing.BillingClientWrapper
import app.letostudio.gameemulator.billing.BillingConfig
import app.letostudio.gameemulator.billing.BillingRepository
import app.letostudio.gameemulator.billing.StoreConfig
import app.letostudio.gameemulator.billing.model.BillingError
import app.letostudio.gameemulator.billing.model.BillingException
import app.letostudio.gameemulator.domain.model.SubscriptionPlan
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** The three RetroNova plans (features.md › Pricing). */
enum class PlanKind(val analytics: String, val productId: String) {
    YEARLY("yearly", BillingConfig.PRO_YEARLY),
    MONTHLY("monthly", BillingConfig.PRO_MONTHLY),
    LIFETIME("lifetime", BillingConfig.PRO_LIFETIME),
    ;

    companion object {
        fun ofProduct(productId: String): PlanKind? = entries.firstOrNull { it.productId == productId }
    }
}

/** One paywall card: the template plan model plus which RetroNova plan it is. */
data class PlanOption(val kind: PlanKind, val plan: SubscriptionPlan) {
    val offersTrial: Boolean get() = plan.offersTrial
}

/**
 * Thin adapter over the template billing module (TEMPLATE_NOTES › Billing). Each plan is its
 * own Play product (`gameemulator_pro_yearly` / `_monthly` / `_lifetime`), which is exactly
 * what the template builds one card for, so this only tags the template's plans with a
 * [PlanKind] and fixes the order Yearly → Monthly → Lifetime (screens.md S04).
 *
 * Never edits template billing internals (spec CLAUDE.md §3).
 */
@Singleton
class PlanCatalog @Inject constructor(
    private val billing: BillingRepository,
    private val clientWrapper: BillingClientWrapper,
) {
    suspend fun load(): Result<List<PlanOption>> {
        val templatePlans = billing.loadPaywallPlans(StoreConfig.PAYWALL).getOrElse { return Result.failure(it) }
        val all = options(templatePlans)
        return if (all.isEmpty()) Result.failure(BillingException(BillingError.ItemUnavailable)) else Result.success(all)
    }

    /** Launches Google Play's purchase sheet; outcome arrives on [BillingRepository.purchaseResults]. */
    suspend fun purchase(activity: Activity, option: PlanOption): BillingError? =
        billing.launchPurchase(activity, option.plan)

    /** Lifetime note rule (screens.md S04): a SUBS purchase that still auto-renews. */
    suspend fun hasAutoRenewingSubscription(): Boolean {
        if (clientWrapper.ensureConnected().responseCode != BillingResponseCode.OK) return false
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        val purchases = suspendCancellableCoroutine<List<Purchase>> { cont ->
            clientWrapper.client.queryPurchasesAsync(params) { result, list ->
                if (cont.isActive) cont.resume(if (result.responseCode == BillingResponseCode.OK) list else emptyList())
            }
        }
        return purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED && it.isAutoRenewing }
    }

    companion object {
        /** Template plans → RetroNova options, Yearly first; unknown products are dropped. */
        internal fun options(plans: List<SubscriptionPlan>): List<PlanOption> =
            plans.mapNotNull { p -> PlanKind.ofProduct(p.productId)?.let { PlanOption(it, p) } }
                .sortedBy { it.kind.ordinal }
    }
}
