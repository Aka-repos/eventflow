package com.app.eventflow.core.network

import com.app.eventflow.core.security.TokenStore
import com.app.eventflow.data.remote.api.AuthApi
import com.app.eventflow.data.remote.dto.RefreshRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Provider

/**
 * Ante 401: rota el refresh token UNA vez (sincronizado) y reintenta. Si el refresh falla
 * (expirado / refresh_token_reused), limpia la sesión — la UI observa hasSession y navega a Login.
 * Usa un AuthApi SIN authenticator (qualifier RefreshClient) para no recursar.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refreshApi: Provider<AuthApi>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.endsWith("/auth/refresh")) return null
        if (responseCount(response) >= 2) return null

        val currentRefresh = tokenStore.refreshToken() ?: return null

        synchronized(this) {
            // Otro hilo pudo rotar mientras esperábamos el lock
            val latestAccess = tokenStore.accessToken()
            val sentToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (latestAccess != null && latestAccess != sentToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestAccess")
                    .build()
            }

            val rotated = runCatching {
                runBlocking { refreshApi.get().refreshToken(RefreshRequestDto(currentRefresh)) }
            }.getOrNull()

            return if (rotated != null) {
                tokenStore.save(rotated.data.accessToken, rotated.data.refreshToken)
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${rotated.data.accessToken}")
                    .build()
            } else {
                tokenStore.clear()
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
