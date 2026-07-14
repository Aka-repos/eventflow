package com.app.eventflow.ui.feature.refunds.recovery

import com.app.eventflow.domain.model.refunds.RecoveryOptions

data class RecoveryUiState(
    val options: RecoveryOptions? = null,
    val isLoading: Boolean = true,
    val offline: Boolean = false,
    val error: String? = null,
    val reason: String = "",
    val submitting: Boolean = false,
    val requested: Boolean = false,
)

sealed interface RecoveryUiEvent {
    data object Retry : RecoveryUiEvent
    data class ReasonChanged(val value: String) : RecoveryUiEvent
    data object RequestRefund : RecoveryUiEvent
    data object BackClicked : RecoveryUiEvent
}

sealed interface RecoveryUiEffect {
    data object NavigateBack : RecoveryUiEffect
    data class ShowMessage(val message: String) : RecoveryUiEffect
}
