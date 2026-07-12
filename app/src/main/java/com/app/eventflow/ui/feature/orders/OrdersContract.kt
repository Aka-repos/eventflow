package com.app.eventflow.ui.feature.orders

import com.app.eventflow.domain.model.orders.Order

data class OrdersUiState(
    val orders: List<Order> = emptyList(),
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val processingOrderId: String? = null,
) {
    val isEmpty: Boolean get() = orders.isEmpty()
}

sealed interface OrdersUiEvent {
    data object Refresh : OrdersUiEvent
    data class PayClicked(val orderId: String) : OrdersUiEvent
    data class CancelClicked(val orderId: String) : OrdersUiEvent
}

sealed interface OrdersUiEffect {
    data class ShowMessage(val message: String) : OrdersUiEffect
}
