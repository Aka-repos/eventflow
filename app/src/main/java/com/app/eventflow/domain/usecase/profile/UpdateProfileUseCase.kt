package com.app.eventflow.domain.usecase.profile

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.UserProfile
import com.app.eventflow.domain.repository.AuthRepository
import javax.inject.Inject

/** PUT /me: actualiza los datos editables del perfil (fullName, phone). */
class UpdateProfileUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(fullName: String, phone: String?): AppResult<UserProfile> =
        repository.updateProfile(fullName, phone)
}
