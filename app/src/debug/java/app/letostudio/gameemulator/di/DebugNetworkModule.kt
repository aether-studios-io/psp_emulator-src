package app.letostudio.gameemulator.di

import android.util.Log
import app.letostudio.gameemulator.util.redactSecrets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug builds only (this file lives in src/debug). BASIC level; every line passes
 * through redactSecrets so credentials in query strings never reach logcat.
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugNetworkModule {

    @Provides
    @IntoSet
    @Singleton
    fun provideLoggingInterceptor(): Interceptor =
        HttpLoggingInterceptor { message ->
            Log.d("AppHttp", redactSecrets(message))
        }.setLevel(HttpLoggingInterceptor.Level.BASIC)
}
