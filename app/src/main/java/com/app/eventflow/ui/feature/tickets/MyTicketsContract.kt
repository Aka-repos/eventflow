package com.app.eventflow.ui.feature.tickets

import com.app.eventflow.domain.model.orders.Ticket

data class MyTicketsUiState(
    val tickets: List<Ticket> = emptyList(),
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
) {
    val isEmpty: Boolean get() = tickets.isEmpty()
}

sealed interface MyTicketsUiEvent {
    data object Refresh : MyTicketsUiEvent
}
