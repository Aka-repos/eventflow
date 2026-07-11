package com.app.eventflow.domain.usecase.auth

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.UserProfile
import com.app.eventflow.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(private val repository: AuthRepository) {

    suspend operator fun invoke(email: String, password: String): AppResult<UserProfile> =
        repository.login(email.trim(), password)
}
