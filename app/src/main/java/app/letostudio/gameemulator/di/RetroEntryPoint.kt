package app.letostudio.gameemulator.di

import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.crash.CrashContext
import app.letostudio.gameemulator.data.prefs.AppRemoteFlags
import app.letostudio.gameemulator.monetization.ProGate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Singletons the navigation shell needs outside a ViewModel (screen_view, crash keys). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RetroEntryPoint {
    fun analytics(): Analytics
    fun crashContext(): CrashContext
    fun proGate(): ProGate
    fun remoteFlags(): AppRemoteFlags
}
