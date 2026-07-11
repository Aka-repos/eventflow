package com.app.eventflow.core.security

import kotlinx.coroutines.flow.StateFlow

/**
 * Almacén de tokens de sesión. Los tokens JAMÁS tocan Room ni SharedPreferences plano
 * (docs/engineering/03 §4); la implementación usa EncryptedSharedPreferences.
 */
interface TokenStore {

    val hasSession: StateFlow<Boolean>

    fun accessToken(): String?

    fun refreshToken(): String?

    fun save(accessToken: String, refreshToken: String)

    fun clear()
}
