package com.app.eventflow.domain.model

/** Modelo de dominio puro; la UI solo ve esto (jamás DTOs ni Entities). */
data class UserProfile(
    val id: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val roles: List<UserRole>,
)

/** Enums del contrato con fallback UNKNOWN obligatorio (docs/api/06 §5). */
enum class UserRole {
    ADMIN, ORGANIZER, STAFF, ATTENDEE, UNKNOWN;

    companion object {
        fun from(raw: String): UserRole = entries.firstOrNull { it.name == raw } ?: UNKNOWN
    }
}
