package com.app.eventflow.ui.feature.refunds.recovery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.usecase.refunds.GetRecoveryOptionsUseCase
import com.app.eventflow.domain.usecase.refunds.RequestRefundUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Cancelación inteligente del asistente (ADR-19). El cliente NO decide elegibilidad: pinta la opción
 * que calcula el servidor (REFUND/EXCHANGE/NONE) y, si procede, envía la solicitud de reembolso.
 * Las opciones exigen conexión — jamás se cachean (api/09).
 */
@HiltViewModel
class RecoveryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getRecoveryOptions: GetRecoveryOptionsUseCase,
    private val requestRefund: RequestRefundUseCase,
) : ViewModel() {

    private val ticketId: String = requireNotNull(savedStateHandle["ticketId"])

    private val _state = MutableStateFlow(RecoveryUiState())
    val state: StateFlow<RecoveryUiState> = _state.asStateFlow()

    private val _effects = Channel<RecoveryUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: RecoveryUiEvent) {
        when (event) {
            RecoveryUiEvent.Retry -> load()
            is RecoveryUiEvent.ReasonChanged -> _state.value = _state.value.copy(reason = event.value)
            RecoveryUiEvent.RequestRefund -> submit()
            RecoveryUiEvent.BackClicked ->
                viewModelScope.launch { _effects.send(RecoveryUiEffect.NavigateBack) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, offline = false, error = null)
            getRecoveryOptions(ticketId)
                .onSuccess { _state.value = _state.value.copy(options = it, isLoading = false) }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        offline = error is AppError.Network,
                        error = if (error is AppError.Network) null else describe(error),
                    )
                }
        }
    }

    private fun submit() {
        if (_state.value.submitting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true)
            requestRefund(ticketId, _state.value.reason.ifBlank { null })
                .onSuccess {
                    _state.value = _state.value.copy(submitting = false, requested = true)
                    _effects.send(RecoveryUiEffect.NavigateBack)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(submitting = false)
                    _effects.send(RecoveryUiEffect.ShowMessage(describe(error)))
                }
        }
    }

    private fun describe(error: AppError): String = when (error) {
        is AppError.Network -> "Sin conexión: intenta de nuevo en línea"
        is AppError.Conflict -> when (error.code) {
            "refund_already_requested" -> "Ya existe una solicitud para este boleto"
            "refund_not_pending" -> "La solicitud ya fue resuelta"
            else -> "No se pudo completar la solicitud"
        }
        is AppError.Business -> when (error.code) {
            "refund_window_closed" -> "La ventana de reembolso ya cerró"
            "refund_not_allowed_exchange_acquired" -> "Los boletos adquiridos por reventa no admiten reembolso"
            else -> error.detail ?: "No se pudo completar la solicitud"
        }
        is AppError.NotFound -> "El boleto no está disponible"
        else -> "No se pudo completar la solicitud"
    }
}
