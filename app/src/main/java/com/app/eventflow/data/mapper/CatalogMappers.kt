package com.app.eventflow.data.mapper

import com.app.eventflow.data.local.FavoriteEventEntity
import com.app.eventflow.data.remote.dto.catalog.CategoryDto
import com.app.eventflow.data.remote.dto.catalog.EventDetailDto
import com.app.eventflow.data.remote.dto.catalog.EventPolicyPublicDto
import com.app.eventflow.data.remote.dto.catalog.EventSummaryDto
import com.app.eventflow.data.remote.dto.catalog.MoneyDto
import com.app.eventflow.data.remote.dto.catalog.SponsorDto
import com.app.eventflow.data.remote.dto.catalog.TicketTypeDto
import com.app.eventflow.data.remote.dto.catalog.ZoneDto
import com.app.eventflow.domain.model.catalog.Category
import com.app.eventflow.domain.model.catalog.EventDetail
import com.app.eventflow.domain.model.catalog.EventPolicyPublic
import com.app.eventflow.domain.model.catalog.EventStatus
import com.app.eventflow.domain.model.catalog.EventSummary
import com.app.eventflow.domain.model.catalog.Money
import com.app.eventflow.domain.model.catalog.Organizer
import com.app.eventflow.domain.model.catalog.Sponsor
import com.app.eventflow.domain.model.catalog.TicketType
import com.app.eventflow.domain.model.catalog.Zone

/** Dto→Domain y Entity↔Domain. Enums desconocidos degradan a UNKNOWN (api/06 §5). */

fun MoneyDto.toDomain() = Money(amount, currency)

fun CategoryDto.toDomain() = Category(id, name, icon)

fun SponsorDto.toDomain() = Sponsor(id, name, logoUrl, website)

fun ZoneDto.toDomain() = Zone(id, name, capacity)

fun TicketTypeDto.toDomain() = TicketType(id, name, description, price.toDomain(), zoneName, available, salesEndsAt)

fun EventPolicyPublicDto.toDomain() = EventPolicyPublic(
    refundWindowEndsAt, exchangeEnabled, exchangeDepreciationPct, waitlistEnabled, qrVisibilityHoursBefore,
)

fun eventStatusOf(raw: String): EventStatus =
    EventStatus.entries.firstOrNull { it.name == raw } ?: EventStatus.UNKNOWN

fun EventSummaryDto.toDomain() = EventSummary(
    id = id,
    title = title,
    venueName = venueName,
    startsAt = startsAt,
    endsAt = endsAt,
    timezone = timezone,
    status = eventStatusOf(status),
    coverUrl = coverUrl,
    category = category.toDomain(),
    priceFrom = priceFrom?.toDomain(),
    isFavorite = isFavorite,
)

fun EventDetailDto.toDomain() = EventDetail(
    summary = EventSummary(
        id = id,
        title = title,
        venueName = venueName,
        startsAt = startsAt,
        endsAt = endsAt,
        timezone = timezone,
        status = eventStatusOf(status),
        coverUrl = coverUrl,
        category = category.toDomain(),
        priceFrom = priceFrom?.toDomain(),
        isFavorite = isFavorite,
    ),
    description = description,
    address = address,
    latitude = latitude,
    longitude = longitude,
    organizer = Organizer(organizer.id, organizer.name),
    ticketTypes = ticketTypes.map { it.toDomain() },
    zones = zones.map { it.toDomain() },
    sponsors = sponsors.map { it.toDomain() },
    policies = policies.toDomain(),
    waitlistOpen = waitlistOpen,
)

fun EventSummary.toFavoriteEntity(savedAt: Long) = FavoriteEventEntity(
    id = id,
    title = title,
    venueName = venueName,
    startsAt = startsAt,
    endsAt = endsAt,
    timezone = timezone,
    status = status.name,
    coverUrl = coverUrl,
    categoryId = category.id,
    categoryName = category.name,
    categoryIcon = category.icon,
    priceAmount = priceFrom?.amount,
    priceCurrency = priceFrom?.currency,
    savedAt = savedAt,
)

fun FavoriteEventEntity.toDomain() = EventSummary(
    id = id,
    title = title,
    venueName = venueName,
    startsAt = startsAt,
    endsAt = endsAt,
    timezone = timezone,
    status = eventStatusOf(status),
    coverUrl = coverUrl,
    category = Category(categoryId, categoryName, categoryIcon),
    priceFrom = if (priceAmount != null && priceCurrency != null) Money(priceAmount, priceCurrency) else null,
    isFavorite = true,
)
