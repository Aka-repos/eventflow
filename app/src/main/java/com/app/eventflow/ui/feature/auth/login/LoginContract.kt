package com.app.eventflow.ui.feature.auth.login

/** Tríada obligatoria del blueprint (docs/engineering/03 §1). */

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val generalError: LoginError? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

/** Errores que esta pantalla distingue, enrutados por code (jamás por texto del servidor). */
enum class LoginError { INVALID_CREDENTIALS, ACCOUNT_BLOCKED, NETWORK, UNKNOWN }

sealed interface LoginUiEvent {
    data class EmailChanged(val value: String) : LoginUiEvent
    data class PasswordChanged(val value: String) : LoginUiEvent
    data object Submit : LoginUiEvent
    data object GoToRegister : LoginUiEvent
}

sealed interface LoginUiEffect {
    data object NavigateHome : LoginUiEffect
    data object NavigateRegister : LoginUiEffect
}
