package app.letostudio.gameemulator.di

import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.letostudio.gameemulator.crash.CrashReporter
import app.letostudio.gameemulator.crash.CrashReportingManager
import javax.inject.Singleton

/**
 * Provides [FirebaseCrashlytics] as an injected dependency (the AnalyticsModule reason:
 * the SDK is faked exactly here in CrashReportingManagerTest) and binds the
 * [CrashReporter] seam to the one consent-gated implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CrashModule {

    @Binds
    abstract fun bindCrashReporter(impl: CrashReportingManager): CrashReporter

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
    }
}
