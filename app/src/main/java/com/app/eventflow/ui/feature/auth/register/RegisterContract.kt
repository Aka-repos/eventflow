package com.app.eventflow.ui.feature.auth.register

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val phone: String = "",
    val isSubmitting: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val generalError: RegisterError? = null,
) {
    val canSubmit: Boolean
        get() = fullName.isNotBlank() && email.isNotBlank() && password.length >= 8 && !isSubmitting
}

enum class RegisterError { EMAIL_TAKEN, NETWORK, UNKNOWN }

sealed interface RegisterUiEvent {
    data class FullNameChanged(val value: String) : RegisterUiEvent
    data class EmailChanged(val value: String) : RegisterUiEvent
    data class PasswordChanged(val value: String) : RegisterUiEvent
    data class PhoneChanged(val value: String) : RegisterUiEvent
    data object Submit : RegisterUiEvent
    data object GoToLogin : RegisterUiEvent
}

sealed interface RegisterUiEffect {
    data object NavigateHome : RegisterUiEffect
    data object NavigateLogin : RegisterUiEffect
}
