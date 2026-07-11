package com.app.eventflow.core.network

import kotlinx.serialization.Serializable

/** Espejo de RFC 9457 + extensiones EventFlow (docs/api/02). */
@Serializable
data class ProblemDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val code: String? = null,
    val timestamp: String? = null,
    val traceId: String? = null,
    val errors: List<FieldErrorDto>? = null,
    val retryAfterSeconds: Int? = null,
    val conflictVersion: Int? = null,
)

@Serializable
data class FieldErrorDto(val field: String, val message: String, val code: String? = null)
