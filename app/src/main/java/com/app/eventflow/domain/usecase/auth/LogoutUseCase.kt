package com.app.eventflow.domain.usecase.auth

import com.app.eventflow.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(private val repository: AuthRepository) {

    suspend operator fun invoke() = repository.logout()
}
