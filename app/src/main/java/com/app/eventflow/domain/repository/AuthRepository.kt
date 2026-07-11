package com.app.eventflow.domain.repository

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    suspend fun register(email: String, password: String, fullName: String, phone: String?): AppResult<UserProfile>

    suspend fun login(email: String, password: String): AppResult<UserProfile>

    suspend fun logout()

    /** Sesión local (Room + TokenStore); null = sin sesión. Fuente de verdad de la navegación raíz. */
    fun observeSession(): Flow<UserProfile?>
}
