package app.letostudio.gameemulator.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides [FirebaseAnalytics] as an injected dependency rather than letting
 * AnalyticsManager reach for the Firebase singleton itself — this boundary is what
 * makes AnalyticsManagerGateTest possible (the SDK is external and uncontrollable,
 * so it is faked exactly here; same treatment AdsManagerGateTest applies).
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)
}
