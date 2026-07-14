package com.app.eventflow.data.remote.dto.refunds

import com.app.eventflow.data.remote.dto.catalog.CursorMetaDto
import com.app.eventflow.data.remote.dto.catalog.MoneyDto
import kotlinx.serialization.Serializable

/** DTOs espejo de los schemas congelados de Recuperación/Reembolsos (OpenAPI 3.1). */

@Serializable
data class RecoveryOptionsDto(
    val ticketId: String,
    val option: String,                 // REFUND | EXCHANGE | NONE
    val reason: String? = null,
    val refund: RefundQuoteDto? = null,
    val exchange: ExchangeQuoteDto? = null,
    val links: RecoveryLinksDto? = null,
)

@Serializable
data class RefundQuoteDto(val amount: MoneyDto, val deadline: String? = null)

@Serializable
data class ExchangeQuoteDto(
    val originalPrice: MoneyDto,
    val depreciationPct: Int,
    val listPrice: MoneyDto,
    val listingDeadline: String? = null,
)

@Serializable
data class RecoveryLinksDto(val action: String? = null)

@Serializable
data class CreateRefundRequestDto(val reason: String? = null)

@Serializable
data class RejectRefundRequestDto(val reason: String)

@Serializable
data class RefundResponseDto(
    val id: String,
    val ticketId: String,
    val amount: MoneyDto,
    val status: String,                 // REQUESTED | APPROVED | REJECTED | CANCELLED
    val reason: String? = null,
    val createdAt: String,
    val resolvedAt: String? = null,
)

@Serializable
data class RefundsPageDto(val data: List<RefundResponseDto>, val meta: CursorMetaDto)
