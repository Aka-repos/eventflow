package com.app.eventflow.testutil

import com.app.eventflow.domain.model.catalog.Category
import com.app.eventflow.domain.model.catalog.EventStatus
import com.app.eventflow.domain.model.catalog.EventSummary
import com.app.eventflow.domain.model.catalog.Money

fun anEventSummary(
    id: String = "e1",
    title: String = "Concierto Test",
    isFavorite: Boolean? = false,
) = EventSummary(
    id = id,
    title = title,
    venueName = "Estadio",
    startsAt = "2027-01-10T20:00:00Z",
    endsAt = "2027-01-10T23:00:00Z",
    timezone = "America/Panama",
    status = EventStatus.PUBLISHED,
    coverUrl = null,
    category = Category(1, "Conciertos", null),
    priceFrom = Money("25.00", "USD"),
    isFavorite = isFavorite,
)
