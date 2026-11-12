package app.letostudio.gameemulator

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class MainApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    // Provider keeps ImageLoader (and the OkHttpClient behind it) lazy — no network
    // setup on the cold-start path.
    @Inject
    lateinit var imageLoader: Provider<ImageLoader>

    // WorkManager on-demand init with Hilt-aware worker creation (@HiltWorker workers).
    // The default initializer is removed in AndroidManifest.xml.
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // RetroNova: user properties + Crashlytics context (analytics.md §4, §6).
    @Inject
    lateinit var trackingSession: Provider<app.letostudio.gameemulator.analytics.TrackingSession>

    override fun onCreate() {
        super.onCreate()
        trackingSession.get().start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader.get()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
