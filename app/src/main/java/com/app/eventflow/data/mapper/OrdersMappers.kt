package com.app.eventflow.data.mapper

import com.app.eventflow.data.local.OrderEntity
import com.app.eventflow.data.local.TicketEntity
import com.app.eventflow.data.remote.dto.orders.OrderItemResponseDto
import com.app.eventflow.data.remote.dto.orders.OrderResponseDto
import com.app.eventflow.data.remote.dto.orders.PaymentSummaryDto
import com.app.eventflow.data.remote.dto.orders.TicketResponseDto
import com.app.eventflow.domain.model.catalog.Money
import com.app.eventflow.domain.model.orders.Order
import com.app.eventflow.domain.model.orders.OrderItem
import com.app.eventflow.domain.model.orders.OrderStatus
import com.app.eventflow.domain.model.orders.PaymentSummary
import com.app.eventflow.domain.model.orders.Ticket
import com.app.eventflow.domain.model.orders.TicketEventInfo
import com.app.eventflow.domain.model.orders.TicketStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Dto→Domain y Dto↔Entity. Enums desconocidos degradan a UNKNOWN (api/06 §5). */

private val cacheJson = Json { ignoreUnknownKeys = true }

fun orderStatusOf(raw: String): OrderStatus =
    OrderStatus.entries.firstOrNull { it.name == raw } ?: OrderStatus.UNKNOWN

fun ticketStatusOf(raw: String): TicketStatus =
    TicketStatus.entries.firstOrNull { it.name == raw } ?: TicketStatus.UNKNOWN

fun OrderItemResponseDto.toDomain() = OrderItem(id, type, description, quantity,
    Money(unitPrice.amount, unitPrice.currency), ticketIds)

fun PaymentSummaryDto.toDomain() = PaymentSummary(id, provider, status, Money(amount.amount, amount.currency))

fun OrderResponseDto.toDomain() = Order(
    id = id,
    status = orderStatusOf(status),
    total = Money(total.amount, total.currency),
    expiresAt = expiresAt,
    createdAt = createdAt,
    items = items.map { it.toDomain() },
    payment = payment?.toDomain(),
)

fun OrderResponseDto.toEntity() = OrderEntity(
    id = id,
    status = status,
    totalAmount = total.amount,
    totalCurrency = total.currency,
    expiresAt = expiresAt,
    createdAt = createdAt,
    itemsJson = cacheJson.encodeToString(items),
    paymentJson = payment?.let { cacheJson.encodeToString(it) },
)

fun OrderEntity.toDomain(): Order {
    val items: List<OrderItemResponseDto> = cacheJson.decodeFromString(itemsJson)
    val payment: PaymentSummaryDto? = paymentJson?.let { cacheJson.decodeFromString(it) }
    return Order(
        id = id,
        status = orderStatusOf(status),
        total = Money(totalAmount, totalCurrency),
        expiresAt = expiresAt,
        createdAt = createdAt,
        items = items.map { it.toDomain() },
        payment = payment?.toDomain(),
    )
}

fun TicketResponseDto.toDomain() = Ticket(
    id = id,
    event = event?.let { TicketEventInfo(it.id, it.title, it.venueName, it.startsAt, it.timezone) },
    ticketTypeName = ticketTypeName,
    zoneName = zoneName,
    status = ticketStatusOf(status),
    acquiredVia = acquiredVia,
    purchasedAt = purchasedAt,
    qrAvailableAt = qrAvailableAt,
    canRecover = canRecover,
)

fun TicketResponseDto.toEntity() = TicketEntity(
    id = id,
    eventId = event?.id,
    eventTitle = event?.title,
    eventVenue = event?.venueName,
    eventStartsAt = event?.startsAt,
    eventTimezone = event?.timezone,
    ticketTypeName = ticketTypeName,
    zoneName = zoneName,
    status = status,
    acquiredVia = acquiredVia,
    purchasedAt = purchasedAt,
    qrAvailableAt = qrAvailableAt,
    canRecover = canRecover,
)

fun TicketEntity.toDomain() = Ticket(
    id = id,
    event = if (eventId != null && eventTitle != null && eventVenue != null &&
        eventStartsAt != null && eventTimezone != null
    ) {
        TicketEventInfo(eventId, eventTitle, eventVenue, eventStartsAt, eventTimezone)
    } else {
        null
    },
    ticketTypeName = ticketTypeName,
    zoneName = zoneName,
    status = ticketStatusOf(status),
    acquiredVia = acquiredVia,
    purchasedAt = purchasedAt,
    qrAvailableAt = qrAvailableAt,
    canRecover = canRecover,
)
