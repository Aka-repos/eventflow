package com.app.eventflow.data

import com.app.eventflow.data.mapper.eventStatusOf
import com.app.eventflow.data.mapper.toDomain
import com.app.eventflow.data.mapper.toFavoriteEntity
import com.app.eventflow.data.remote.dto.catalog.CategoryDto
import com.app.eventflow.data.remote.dto.catalog.EventSummaryDto
import com.app.eventflow.data.remote.dto.catalog.MoneyDto
import com.app.eventflow.domain.model.catalog.EventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMappersTest {

    private val dto = EventSummaryDto(
        id = "e1",
        title = "Concierto",
        venueName = "Estadio",
        startsAt = "2027-01-10T20:00:00Z",
        endsAt = "2027-01-10T23:00:00Z",
        timezone = "America/Panama",
        status = "PUBLISHED",
        category = CategoryDto(1, "Conciertos"),
        priceFrom = MoneyDto("25.00", "USD"),
        isFavorite = true,
    )

    @Test
    fun `dto maps to domain preserving money and category`() {
        val domain = dto.toDomain()
        assertEquals(EventStatus.PUBLISHED, domain.status)
        assertEquals("25.00", domain.priceFrom?.amount)
        assertEquals("Conciertos", domain.category.name)
        assertEquals(true, domain.isFavorite)
    }

    @Test
    fun `unknown enum degrades to UNKNOWN not crash`() {
        assertEquals(EventStatus.UNKNOWN, eventStatusOf("MUTATED_FUTURE_STATUS"))
        assertEquals(EventStatus.UNKNOWN, dto.copy(status = "NUEVO_ESTADO").toDomain().status)
    }

    @Test
    fun `favorite entity roundtrip preserves summary`() {
        val original = dto.toDomain()
        val restored = original.toFavoriteEntity(savedAt = 42L).toDomain()
        assertEquals(original.id, restored.id)
        assertEquals(original.title, restored.title)
        assertEquals(original.priceFrom, restored.priceFrom)
        assertEquals(original.category, restored.category)
        assertEquals(true, restored.isFavorite)
    }

    @Test
    fun `favorite entity without price maps to null money`() {
        val noPrice = dto.copy(priceFrom = null).toDomain()
        assertNull(noPrice.toFavoriteEntity(1L).toDomain().priceFrom)
    }
}
