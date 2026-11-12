package app.letostudio.gameemulator.ui.navigation

/** RetroNova routes (screens.md › Navigation graph). Pause is an overlay inside `player`. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val PAYWALL = "paywall?source={source}"
    const val LIBRARY = "library"
    const val IMPORT = "import"
    const val GAME = "game/{gameId}"
    const val HOMEBREW = "homebrew"
    const val HOMEBREW_GUIDE = "homebrew-guide"
    const val PLAYER = "player/{gameId}?slot={slot}&source={source}"
    const val SETTINGS = "settings"
    const val EMU_SETTINGS = "emulator-settings?gameId={gameId}"
    const val CONTROLS = "controls?gameId={gameId}"
    const val OSS = "oss-licenses"
    const val DEBUG = "debug"

    fun paywall(source: String) = "paywall?source=$source"
    fun game(id: Long) = "game/$id"
    fun player(id: Long, slot: Int?, source: String) = "player/$id?slot=${slot ?: -1}&source=$source"
    fun emuSettings(gameId: Long?) = "emulator-settings?gameId=${gameId ?: -1}"
    fun controls(gameId: Long?) = "controls?gameId=${gameId ?: -1}"

    /** Tab roots — the bottom bar shows only on these. */
    val tabRoutes = setOf(LIBRARY, HOMEBREW, SETTINGS)

    /** Every destination, for ScreenNamesTest. */
    val all = listOf(ONBOARDING, PAYWALL, LIBRARY, IMPORT, GAME, HOMEBREW, HOMEBREW_GUIDE, PLAYER, SETTINGS, EMU_SETTINGS, CONTROLS, OSS, DEBUG)
}
