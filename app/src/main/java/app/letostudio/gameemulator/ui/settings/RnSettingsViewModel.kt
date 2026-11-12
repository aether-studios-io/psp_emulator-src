package app.letostudio.gameemulator.ui.settings

import android.app.Activity
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.BuildConfig
import app.letostudio.gameemulator.ads.ConsentManager
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AnalyticsManager
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.AppUserProps
import app.letostudio.gameemulator.billing.BillingConfig
import app.letostudio.gameemulator.billing.BillingRepository
import app.letostudio.gameemulator.billing.model.PurchaseResult
import app.letostudio.gameemulator.data.library.LibraryRepository
import app.letostudio.gameemulator.data.prefs.EmulatorDefaults
import app.letostudio.gameemulator.data.prefs.SettingsStore
import app.letostudio.gameemulator.data.repository.SettingsRepository
import app.letostudio.gameemulator.domain.model.ThemeSetting
import app.letostudio.gameemulator.input.GamepadManager
import app.letostudio.gameemulator.monetization.NativePlacement
import app.letostudio.gameemulator.monetization.PlacementNativeAds
import app.letostudio.gameemulator.monetization.PlanCatalog
import app.letostudio.gameemulator.monetization.PlanKind
import app.letostudio.gameemulator.monetization.ProGate
import app.letostudio.gameemulator.monetization.ProPlan
import app.letostudio.gameemulator.monetization.ProState
import app.letostudio.gameemulator.ui.paywall.RnPaywallViewModel
import com.google.android.gms.ads.nativead.NativeAd
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val pro: ProState = ProState.FREE,
    val theme: ThemeSetting = ThemeSetting.LIGHT,
    val folderName: String? = null,
    val emu: EmulatorDefaults = EmulatorDefaults(),
    val padName: String? = null,
    val privacyOptionsRequired: Boolean = false,
    val version: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    val switching: Boolean = false,
)

sealed interface SettingsEvent {
    data class OpenUrl(val url: String) : SettingsEvent
    data class Email(val to: String, val subjectRes: Int, val body: String) : SettingsEvent
    data object Rate : SettingsEvent
    data object DebugUnlocked : SettingsEvent
    data object OpenDebug : SettingsEvent
    data object SwitchFailed : SettingsEvent
}

