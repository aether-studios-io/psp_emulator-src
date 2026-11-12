package app.letostudio.gameemulator.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * ONE OkHttpClient for the whole app — shared by Retrofit and Coil (tech_stacks.md §7).
 * Interceptors arrive via multibinding so the debug-only redacting HTTP logger
 * (src/debug DebugNetworkModule) never leaks into release builds.
 *
 * TEMPLATE: set BASE_URL to the app's API root and add one @Provides per Retrofit
 * service. Keep the single shared client — a second OkHttpClient means a second
 * connection pool and a second thread pool for no benefit.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Multibinds
    abstract fun networkInterceptors(): Set<Interceptor>

    companion object {

        // Retrofit requires a baseUrl even when every call passes a full @Url.
        private const val BASE_URL = "http://localhost/"

        @Provides
        @Singleton
        fun provideOkHttpClient(interceptors: Set<@JvmSuppressWildcards Interceptor>): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .apply { interceptors.forEach(::addInterceptor) }
                .build()

        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }

        @Provides
        @Singleton
        fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

        @Provides
        @Singleton
        fun provideImageLoader(
            @ApplicationContext context: Context,
            client: OkHttpClient,
        ): ImageLoader =
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
                .crossfade(true)
                .build()
    }
}
