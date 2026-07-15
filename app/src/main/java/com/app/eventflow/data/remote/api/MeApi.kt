package com.app.eventflow.data.remote.api

import com.app.eventflow.data.remote.dto.DataEnvelope
import com.app.eventflow.data.remote.dto.UpdateProfileRequestDto
import com.app.eventflow.data.remote.dto.UserProfileDto
import retrofit2.http.Body
import retrofit2.http.PUT

/** Perfil del usuario autenticado (tag me). Método = operationId del contrato congelado. */
interface MeApi {

    @PUT("me")
    suspend fun updateProfile(@Body body: UpdateProfileRequestDto): DataEnvelope<UserProfileDto>
}