/** S13 / S14 Settings (F15). N4 native ad is Free only. */
@HiltViewModel
class RnSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val settingsStore: SettingsStore,
    private val library: LibraryRepository,
    private val proGate: ProGate,
    private val catalog: PlanCatalog,
    private val billing: BillingRepository,
    private val consentManager: ConsentManager,
    private val analyticsManager: AnalyticsManager,
    private val analytics: Analytics,
    gamepads: GamepadManager,
    private val nativeAds: PlacementNativeAds,
) : ViewModel() {

    val ad: StateFlow<NativeAd?> = nativeAds.ad(NativePlacement.SETTINGS)
    private val switching = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        combine(proGate.state, settingsRepository.settings, library.folderUri) { p, s, f -> Triple(p, s.themeMode, f) },
        settingsStore.emulatorDefaults,
        gamepads.connected,
        consentManager.privacyOptionsRequired,
        switching,
    ) { (pro, theme, folder), emu, pads, privacy, sw ->
        SettingsUiState(
            pro = pro,
            theme = theme,
            folderName = library.folderDisplayName(folder),
            emu = emu,
            padName = pads.firstOrNull()?.name,
            privacyOptionsRequired = privacy,
            switching = sw,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var versionTaps = 0
    private var pendingSwitch: Pair<String, String>? = null

    init {
        viewModelScope.launch {
            proGate.state.collect { if (!it.isPro) nativeAds.request(NativePlacement.SETTINGS) }
        }
        viewModelScope.launch {
            billing.purchaseResults.collect { r ->
                val sw = pendingSwitch ?: return@collect
                switching.value = false
                if (r is PurchaseResult.Success) {
                    analytics.log(AppEvents.CHANGE_PLAN_SUCCESS, mapOf(AppParams.FROM to sw.first, AppParams.TO to sw.second))
                } else if (r !is PurchaseResult.Cancelled && r !is PurchaseResult.Pending) {
                    _events.send(SettingsEvent.SwitchFailed)
                }
                pendingSwitch = null
            }
        }
    }

    fun setTheme(t: ThemeSetting) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(t)
            val wire = t.name.lowercase()
            analytics.log(AppEvents.THEME_CHANGE, mapOf(AppParams.MODE to wire))
            analytics.setUserProperty(AppUserProps.THEME_MODE, wire)
        }
    }

    /** Monthly ↔ Yearly (screens.md: SubscriptionUpdateParams via the template's launch). */
    fun switchPlan(activity: Activity) {
        val from = state.value.pro.plan
        val target = if (from == ProPlan.MONTHLY) PlanKind.YEARLY else PlanKind.MONTHLY
        analytics.log(AppEvents.CHANGE_PLAN_START, mapOf(AppParams.FROM to from.analytics, AppParams.TO to target.analytics))
        switching.value = true
        viewModelScope.launch {
            val option = catalog.load().getOrNull()?.firstOrNull { it.kind == target }
            if (option == null) {
                switching.value = false
                _events.send(SettingsEvent.SwitchFailed)
                return@launch
            }
            pendingSwitch = from.analytics to target.analytics
            val err = catalog.purchase(activity, option)
            if (err != null) {
                switching.value = false
                pendingSwitch = null
                _events.send(SettingsEvent.SwitchFailed)
            }
        }
    }

    fun manageSubscription() {
        analytics.log(AppEvents.MANAGE_SUBSCRIPTION_TAP, mapOf(AppParams.SOURCE to "settings"))
        viewModelScope.launch { _events.send(SettingsEvent.OpenUrl(RnPaywallViewModel.playSubscriptionsUrl(ProGate.productOf(state.value.pro.plan)))) }
    }

    fun rate() {
        analytics.log(AppEvents.RATE_TAP)
        viewModelScope.launch { _events.send(SettingsEvent.Rate) }
    }

    fun contact() {
        analytics.log(AppEvents.CONTACT_US_TAP)
        val s = state.value
        val plan = if (s.pro.isPro) "Premium (${s.pro.plan.analytics})" else "Free"
        val body = "\n\n—\nApp: RetroNova ${s.version}\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE}\nPlan: $plan"
        viewModelScope.launch { _events.send(SettingsEvent.Email(SUPPORT_EMAIL, app.letostudio.gameemulator.R.string.settings_contact_subject, body)) }
    }

    fun openPrivacy() = viewModelScope.launch { _events.send(SettingsEvent.OpenUrl(BillingConfig.PRIVACY_POLICY_URL)) }
    fun openTerms() = viewModelScope.launch { _events.send(SettingsEvent.OpenUrl(BillingConfig.TERMS_OF_SERVICE_URL)) }

    fun privacyOptions(activity: Activity) {
        analytics.log(AppEvents.PRIVACY_OPTIONS_OPEN)
        consentManager.showPrivacyOptionsForm(activity) { analyticsManager.onPrivacyOptionsFormDismissed() }
    }

    fun onOssOpened() = analytics.log(AppEvents.OSS_LICENSES_OPEN)

    /** Version tapped 7× → hidden debug screen (debug builds, or release with the RC flag). */
    fun onVersionTap(debugAllowed: Boolean) {
        if (!debugAllowed) return
        versionTaps++
        viewModelScope.launch {
            if (settingsStore.debugUnlocked.first()) {
                if (versionTaps >= 7) { versionTaps = 0; _events.send(SettingsEvent.OpenDebug) }
                return@launch
            }
            if (versionTaps >= 7) {
                versionTaps = 0
                settingsStore.unlockDebug()
                _events.send(SettingsEvent.DebugUnlocked)
                _events.send(SettingsEvent.OpenDebug)
            }
        }
    }

    companion object {
        const val SUPPORT_EMAIL = "admin@letostudio.app"
    }
}
