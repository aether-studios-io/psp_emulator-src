package app.letostudio.gameemulator.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.AppEvents
import app.letostudio.gameemulator.analytics.AppParams
import app.letostudio.gameemulator.analytics.AppScreens
import app.letostudio.gameemulator.data.prefs.SettingsStore
import app.letostudio.gameemulator.data.repository.SettingsRepository
import app.letostudio.gameemulator.monetization.PlanCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** S01–S03 (F01). No ads. Finishing or Skip persists the flag; the paywall follows once. */
@HiltViewModel
class RnOnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val settingsStore: SettingsStore,
    private val analytics: Analytics,
    planCatalog: PlanCatalog,
) : ViewModel() {
    private var lastLogged = -1

    init {
        // The paywall follows onboarding: fetch its prices while the user reads the pages.
        planCatalog.prefetch()
        viewModelScope.launch {
            if (settingsStore.markTutorialBegin()) analytics.log(AppEvents.TUTORIAL_BEGIN)
        }
    }

    /** [page] 0-based; logs screen_view onboarding_N + onboarding_step once per page view. */
    fun onPageShown(page: Int) {
        if (page == lastLogged) return
        lastLogged = page
        analytics.screen(listOf(AppScreens.ONBOARDING_1, AppScreens.ONBOARDING_2, AppScreens.ONBOARDING_3)[page])
        analytics.log(AppEvents.ONBOARDING_STEP, mapOf(AppParams.STEP to page + 1))
    }

    fun onSkip(page: Int, done: () -> Unit) {
        analytics.log(AppEvents.ONBOARDING_SKIP, mapOf(AppParams.STEP to page + 1))
        finish(done)
    }

    fun onGetStarted(done: () -> Unit) {
        analytics.log(AppEvents.TUTORIAL_COMPLETE)
        finish(done)
    }

    private fun finish(done: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOnboardingSeen()
            done()
        }
    }
}
