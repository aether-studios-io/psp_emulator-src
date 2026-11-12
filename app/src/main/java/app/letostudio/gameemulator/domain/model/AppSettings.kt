package app.letostudio.gameemulator.domain.model

enum class ThemeSetting { LIGHT, DARK, SYSTEM }

/**
 * App-wide preferences persisted in DataStore (see SettingsRepository).
 *
 * TEMPLATE: only the settings every app needs live here. Add feature preferences as
 * new fields plus a matching key + setter in SettingsRepository — the Settings screen
 * reads this one object, so a new field shows up there with no plumbing.
 */
data class AppSettings(
    val themeMode: ThemeSetting = ThemeSetting.LIGHT,
)
