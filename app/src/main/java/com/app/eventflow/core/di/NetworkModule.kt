package com.app.eventflow.core.di

import com.app.eventflow.BuildConfig
import com.app.eventflow.core.network.AuthInterceptor
import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.core.network.TokenAuthenticator
import com.app.eventflow.core.security.TokenStore
import com.app.eventflow.data.remote.api.AuthApi
import com.app.eventflow.data.remote.api.CatalogApi
import com.app.eventflow.data.remote.api.CheckInApi
import com.app.eventflow.data.remote.api.OrdersApi
import com.app.eventflow.data.remote.api.RefundApi
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // OkHttp deja callTimeout ilimitado por defecto; límites explícitos (kotlin/security.md)
    private const val CONNECT_TIMEOUT_S = 10L
    private const val READ_TIMEOUT_S = 30L
    private const val WRITE_TIMEOUT_S = 30L
    private const val CALL_TIMEOUT_S = 45L

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true   // tolerant reader (docs/api/06 §5)
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun problemConverter(json: Json): ProblemConverter = ProblemConverter(json)

    /** Cliente/Retrofit SIN authenticator, exclusivo para /auth/refresh (evita recursión). */
    @Provides
    @Singleton
    @RefreshClient
    fun refreshRetrofit(json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(
            OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
                .build()
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    @RefreshClient
    fun refreshAuthApi(@RefreshClient retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun okHttpClient(
        tokenStore: TokenStore,
        @RefreshClient refreshApi: Provider<AuthApi>,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(tokenStore))
        .authenticator(TokenAuthenticator(tokenStore, refreshApi))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun catalogApi(retrofit: Retrofit): CatalogApi = retrofit.create(CatalogApi::class.java)

    @Provides
    @Singleton
    fun ordersApi(retrofit: Retrofit): OrdersApi = retrofit.create(OrdersApi::class.java)

    @Provides
    @Singleton
    fun checkInApi(retrofit: Retrofit): CheckInApi = retrofit.create(CheckInApi::class.java)

    @Provides
    @Singleton
    fun refundApi(retrofit: Retrofit): RefundApi = retrofit.create(RefundApi::class.java)
}
