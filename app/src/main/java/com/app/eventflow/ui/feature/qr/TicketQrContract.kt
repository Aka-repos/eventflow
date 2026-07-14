package com.app.eventflow.ui.feature.qr

import com.app.eventflow.domain.model.checkin.TicketQr

data class TicketQrUiState(
    val qr: TicketQr? = null,
    val isLoading: Boolean = true,
    val notYetVisible: Boolean = false,
    val error: String? = null,
)

sealed interface TicketQrUiEvent {
    data object Retry : TicketQrUiEvent
    data object BackClicked : TicketQrUiEvent
}

sealed interface TicketQrUiEffect {
    data object NavigateBack : TicketQrUiEffect
}
