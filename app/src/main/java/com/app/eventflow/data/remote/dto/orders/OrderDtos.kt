package com.app.eventflow.data.remote.dto.orders

import com.app.eventflow.data.remote.dto.catalog.CategoryDto
import com.app.eventflow.data.remote.dto.catalog.CursorMetaDto
import com.app.eventflow.data.remote.dto.catalog.MoneyDto
import kotlinx.serialization.Serializable

/** DTOs espejo EXACTO de components.schemas (tags orders y tickets). Congelados. */

@Serializable
data class OrderItemRequestDto(val type: String, val referenceId: String, val quantity: Int)

@Serializable
data class CreateOrderRequestDto(val items: List<OrderItemRequestDto>)

@Serializable
data class PayOrderRequestDto(val method: String)

@Serializable
data class OrderItemResponseDto(
    val id: String,
    val type: String,
    val description: String,
    val quantity: Int,
    val unitPrice: MoneyDto,
    val ticketIds: List<String> = emptyList(),
)

@Serializable
data class PaymentSummaryDto(val id: String, val provider: String, val status: String, val amount: MoneyDto)

@Serializable
data class OrderResponseDto(
    val id: String,
    val status: String,
    val total: MoneyDto,
    val expiresAt: String,
    val createdAt: String,
    val items: List<OrderItemResponseDto> = emptyList(),
    val payment: PaymentSummaryDto? = null,
)

@Serializable
data class OrdersPageDto(val data: List<OrderResponseDto>, val meta: CursorMetaDto)

@Serializable
data class TicketEventLiteDto(
    val id: String,
    val title: String,
    val venueName: String,
    val startsAt: String,
    val endsAt: String,
    val timezone: String,
    val status: String,
    val coverUrl: String? = null,
    val category: CategoryDto? = null,
)

@Serializable
data class TicketResponseDto(
    val id: String,
    val event: TicketEventLiteDto? = null,
    val ticketTypeName: String,
    val zoneName: String? = null,
    val status: String,
    val acquiredVia: String,
    val purchasedAt: String,
    val qrAvailableAt: String? = null,
    val canRecover: Boolean,
)

@Serializable
data class TicketsPageDto(val data: List<TicketResponseDto>, val meta: CursorMetaDto)
