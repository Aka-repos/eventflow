package com.app.eventflow.data.repository

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.core.network.map
import com.app.eventflow.core.network.safeApiCall
import com.app.eventflow.core.security.TokenStore
import com.app.eventflow.data.local.SessionUserDao
import com.app.eventflow.data.mapper.toDomain
import com.app.eventflow.data.mapper.toEntity
import com.app.eventflow.data.remote.api.AuthApi
import com.app.eventflow.data.remote.dto.AuthTokensResponseDto
import com.app.eventflow.data.remote.dto.LoginRequestDto
import com.app.eventflow.data.remote.dto.LogoutRequestDto
import com.app.eventflow.data.remote.dto.RegisterRequestDto
import com.app.eventflow.domain.model.UserProfile
import com.app.eventflow.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val tokenStore: TokenStore,
    private val sessionUserDao: SessionUserDao,
    private val problemConverter: ProblemConverter,
    private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override suspend fun register(
        email: String,
        password: String,
        fullName: String,
        phone: String?,
    ): AppResult<UserProfile> =
        safeApiCall(ioDispatcher, problemConverter) {
            api.register(RegisterRequestDto(email, password, fullName, phone)).data
        }.also { persistOnSuccess(it) }.map { it.user.toEntity().toDomain() }

    override suspend fun login(email: String, password: String): AppResult<UserProfile> =
        safeApiCall(ioDispatcher, problemConverter) {
            api.login(LoginRequestDto(email, password)).data
        }.also { persistOnSuccess(it) }.map { it.user.toEntity().toDomain() }

    override suspend fun logout() {
        withContext(ioDispatcher) {
            // Best-effort en servidor (idempotente); la sesión local se limpia SIEMPRE
            tokenStore.refreshToken()?.let { refresh ->
                runCatching { api.logout(LogoutRequestDto(refresh)) }
            }
            tokenStore.clear()
            sessionUserDao.clear()
        }
    }

    override fun observeSession(): Flow<UserProfile?> =
        combine(sessionUserDao.observe(), tokenStore.hasSession) { entity, hasTokens ->
            if (hasTokens) entity?.toDomain() else null
        }

    private suspend fun persistOnSuccess(result: AppResult<AuthTokensResponseDto>) {
        if (result is AppResult.Success) {
            tokenStore.save(result.value.accessToken, result.value.refreshToken)
            sessionUserDao.upsert(result.value.user.toEntity())
        }
    }
}
