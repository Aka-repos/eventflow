package com.app.eventflow.domain.model.refunds

/** Dinero presentable (el backend congela montos; el cliente solo los muestra). */
data class Money(val amount: String, val currency: String) {
    fun formatted(): String = "$amount $currency"
}

enum class RecoveryOption { REFUND, EXCHANGE, NONE }

data class RefundQuote(val amount: Money, val deadline: String?)

data class ExchangeQuote(
    val originalPrice: Money,
    val depreciationPct: Int,
    val listPrice: Money,
    val listingDeadline: String?,
)

/**
 * Opciones de recuperación de un boleto (ADR-19), calculadas por el servidor. El cliente jamás
 * decide elegibilidad: solo pinta la opción vigente (REFUND / EXCHANGE / NONE) y su motivo.
 */
data class RecoveryOptions(
    val ticketId: String,
    val option: RecoveryOption,
    val reason: String?,
    val refund: RefundQuote?,
    val exchange: ExchangeQuote?,
)

enum class RefundStatus { REQUESTED, APPROVED, REJECTED, CANCELLED, UNKNOWN }

/** Expediente de reembolso visto por asistente (su solicitud) y organizador (su bandeja). */
data class RefundRequest(
    val id: String,
    val ticketId: String,
    val amount: Money,
    val status: RefundStatus,
    val reason: String?,
    val createdAt: String,
    val resolvedAt: String?,
)

/** Página con cursor opaco para la bandeja del organizador. */
data class RefundPage(
    val items: List<RefundRequest>,
    val hasNext: Boolean,
    val nextCursor: String?,
)
