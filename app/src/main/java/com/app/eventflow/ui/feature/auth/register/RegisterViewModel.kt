package com.app.eventflow.ui.feature.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.usecase.auth.RegisterUseCase
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
class RegisterViewModel @Inject constructor(
    private val register: RegisterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _effects = Channel<RegisterUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: RegisterUiEvent) {
        when (event) {
            is RegisterUiEvent.FullNameChanged -> _uiState.update { it.copy(fullName = event.value) }
            is RegisterUiEvent.EmailChanged ->
                _uiState.update { it.copy(email = event.value, fieldErrors = it.fieldErrors - "email", generalError = null) }
            is RegisterUiEvent.PasswordChanged ->
                _uiState.update { it.copy(password = event.value, fieldErrors = it.fieldErrors - "password") }
            is RegisterUiEvent.PhoneChanged ->
                _uiState.update { it.copy(phone = event.value, fieldErrors = it.fieldErrors - "phone") }
            RegisterUiEvent.Submit -> submit()
            RegisterUiEvent.GoToLogin -> viewModelScope.launch { _effects.send(RegisterUiEffect.NavigateLogin) }
        }
    }

    private fun submit() {
        val current = _uiState.value
        if (!current.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, generalError = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            register(current.email, current.password, current.fullName, current.phone.ifBlank { null })
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _effects.send(RegisterUiEffect.NavigateHome)
                }
                .onFailure { error ->
                    _uiState.update { state ->
                        when (error) {
                            is AppError.Validation ->
                                state.copy(isSubmitting = false, fieldErrors = error.fields)
                            is AppError.Conflict ->
                                state.copy(isSubmitting = false, generalError = RegisterError.EMAIL_TAKEN)
                            is AppError.Network ->
                                state.copy(isSubmitting = false, generalError = RegisterError.NETWORK)
                            else -> state.copy(isSubmitting = false, generalError = RegisterError.UNKNOWN)
                        }
                    }
                }
        }
    }
}
