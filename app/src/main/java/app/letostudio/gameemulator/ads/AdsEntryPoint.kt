package app.letostudio.gameemulator.ads

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** App-scoped accessor for AppNavHost (same pattern as PipControllerEntryPoint). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AdsEntryPoint {
    fun adsManager(): AdsManager
    fun bannerAdManager(): BannerAdManager
    fun appOpenAdManager(): AppOpenAdManager
    fun nativeAdManager(): NativeAdManager
    fun nativeFullscreenAdManager(): NativeFullscreenAdManager
}
