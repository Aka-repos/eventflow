package com.app.eventflow.data.remote.dto.checkin

import kotlinx.serialization.Serializable

/** DTOs espejo de QrResponse/CheckInRequest/CheckInResponse (OpenAPI congelado). */

@Serializable
data class QrResponseDto(val qrToken: String, val expiresAt: String, val refreshAfter: String)

@Serializable
data class CheckInRequestDto(val qrToken: String)

@Serializable
data class CheckInResponseDto(
    val result: String,
    val attendeeName: String? = null,
    val ticketTypeName: String? = null,
    val zoneName: String? = null,
    val denialCode: String? = null,
    val occurredAt: String,
)
