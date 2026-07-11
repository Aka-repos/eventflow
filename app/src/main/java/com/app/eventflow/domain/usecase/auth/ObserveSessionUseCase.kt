package com.app.eventflow.domain.usecase.auth

import com.app.eventflow.domain.model.UserProfile
import com.app.eventflow.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSessionUseCase @Inject constructor(private val repository: AuthRepository) {

    operator fun invoke(): Flow<UserProfile?> = repository.observeSession()
}
