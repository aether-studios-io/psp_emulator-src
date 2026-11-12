package app.letostudio.gameemulator.ui.paywall

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.AppScreens
import app.letostudio.gameemulator.billing.BillingRepository
import app.letostudio.gameemulator.billing.model.BillingError
import app.letostudio.gameemulator.billing.model.PurchaseResult
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.crash.CrashReporter
import app.letostudio.gameemulator.monetization.PlanCatalog
import app.letostudio.gameemulator.monetization.PlanKind
import app.letostudio.gameemulator.monetization.PlanOption
import app.letostudio.gameemulator.monetization.ProGate
import app.letostudio.gameemulator.monetization.ProPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallUiState(
    val loading: Boolean = true,
    val plans: List<PlanOption> = emptyList(),
    val selected: PlanKind = PlanKind.YEARLY,
    val error: Boolean = false,
    val purchasing: Boolean = false,
    val showLifetimeNote: Boolean = false,
    val hasAutoRenewingSub: Boolean = false,
    val currentPlan: ProPlan = ProPlan.NONE,
) {
    val selectedOption: PlanOption? get() = plans.firstOrNull { it.kind == selected }
}

sealed interface PaywallEvent {
    data object Close : PaywallEvent
    data object Pending : PaywallEvent
    data object Failed : PaywallEvent
    data class OpenUrl(val url: String) : PaywallEvent
}

/** S04 Paywall (F14). No ads on this screen; no Restore button (template restores on resume). */
@HiltViewModel
class RnPaywallViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: PlanCatalog,
    private val billing: BillingRepository,
    private val proGate: ProGate,
    private val analytics: Analytics,
    private val crashReporter: CrashReporter,
    private val crash: CrashContext,
) : ViewModel() {
    val source: String = savedState.get<String>("source")?.takeIf { it.isNotBlank() } ?: "settings"

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()
    private val _events = Channel<PaywallEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var closed = false

    init {
        analytics.screen(AppScreens.PAYWALL)
        analytics.log(AppEvents.PAYWALL_VIEW, mapOf(AppParams.SOURCE to source))
        load()
        viewModelScope.launch {
            billing.purchaseResults.collect(::onPurchaseResult)
        }
    }

    fun load() {
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            val current = proGate.state.first().plan
            catalog.load()
                .onSuccess { plans ->
                    val autoRenew = runCatching { catalog.hasAutoRenewingSubscription() }.getOrDefault(false)
                    _state.update {
                        it.copy(
                            loading = false,
                            plans = plans,
                            selected = if (plans.any { p -> p.kind == PlanKind.YEARLY }) PlanKind.YEARLY else plans.first().kind,
                            hasAutoRenewingSub = autoRenew,
                            currentPlan = current,
                        ).withNote()
                    }
                }
                .onFailure { e ->
                    crash.breadcrumb("billing", "plans load failed")
                    _state.update { it.copy(loading = false, error = true) }
                }
        }
    }

    fun select(kind: PlanKind) {
        if (_state.value.selected == kind) return
        _state.update { it.copy(selected = kind).withNote() }
        analytics.log(AppEvents.PAYWALL_PLAN_SELECT, mapOf(AppParams.PLAN to kind.analytics))
        if (_state.value.showLifetimeNote) {
            analytics.log(AppEvents.PAYWALL_LIFETIME_NOTE_VIEW, mapOf(AppParams.CURRENT_PLAN to _state.value.currentPlan.analytics))
        }
    }

    private fun PaywallUiState.withNote() = copy(showLifetimeNote = selected == PlanKind.LIFETIME && hasAutoRenewingSub)

    fun purchase(activity: Activity) {
        val option = _state.value.selectedOption ?: return
        analytics.log(AppEvents.PAYWALL_CTA_TAP, mapOf(AppParams.PLAN to option.kind.analytics, AppParams.TRIAL to option.offersTrial))
        crash.breadcrumb("billing", "launch ${option.kind.analytics}")
        _state.update { it.copy(purchasing = true) }
        viewModelScope.launch {
            val error = catalog.purchase(activity, option)
            if (error != null) {
                _state.update { it.copy(purchasing = false) }
                reportBillingError(error, option.kind)
                _events.send(PaywallEvent.Failed)
            }
        }
    }

    private suspend fun onPurchaseResult(result: PurchaseResult) {
        val option = _state.value.selectedOption
        val plan = option?.kind?.analytics ?: "unknown"
        _state.update { it.copy(purchasing = false) }
        when (result) {
            is PurchaseResult.Success -> {
                crash.breadcrumb("billing", "purchase ok")
                analytics.log(
                    AppEvents.PURCHASE_SUCCESS,
                    mapOf(
                        AppParams.PLAN to plan,
                        AppParams.PRODUCT_ID to (result.purchase.products.firstOrNull() ?: ""),
                        AppParams.TRIAL to (option?.offersTrial == true),
                        AppParams.SOURCE to source,
                    ),
                )
                closed = true
                _events.send(PaywallEvent.Close)
            }
            PurchaseResult.Cancelled -> analytics.log(AppEvents.PURCHASE_CANCEL, mapOf(AppParams.PLAN to plan))
            PurchaseResult.AlreadyOwned -> {
                closed = true
                _events.send(PaywallEvent.Close)
            }
            is PurchaseResult.Pending -> _events.send(PaywallEvent.Pending)
            is PurchaseResult.Error -> { reportBillingError(result.error, option?.kind); _events.send(PaywallEvent.Failed) }
            is PurchaseResult.NetworkError -> { reportBillingError(result.error, option?.kind); _events.send(PaywallEvent.Failed) }
        }
    }

    private fun reportBillingError(error: BillingError, kind: PlanKind?) {
        val code = error.responseCode
        analytics.log(AppEvents.PURCHASE_FAIL, mapOf(AppParams.CODE to code, AppParams.PLAN to (kind?.analytics ?: "unknown")))
        crashReporter.recordNonFatal("billing", RuntimeException("purchase failed: ${error::class.simpleName}"), mapOf("code" to code.toString()))
    }

    fun close() {
        if (closed) return
        closed = true
        analytics.log(AppEvents.PAYWALL_CLOSE, mapOf(AppParams.SOURCE to source, AppParams.PLAN_SELECTED to _state.value.selected.analytics))
        viewModelScope.launch { _events.send(PaywallEvent.Close) }
    }

    /** Cancel anytime → Play subscriptions (with sku unless Lifetime is selected). */
    fun cancelAnytime() {
        analytics.log(AppEvents.MANAGE_SUBSCRIPTION_TAP, mapOf(AppParams.SOURCE to "paywall"))
        val sku = _state.value.selected.takeIf { it != PlanKind.LIFETIME }?.productId
        viewModelScope.launch { _events.send(PaywallEvent.OpenUrl(playSubscriptionsUrl(sku))) }
    }

    companion object {
        const val PACKAGE = "app.letostudio.gameemulator"

        fun playSubscriptionsUrl(sku: String?): String =
            if (sku == null) {
                "https://play.google.com/store/account/subscriptions?package=$PACKAGE"
            } else {
                "https://play.google.com/store/account/subscriptions?sku=$sku&package=$PACKAGE"
            }
    }
}
