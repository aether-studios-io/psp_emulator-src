package app.letostudio.gameemulator.billing

import app.letostudio.gameemulator.billing.model.BillingPeriod
import app.letostudio.gameemulator.domain.model.SubscriptionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Paywall order rule (the IPTV app's): every plan Play returns is listed. Trial
 * eligibility is Play's decision plus a local purchase-history hint, so the only thing
 * that changes is the order — the trial leads while it is usable, the best-value plan
 * leads otherwise, and the trial product stays on the list as a plain plan.
 */
class PaywallPlanSelectionTest {

    private fun plan(
        productId: String,
        period: BillingPeriod,
        hasFreeTrial: Boolean = false,
        isTrialEligible: Boolean = true,
    ) = SubscriptionPlan(
        productId = productId,
        basePlanId = "base",
        offerToken = "token",
        name = productId,
        billingPeriod = period,
        formattedPrice = "$1.99",
        priceMicros = 1_990_000,
        currencyCode = "USD",
        hasFreeTrial = hasFreeTrial,
        isTrialEligible = isTrialEligible,
    )

    private val trial = plan(TRIAL_ID, BillingPeriod.WEEKLY, hasFreeTrial = true)
    private val trialWithoutFreePhase = plan(TRIAL_ID, BillingPeriod.WEEKLY)
    private val trialForReturningUser =
        plan(TRIAL_ID, BillingPeriod.WEEKLY, hasFreeTrial = true, isTrialEligible = false)
    private val mid = plan(MONTHLY_ID, BillingPeriod.MONTHLY)
    private val best = plan(YEARLY_ID, BillingPeriod.YEARLY)

    @Test
    fun `usable free trial leads, then the remaining plans in catalogue order`() {
        val visible = BillingRepository.selectPaywallPlans(listOf(best, mid, trial), ORDER)
        assertEquals(
            listOf(trial.productId, mid.productId, best.productId),
            visible.map { it.productId },
        )
    }

    @Test
    fun `Play withheld the free phase - trial product stays listed as a plain plan, best value leads`() {
        // Play Console "new customer acquisition" eligibility: returning subscribers get
        // the trial product back without its free pricing phase.
        val visible = BillingRepository.selectPaywallPlans(listOf(trialWithoutFreePhase, mid, best), ORDER)
        assertEquals(
            listOf(best.productId, mid.productId, trialWithoutFreePhase.productId),
            visible.map { it.productId },
        )
        assertFalse(visible.last().offersTrial)
    }

    @Test
    fun `user who purchased before - trial product stays listed but does not sell the trial`() {
        // Play still returned a free phase, the local history says this user already
        // bought a plan: no trial copy, best value leads, the plan remains purchasable.
        val visible = BillingRepository.selectPaywallPlans(listOf(trialForReturningUser, mid, best), ORDER)
        assertEquals(
            listOf(best.productId, mid.productId, trialForReturningUser.productId),
            visible.map { it.productId },
        )
        assertFalse(visible.last().offersTrial)
    }

    @Test
    fun `no trial product configured falls back to best-value-first order`() {
        val visible = BillingRepository.selectPaywallPlans(listOf(mid, best), ORDER)
        assertEquals(listOf(best.productId, mid.productId), visible.map { it.productId })
    }

    @Test
    fun `unknown products sort last in either order`() {
        val unknown = plan("com.example.unknown", BillingPeriod.MONTHLY)
        assertEquals(
            listOf(trial.productId, mid.productId, best.productId, unknown.productId),
            BillingRepository.selectPaywallPlans(listOf(unknown, best, mid, trial), ORDER).map { it.productId },
        )
    }

    private companion object {
        // The template's ordering rule, exercised with a generic 3-product store
        // (RetroNova's own catalogue is one subscription + lifetime, see PlanCatalogTest).
        const val TRIAL_ID = "test.weeklytrial"
        const val MONTHLY_ID = "test.monthly1"
        const val YEARLY_ID = "test.yearly1"
        val ORDER = listOf(TRIAL_ID, MONTHLY_ID, YEARLY_ID)
    }
}
