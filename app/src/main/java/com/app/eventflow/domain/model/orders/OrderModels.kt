package com.app.eventflow.domain.model.orders

import com.app.eventflow.domain.model.catalog.Money

enum class OrderStatus { PENDING, PAID, FAILED, CANCELLED, REFUNDED, UNKNOWN }

enum class TicketStatus {
    ACTIVE, PUBLISHED_IN_EXCHANGE, REFUND_PENDING, REFUNDED, USED, EXPIRED, CANCELLED, INVALIDATED, UNKNOWN
}

data class OrderItem(
    val id: String,
    val type: String,
    val description: String,
    val quantity: Int,
    val unitPrice: Money,
    val ticketIds: List<String>,
)

data class PaymentSummary(val id: String, val provider: String, val status: String, val amount: Money)

data class Order(
    val id: String,
    val status: OrderStatus,
    val total: Money,
    val expiresAt: String,
    val createdAt: String,
    val items: List<OrderItem>,
    val payment: PaymentSummary?,
)

data class TicketEventInfo(
    val id: String,
    val title: String,
    val venueName: String,
    val startsAt: String,
    val timezone: String,
)

data class Ticket(
    val id: String,
    val event: TicketEventInfo?,
    val ticketTypeName: String,
    val zoneName: String?,
    val status: TicketStatus,
    val acquiredVia: String,
    val purchasedAt: String,
    val qrAvailableAt: String?,
    val canRecover: Boolean,
)
