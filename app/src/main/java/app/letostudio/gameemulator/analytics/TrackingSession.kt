package app.letostudio.gameemulator.analytics

import app.letostudio.gameemulator.core.CoreAssets
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.data.db.GameDao
import app.letostudio.gameemulator.data.db.SaveSlotDao
import app.letostudio.gameemulator.data.prefs.SettingsStore
import app.letostudio.gameemulator.data.repository.SettingsRepository
import app.letostudio.gameemulator.monetization.ProGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps analytics user properties (analytics.md §4) and Crashlytics context (§6) in sync for
 * the whole process. Started once from MainApplication.
 */
@Singleton
class TrackingSession @Inject constructor(
    private val analytics: Analytics,
    private val crash: CrashContext,
    private val settingsStore: SettingsStore,
    private val settingsRepository: SettingsRepository,
    private val proGate: ProGate,
    private val gameDao: GameDao,
    private val saveSlotDao: SaveSlotDao,
    private val coreAssets: CoreAssets,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            crash.setUserId(settingsStore.installUuid())
            crash.set(CrashContext.Keys.CORE_VERSION, coreAssets.coreVersion)
            crash.set(CrashContext.Keys.IN_GAME, false)
        }
        scope.launch {
            proGate.state.collect { s ->
                analytics.setUserProperty(AppUserProps.IS_PRO, s.isPro.toString())
                analytics.setUserProperty(AppUserProps.PRO_PLAN, s.plan.analytics.takeIf { s.isPro } ?: "none")
                crash.set(CrashContext.Keys.IS_PRO, s.isPro)
            }
        }
        scope.launch {
            settingsRepository.settings.map { it.themeMode.name.lowercase() }.distinctUntilChanged()
                .collect { analytics.setUserProperty(AppUserProps.THEME_MODE, it) }
        }
        scope.launch {
            gameDao.observeCount().map(AppUserProps::gamesBucket).distinctUntilChanged()
                .collect { analytics.setUserProperty(AppUserProps.GAMES_BUCKET, it) }
        }
        scope.launch {
            saveSlotDao.observeCount().map(AppUserProps::savesBucket).distinctUntilChanged()
                .collect { analytics.setUserProperty(AppUserProps.SAVES_BUCKET, it) }
        }
        scope.launch {
            settingsStore.deviceTier.filterNotNull().distinctUntilChanged().collect {
                analytics.setUserProperty(AppUserProps.DEVICE_TIER, it)
                crash.set(CrashContext.Keys.DEVICE_TIER, it)
            }
        }
        scope.launch {
            settingsStore.usesGamepad.distinctUntilChanged()
                .collect { analytics.setUserProperty(AppUserProps.USES_GAMEPAD, it.toString()) }
        }
    }
}
