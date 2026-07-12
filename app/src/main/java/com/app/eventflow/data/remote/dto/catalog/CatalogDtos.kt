package com.app.eventflow.data.remote.dto.catalog

import kotlinx.serialization.Serializable

/** DTOs espejo EXACTO de components.schemas del OpenAPI congelado (tag catalog/me). */

@Serializable
data class MoneyDto(val amount: String, val currency: String)

@Serializable
data class CategoryDto(val id: Int, val name: String, val icon: String? = null)

@Serializable
data class SponsorDto(val id: String, val name: String, val logoUrl: String? = null, val website: String? = null)

@Serializable
data class ZoneDto(val id: String, val name: String, val capacity: Int)

@Serializable
data class TicketTypeDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val price: MoneyDto,
    val zoneName: String? = null,
    val available: Boolean,
    val salesEndsAt: String? = null,
)

@Serializable
data class EventPolicyPublicDto(
    val refundWindowEndsAt: String? = null,
    val exchangeEnabled: Boolean,
    val exchangeDepreciationPct: Int,
    val waitlistEnabled: Boolean,
    val qrVisibilityHoursBefore: Int,
)

@Serializable
data class OrganizerDto(val id: String, val name: String)

@Serializable
data class EventSummaryDto(
    val id: String,
    val title: String,
    val venueName: String,
    val startsAt: String,
    val endsAt: String,
    val timezone: String,
    val status: String,
    val coverUrl: String? = null,
    val category: CategoryDto,
    val priceFrom: MoneyDto? = null,
    val isFavorite: Boolean? = null,
)

@Serializable
data class EventDetailDto(
    val id: String,
    val title: String,
    val venueName: String,
    val startsAt: String,
    val endsAt: String,
    val timezone: String,
    val status: String,
    val coverUrl: String? = null,
    val category: CategoryDto,
    val priceFrom: MoneyDto? = null,
    val isFavorite: Boolean? = null,
    val description: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val organizer: OrganizerDto,
    val ticketTypes: List<TicketTypeDto> = emptyList(),
    val zones: List<ZoneDto> = emptyList(),
    val sponsors: List<SponsorDto> = emptyList(),
    val policies: EventPolicyPublicDto,
    val waitlistOpen: Boolean,
)

@Serializable
data class CursorMetaDto(val hasNext: Boolean, val nextCursor: String? = null)

@Serializable
data class EventsPageDto(val data: List<EventSummaryDto>, val meta: CursorMetaDto)
