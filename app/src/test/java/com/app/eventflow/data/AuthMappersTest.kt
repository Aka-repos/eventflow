package com.app.eventflow.data

import com.app.eventflow.data.mapper.toDomain
import com.app.eventflow.data.mapper.toEntity
import com.app.eventflow.data.remote.dto.UserProfileDto
import com.app.eventflow.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthMappersTest {

    @Test
    fun `dto a entity a domain conserva los datos`() {
        val dto = UserProfileDto("u-1", "ana@mail.com", "Ana P.", "+50761234567", listOf("ATTENDEE"))

        val domain = dto.toEntity().toDomain()

        assertEquals("u-1", domain.id)
        assertEquals("ana@mail.com", domain.email)
        assertEquals("+50761234567", domain.phone)
        assertEquals(listOf(UserRole.ATTENDEE), domain.roles)
    }

    @Test
    fun `rol desconocido del contrato cae en UNKNOWN, jamas crashea`() {
        val dto = UserProfileDto("u-1", "a@b.c", "A", null, listOf("SUPER_ADMIN_2030", "STAFF"))

        val domain = dto.toEntity().toDomain()

        assertEquals(listOf(UserRole.UNKNOWN, UserRole.STAFF), domain.roles)
    }
}
