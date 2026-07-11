package com.app.eventflow.data.remote.api

import com.app.eventflow.data.remote.dto.AuthTokensResponseDto
import com.app.eventflow.data.remote.dto.DataEnvelope
import com.app.eventflow.data.remote.dto.LoginRequestDto
import com.app.eventflow.data.remote.dto.LogoutRequestDto
import com.app.eventflow.data.remote.dto.RefreshRequestDto
import com.app.eventflow.data.remote.dto.RegisterRequestDto
import retrofit2.http.Body
import retrofit2.http.POST

/** Métodos = operationId del tag auth del contrato. */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): DataEnvelope<AuthTokensResponseDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): DataEnvelope<AuthTokensResponseDto>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body body: RefreshRequestDto): DataEnvelope<AuthTokensResponseDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto)
}
