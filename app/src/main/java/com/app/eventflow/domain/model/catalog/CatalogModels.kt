package com.app.eventflow.domain.model.catalog

/** Modelos de dominio del catálogo (la UI solo ve esto, jamás DTOs — doc 10 §4). */

data class Money(val amount: String, val currency: String) {
    fun display(): String = "$currency $amount"
}

enum class EventStatus {
    DRAFT, PUBLISHED, SOLD_OUT, IN_PROGRESS, FINISHED, CANCELLED, SUSPENDED, UNKNOWN
}

data class Category(val id: Int, val name: String, val icon: String?)

data class EventSummary(
    val id: String,
    val title: String,
    val venueName: String,
    val startsAt: String,
    val endsAt: String,
    val timezone: String,
    val status: EventStatus,
    val coverUrl: String?,
    val category: Category,
    val priceFrom: Money?,
    val isFavorite: Boolean?,
)

data class TicketType(
    val id: String,
    val name: String,
    val description: String?,
    val price: Money,
    val zoneName: String?,
    val available: Boolean,
    val salesEndsAt: String?,
)

data class Zone(val id: String, val name: String, val capacity: Int)

data class Sponsor(val id: String, val name: String, val logoUrl: String?, val website: String?)

data class EventPolicyPublic(
    val refundWindowEndsAt: String?,
    val exchangeEnabled: Boolean,
    val exchangeDepreciationPct: Int,
    val waitlistEnabled: Boolean,
    val qrVisibilityHoursBefore: Int,
)

data class Organizer(val id: String, val name: String)

data class EventDetail(
    val summary: EventSummary,
    val description: String,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val organizer: Organizer,
    val ticketTypes: List<TicketType>,
    val zones: List<Zone>,
    val sponsors: List<Sponsor>,
    val policies: EventPolicyPublic,
    val waitlistOpen: Boolean,
)

/** Filtros de GET /events (parámetros congelados). */
data class EventQuery(
    val q: String? = null,
    val categoryId: Int? = null,
    val sortDescending: Boolean = false,
    val cursor: String? = null,
    val limit: Int = 20,
)

data class EventsPage(val items: List<EventSummary>, val nextCursor: String?)
