package com.app.eventflow.testutil

import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.UserProfile
import com.app.eventflow.domain.model.UserRole
import com.app.eventflow.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Fake determinista (preferido sobre mocks — reglas kotlin/testing). */
class FakeAuthRepository : AuthRepository {

    val sampleUser = UserProfile("u-1", "ana@mail.com", "Ana P.", null, listOf(UserRole.ATTENDEE))

    var nextError: AppError? = null
    var loginCalls = 0
    var registerCalls = 0
    var logoutCalls = 0

    private val session = MutableStateFlow<UserProfile?>(null)

    override suspend fun register(
        email: String,
        password: String,
        fullName: String,
        phone: String?,
    ): AppResult<UserProfile> {
        registerCalls++
        return respond()
    }

    override suspend fun login(email: String, password: String): AppResult<UserProfile> {
        loginCalls++
        return respond()
    }

    override suspend fun logout() {
        logoutCalls++
        session.value = null
    }

    override fun observeSession(): Flow<UserProfile?> = session

    private fun respond(): AppResult<UserProfile> =
        nextError?.let { AppResult.Failure(it) }
            ?: AppResult.Success(sampleUser).also { session.value = sampleUser }
}
