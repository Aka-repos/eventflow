package com.app.eventflow.ui.feature.checkout

import com.app.eventflow.domain.model.catalog.TicketType
import com.app.eventflow.domain.model.orders.Order

/** Compra primaria (S2): resumen → orden PENDING con countdown → pago → boletos. */
data class CheckoutUiState(
    val eventTitle: String = "",
    val tariff: TicketType? = null,
    val quantity: Int = 1,
    val order: Order? = null,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val paymentError: String? = null,
    val fatalError: Boolean = false,
    val soldOut: Boolean = false,
) {
    val totalLabel: String
        get() = tariff?.let {
            val total = it.price.amount.toBigDecimal().multiply(quantity.toBigDecimal())
            "${it.price.currency} ${total.setScale(2)}"
        } ?: ""
}

sealed interface CheckoutUiEvent {
    data class QuantityChanged(val quantity: Int) : CheckoutUiEvent
    data object ConfirmOrder : CheckoutUiEvent
    data object Pay : CheckoutUiEvent
    data object CancelOrder : CheckoutUiEvent
    data object BackClicked : CheckoutUiEvent
}

sealed interface CheckoutUiEffect {
    data object NavigateToTickets : CheckoutUiEffect
    data object NavigateBack : CheckoutUiEffect
}
