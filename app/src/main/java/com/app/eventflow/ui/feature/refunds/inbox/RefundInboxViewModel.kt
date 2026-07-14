package com.app.eventflow.ui.feature.refunds.inbox

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.usecase.refunds.ApproveRefundUseCase
import com.app.eventflow.domain.usecase.refunds.ListEventRefundsUseCase
import com.app.eventflow.domain.usecase.refunds.RejectRefundUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bandeja de reembolsos del organizador dueño del evento. Aprobar/rechazar es ⚡ (server-side);
 * tras resolver, recarga la lista para reflejar el nuevo estado. El rechazo exige motivo.
 */
@HiltViewModel
class RefundInboxViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listEventRefunds: ListEventRefundsUseCase,
    private val approveRefund: ApproveRefundUseCase,
    private val rejectRefund: RejectRefundUseCase,
) : ViewModel() {

    private val eventId: String = requireNotNull(savedStateHandle["eventId"])

    private val _state = MutableStateFlow(RefundInboxUiState())
    val state: StateFlow<RefundInboxUiState> = _state.asStateFlow()

    private val _effects = Channel<RefundInboxUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: RefundInboxUiEvent) {
        when (event) {
            RefundInboxUiEvent.Retry -> load()
            is RefundInboxUiEvent.Approve -> approve(event.refundId)
            is RefundInboxUiEvent.StartReject ->
                _state.value = _state.value.copy(rejectingId = event.refundId, rejectReason = "")
            is RefundInboxUiEvent.RejectReasonChanged ->
                _state.value = _state.value.copy(rejectReason = event.value)
            RefundInboxUiEvent.ConfirmReject -> confirmReject()
            RefundInboxUiEvent.DismissReject ->
                _state.value = _state.value.copy(rejectingId = null, rejectReason = "")
            RefundInboxUiEvent.BackClicked ->
                viewModelScope.launch { _effects.send(RefundInboxUiEffect.NavigateBack) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, offline = false, error = null)
            listEventRefunds(eventId)
                .onSuccess { _state.value = _state.value.copy(items = it.items, isLoading = false) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        offline = error is AppError.Network,
                        error = if (error is AppError.Network) null else describe(error),
                    )
                }
        }
    }

    private fun approve(refundId: String) {
        if (_state.value.actioningId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actioningId = refundId)
            approveRefund(refundId)
                .onSuccess {
                    _state.value = _state.value.copy(actioningId = null)
                    load()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(actioningId = null)
                    _effects.send(RefundInboxUiEffect.ShowMessage(describe(error)))
                }
        }
    }

    private fun confirmReject() {
        val refundId = _state.value.rejectingId ?: return
        val reason = _state.value.rejectReason.trim()
        if (reason.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(actioningId = refundId, rejectingId = null)
            rejectRefund(refundId, reason)
                .onSuccess {
                    _state.value = _state.value.copy(actioningId = null, rejectReason = "")
                    load()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(actioningId = null)
                    _effects.send(RefundInboxUiEffect.ShowMessage(describe(error)))
                }
        }
    }

    private fun describe(error: AppError): String = when (error) {
        is AppError.Network -> "Sin conexión: intenta de nuevo"
        is AppError.Conflict -> when (error.code) {
            "refund_not_pending" -> "La solicitud ya fue resuelta"
            else -> "No se pudo resolver la solicitud"
        }
        is AppError.NotFound -> "La solicitud no está disponible"
        is AppError.Forbidden -> "No tienes permiso sobre este evento"
        else -> "No se pudo resolver la solicitud"
    }
}
