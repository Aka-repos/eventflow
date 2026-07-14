package com.app.eventflow.ui.feature.scanner

import com.app.eventflow.domain.model.checkin.CheckInOutcome

data class ScannerUiState(
    val isProcessing: Boolean = false,
    val lastOutcome: CheckInOutcome? = null,
    val networkError: Boolean = false,
) {
    /** Tras un resultado, la cámara pausa hasta que el staff toque "siguiente". */
    val cameraActive: Boolean get() = lastOutcome == null && !isProcessing
}

sealed interface ScannerUiEvent {
    data class QrDetected(val token: String) : ScannerUiEvent
    data object ScanNext : ScannerUiEvent
    data object BackClicked : ScannerUiEvent
}

sealed interface ScannerUiEffect {
    data object NavigateBack : ScannerUiEffect
}
