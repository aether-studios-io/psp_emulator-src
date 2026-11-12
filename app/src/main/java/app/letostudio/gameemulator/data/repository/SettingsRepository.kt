package app.letostudio.gameemulator.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.letostudio.gameemulator.domain.model.AppSettings
import app.letostudio.gameemulator.domain.model.ThemeSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The app's preference store. TEMPLATE: everything here is app-agnostic — theme, the
 * one-shot first-run flags, the analytics opt-out and the license-agreement consent.
 * Add feature preferences as a new key + a field on [AppSettings] + a setter.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val tutorialSeen = booleanPreferencesKey("tutorial_seen")
        val onboardingSeen = booleanPreferencesKey("onboarding_seen")
        val analyticsOptOut = booleanPreferencesKey("analytics_opt_out")
        val licenseAgreed = booleanPreferencesKey("license_agreed")
    }

    private val defaults = AppSettings()

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .map { prefs ->
            AppSettings(
                themeMode = prefs[Keys.themeMode].toEnum(defaults.themeMode),
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(value: ThemeSetting) =
        context.settingsDataStore.edit { it[Keys.themeMode] = value.name }

    /** Whether the in-app tutorial hint was acknowledged (opened or dismissed). */
    val tutorialSeen: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.tutorialSeen] ?: false }
        .distinctUntilChanged()

    suspend fun setTutorialSeen() =
        context.settingsDataStore.edit { it[Keys.tutorialSeen] = true }

    /**
     * Whether the first-launch onboarding pager already ran. Distinct from
     * [tutorialSeen], which is the in-app hint on a specific screen.
     */
    val onboardingSeen: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.onboardingSeen] ?: false }
        .distinctUntilChanged()

    suspend fun setOnboardingSeen() =
        context.settingsDataStore.edit { it[Keys.onboardingSeen] = true }

    /**
     * Local analytics opt-out. `false` means "not opted out" — it does NOT mean consent
     * was granted; the consent gate is independent and ANDed in AnalyticsManager. The
     * opt-out can only ever subtract collection, never add.
     */
    val analyticsOptOut: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.analyticsOptOut] ?: false }
        .distinctUntilChanged()

    suspend fun setAnalyticsOptOut(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.analyticsOptOut] = value }

    /**
     * Whether the user accepted the license agreement / content disclaimer. Gate this
     * on whatever user-supplied content the app accepts; agreeing once covers every
     * entry point, and the toggle on the License Agreement screen shares the flag.
     */
    val licenseAgreed: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[Keys.licenseAgreed] ?: false }
        .distinctUntilChanged()

    suspend fun setLicenseAgreed(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.licenseAgreed] = value }
}

// Unknown stored values (e.g. after a downgrade) fall back to the default instead of crashing.
private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
