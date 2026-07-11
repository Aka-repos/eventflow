package com.app.eventflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Usuario autenticado cacheado (requisito offline). Los tokens van al TokenStore, jamás aquí. */
@Entity(tableName = "session_user")
data class SessionUserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val roles: String, // CSV de códigos de rol
)
