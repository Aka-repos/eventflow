package com.app.eventflow.domain.usecase.auth

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.UserProfile
import com.app.eventflow.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(private val repository: AuthRepository) {

    suspend operator fun invoke(
        email: String,
        password: String,
        fullName: String,
        phone: String?,
    ): AppResult<UserProfile> =
        repository.register(email.trim(), password, fullName.trim(), phone?.trim()?.ifEmpty { null })
}
