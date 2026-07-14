package com.app.eventflow.ui.feature.qr

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.usecase.checkin.GetTicketQrUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * QR dinámico del asistente. El ViewModel solo carga (y recarga bajo demanda); la temporización del
 * refresco vive en el Composable (LaunchedEffect sobre refreshAfter) para mantenerlo testeable.
 * El token vive solo en memoria — jamás en disco (regla de seguridad api/09).
 */
@HiltViewModel
class TicketQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTicketQr: GetTicketQrUseCase,
) : ViewModel() {

    private val ticketId: String = requireNotNull(savedStateHandle["ticketId"])

    private val _state = MutableStateFlow(TicketQrUiState())
    val state: StateFlow<TicketQrUiState> = _state.asStateFlow()

    private val _effects = Channel<TicketQrUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: TicketQrUiEvent) {
        when (event) {
            TicketQrUiEvent.Retry -> load()
            TicketQrUiEvent.BackClicked ->
                viewModelScope.launch { _effects.send(TicketQrUiEffect.NavigateBack) }
        }
    }

    /** Recarga (la UI la dispara antes de que el QR expire). */
    fun refresh() = load()

    /** Milisegundos hasta el próximo refresco, derivados de refreshAfter (para el Composable). */
    fun millisUntilRefresh(refreshAfter: String): Long =
        try {
            Duration.between(Instant.now(), Instant.parse(refreshAfter)).toMillis().coerceIn(5_000L, 120_000L)
        } catch (e: Exception) {
            30_000L
        }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = _state.value.qr == null, error = null)
            getTicketQr(ticketId)
                .onSuccess { _state.value = TicketQrUiState(qr = it, isLoading = false) }
                .onFailure { error ->
                    val notVisible = error is AppError.Forbidden && error.code == "qr_not_yet_visible"
                    _state.value = _state.value.copy(
                        isLoading = false,
                        notYetVisible = notVisible,
                        error = if (notVisible) null else describe(error),
                    )
                }
        }
    }

    private fun describe(error: AppError): String = when (error) {
        is AppError.Network -> "Sin conexión: el QR requiere estar en línea"
        is AppError.Conflict -> "El boleto no está activo"
        else -> "No se pudo obtener el QR"
    }
}
