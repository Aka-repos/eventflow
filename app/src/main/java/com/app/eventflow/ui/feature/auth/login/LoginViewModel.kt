package com.app.eventflow.ui.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.usecase.auth.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _effects = Channel<LoginUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.EmailChanged ->
                _uiState.update { it.copy(email = event.value, emailError = null, generalError = null) }
            is LoginUiEvent.PasswordChanged ->
                _uiState.update { it.copy(password = event.value, passwordError = null, generalError = null) }
            LoginUiEvent.Submit -> submit()
            LoginUiEvent.GoToRegister -> viewModelScope.launch { _effects.send(LoginUiEffect.NavigateRegister) }
        }
    }

    private fun submit() {
        val current = _uiState.value
        if (!current.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, generalError = null) }
        viewModelScope.launch {
            login(current.email, current.password)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _effects.send(LoginUiEffect.NavigateHome)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSubmitting = false, generalError = error.toLoginError()) }
                }
        }
    }

    private fun AppError.toLoginError(): LoginError = when (this) {
        is AppError.Auth -> LoginError.INVALID_CREDENTIALS
        is AppError.Forbidden -> LoginError.ACCOUNT_BLOCKED
        is AppError.Network -> LoginError.NETWORK
        else -> LoginError.UNKNOWN
    }
}
