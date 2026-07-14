package com.app.eventflow.ui.feature.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.usecase.checkin.EventCheckInUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Escáner de check-in (STAFF/ORGANIZER): recibe el token detectado por la cámara (CameraX+ML Kit)
 * y lo manda al backend. El cliente NUNCA decide si el QR es válido — solo transporta y muestra el
 * veredicto server-side. Deduplica lecturas repetidas del mismo frame.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val checkIn: EventCheckInUseCase,
) : ViewModel() {

    private val eventId: String = requireNotNull(savedStateHandle["eventId"])

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private val _effects = Channel<ScannerUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var lastToken: String? = null

    fun onEvent(event: ScannerUiEvent) {
        when (event) {
            is ScannerUiEvent.QrDetected -> onDetected(event.token)
            ScannerUiEvent.ScanNext -> {
                lastToken = null
                _state.value = ScannerUiState()
            }
            ScannerUiEvent.BackClicked ->
                viewModelScope.launch { _effects.send(ScannerUiEffect.NavigateBack) }
        }
    }

    private fun onDetected(token: String) {
        // ignora frames repetidos y lecturas mientras procesa o ya hay resultado
        if (!_state.value.cameraActive || token == lastToken) {
            return
        }
        lastToken = token
        _state.value = _state.value.copy(isProcessing = true, networkError = false)
        viewModelScope.launch {
            checkIn(eventId, token)
                .onSuccess { outcome ->
                    _state.value = ScannerUiState(lastOutcome = outcome)
                }
                .onFailure { error ->
                    // solo un fallo de red llega aquí; los rechazos del contrato son "Denied"
                    _state.value = ScannerUiState(networkError = error is AppError.Network)
                    lastToken = null
                }
        }
    }
}
