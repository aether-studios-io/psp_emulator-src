package app.letostudio.gameemulator.monetization

import app.letostudio.gameemulator.billing.BillingConfig
import app.letostudio.gameemulator.billing.BillingRepository
import app.letostudio.gameemulator.domain.model.PremiumSource
import app.letostudio.gameemulator.domain.model.PremiumStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

/** Which plan the user holds. [SUBSCRIPTION] = Pro from a product this build does not know (e.g. added via Remote Config). */
enum class ProPlan(val analytics: String) {
    NONE("none"), MONTHLY("monthly"), YEARLY("yearly"), LIFETIME("lifetime"), SUBSCRIPTION("yearly");
}

data class ProState(val isPro: Boolean, val plan: ProPlan) {
    companion object {
        val FREE = ProState(false, ProPlan.NONE)
    }
}

/**
 * Wraps the template's premium state (`BillingRepository.premiumStatus`) into RetroNova's
 * Pro state. Everything that changes with Pro (ads, save-slot limit, Pro settings rows, plan
 * card) reads [state] — never billing directly.
 */
@Singleton
class ProGate @Inject constructor(
    billing: BillingRepository,
) {
    val state: Flow<ProState> = billing.premiumStatus.map { derive(it) }.distinctUntilChanged()

    suspend fun isPro(): Boolean = state.first().isPro

    companion object {
        fun derive(status: PremiumStatus): ProState {
            if (!status.isPremium) return ProState.FREE
            val plan = when {
                status.source == PremiumSource.LIFETIME ||
                    status.activeProductId == BillingConfig.PRO_LIFETIME -> ProPlan.LIFETIME
                status.activeProductId == BillingConfig.PRO_MONTHLY -> ProPlan.MONTHLY
                status.activeProductId == BillingConfig.PRO_YEARLY -> ProPlan.YEARLY
                else -> ProPlan.SUBSCRIPTION
            }
            return ProState(true, plan)
        }

        /** Play subscription product behind [plan], for "Manage subscription" deep links. */
        fun productOf(plan: ProPlan): String? = when (plan) {
            ProPlan.MONTHLY -> BillingConfig.PRO_MONTHLY
            ProPlan.YEARLY -> BillingConfig.PRO_YEARLY
            else -> null
        }

        /** Free users keep 3 manual slots per game; Pro is unlimited (F10). */
        const val FREE_SAVE_SLOTS = 3
    }
}
