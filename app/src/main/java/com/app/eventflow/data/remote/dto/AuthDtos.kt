package com.app.eventflow.data.remote.dto

import kotlinx.serialization.Serializable

/** DTOs espejo EXACTO de components.schemas (docs/api/05-openapi.yaml). Congelados. */

@Serializable
data class DataEnvelope<T>(val data: T)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val fullName: String,
    val phone: String? = null,
)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class LogoutRequestDto(val refreshToken: String)

@Serializable
data class UserProfileDto(
    val id: String,
    val email: String,
    val fullName: String,
    val phone: String? = null,
    val roles: List<String> = emptyList(),
    val createdAt: String? = null,
)

/** Espejo del schema UpdateProfileRequest (solo campos editables: fullName requerido, phone E.164). */
@Serializable
data class UpdateProfileRequestDto(val fullName: String, val phone: String? = null)

@Serializable
data class AuthTokensResponseDto(
    val accessToken: String,
    val accessTokenExpiresIn: Long,
    val refreshToken: String,
    val user: UserProfileDto,
)
