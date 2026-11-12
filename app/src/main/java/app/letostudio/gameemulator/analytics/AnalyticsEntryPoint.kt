package app.letostudio.gameemulator.analytics

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt access for the ONE composable-layer analytics call site — AppNavHost's
 * screen_view tracking (specs/006-analytics R5). Mirrors ads/AdsEntryPoint. Any other
 * ui/ file reaching for this trips the Konsist rule.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AnalyticsEntryPoint {
    fun analyticsManager(): AnalyticsManager
}
