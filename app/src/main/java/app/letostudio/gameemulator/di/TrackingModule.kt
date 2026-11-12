package app.letostudio.gameemulator.di

import app.letostudio.gameemulator.BuildConfig
import app.letostudio.gameemulator.analytics.Analytics
import app.letostudio.gameemulator.analytics.CompositeTracker
import app.letostudio.gameemulator.analytics.DebugTracker
import app.letostudio.gameemulator.analytics.FirebaseTracker
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** RetroNova [Analytics]: Firebase always (consent-gated by the SDK switch), + Logcat in debug. */
@Module
@InstallIn(SingletonComponent::class)
object TrackingModule {

    @Provides
    @Singleton
    fun provideAnalytics(firebase: FirebaseAnalytics): Analytics {
        val firebaseTracker = FirebaseTracker(firebase)
        return if (BuildConfig.DEBUG) CompositeTracker(listOf(firebaseTracker, DebugTracker())) else firebaseTracker
    }
}
