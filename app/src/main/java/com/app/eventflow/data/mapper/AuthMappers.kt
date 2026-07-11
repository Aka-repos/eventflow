package com.app.eventflow.data.mapper

import com.app.eventflow.data.local.SessionUserEntity
import com.app.eventflow.data.remote.dto.UserProfileDto
import com.app.eventflow.domain.model.UserProfile
import com.app.eventflow.domain.model.UserRole

/** Dto → Entity → Domain (docs/engineering/03 §4). Enum desconocido → UNKNOWN solo aquí. */

fun UserProfileDto.toEntity() = SessionUserEntity(
    id = id,
    email = email,
    fullName = fullName,
    phone = phone,
    roles = roles.joinToString(","),
)

fun SessionUserEntity.toDomain() = UserProfile(
    id = id,
    email = email,
    fullName = fullName,
    phone = phone,
    roles = roles.split(",").filter { it.isNotBlank() }.map(UserRole::from),
)
