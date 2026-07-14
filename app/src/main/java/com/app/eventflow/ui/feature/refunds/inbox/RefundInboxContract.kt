package com.app.eventflow.ui.feature.refunds.inbox

import com.app.eventflow.domain.model.refunds.RefundRequest

data class RefundInboxUiState(
    val items: List<RefundRequest> = emptyList(),
    val isLoading: Boolean = true,
    val offline: Boolean = false,
    val error: String? = null,
    val actioningId: String? = null,
    val rejectingId: String? = null,
    val rejectReason: String = "",
)

sealed interface RefundInboxUiEvent {
    data object Retry : RefundInboxUiEvent
    data class Approve(val refundId: String) : RefundInboxUiEvent
    data class StartReject(val refundId: String) : RefundInboxUiEvent
    data class RejectReasonChanged(val value: String) : RefundInboxUiEvent
    data object ConfirmReject : RefundInboxUiEvent
    data object DismissReject : RefundInboxUiEvent
    data object BackClicked : RefundInboxUiEvent
}

sealed interface RefundInboxUiEffect {
    data object NavigateBack : RefundInboxUiEffect
    data class ShowMessage(val message: String) : RefundInboxUiEffect
}
