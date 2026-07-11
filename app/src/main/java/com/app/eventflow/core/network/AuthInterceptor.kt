package com.app.eventflow.core.network

import com.app.eventflow.core.security.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/** Agrega Bearer (salvo /auth públicos) y X-Correlation-ID a todo request. */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("X-Correlation-ID", UUID.randomUUID().toString())

        val isPublicAuth = original.url.encodedPath.let {
            it.endsWith("/auth/register") || it.endsWith("/auth/login") || it.endsWith("/auth/refresh")
        }
        if (!isPublicAuth) {
            tokenStore.accessToken()?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }
}
