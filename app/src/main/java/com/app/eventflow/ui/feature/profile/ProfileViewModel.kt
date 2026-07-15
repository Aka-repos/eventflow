package com.app.eventflow.ui.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.usecase.auth.ObserveSessionUseCase
import com.app.eventflow.domain.usecase.profile.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Perfil editable (PUT /me). Prellena desde la sesión local observada; valida en cliente igual que el
 * contrato (fullName 1..200, phone E.164) antes de enviar. El servidor revalida y manda la verdad.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val updateProfile: UpdateProfileUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _effects = Channel<ProfileUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeSession().collect { profile ->
                // prellena una sola vez (para no pisar lo que el usuario está escribiendo)
                if (profile != null && !_state.value.prefilled) {
                    _state.value = _state.value.copy(
                        email = profile.email,
                        roles = profile.roles.joinToString(", ") { it.name },
                        fullName = profile.fullName,
                        phone = profile.phone.orEmpty(),
                        prefilled = true,
                    )
                }
            }
        }
    }

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            is ProfileUiEvent.FullNameChanged -> _state.value = _state.value.copy(
                fullName = event.value,
                fullNameError = if (event.value.isBlank() || event.value.length > 200) NAME_ERROR else null,
            )
            is ProfileUiEvent.PhoneChanged -> _state.value = _state.value.copy(
                phone = event.value,
                phoneError = if (event.value.isNotBlank() && !PHONE_REGEX.matches(event.value)) PHONE_ERROR else null,
            )
            ProfileUiEvent.Save -> save()
            ProfileUiEvent.BackClicked ->
                viewModelScope.launch { _effects.send(ProfileUiEffect.NavigateBack) }
        }
    }

    private fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.value = current.copy(saving = true)
            updateProfile(current.fullName.trim(), current.phone.trim().ifBlank { null })
                .onSuccess {
                    // la sesión local ya se actualizó en el repo → observeSession refleja el cambio
                    _state.value = _state.value.copy(saving = false)
                    _effects.send(ProfileUiEffect.ShowMessage(SAVED))
                    _effects.send(ProfileUiEffect.NavigateBack)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(saving = false)
                    _effects.send(ProfileUiEffect.ShowMessage(describe(error)))
                }
        }
    }

    private fun describe(error: AppError): String = when (error) {
        is AppError.Network -> "Sin conexión: intenta de nuevo en línea"
        is AppError.Validation -> error.fields.values.firstOrNull() ?: "Datos inválidos"
        is AppError.Auth -> "Sesión expirada"
        else -> "No se pudo actualizar el perfil"
    }

    private companion object {
        val PHONE_REGEX = Regex("^\\+[1-9]\\d{6,14}$")
        const val NAME_ERROR = "El nombre es obligatorio (máx. 200)"
        const val PHONE_ERROR = "Teléfono en formato internacional, ej. +50761234567"
        const val SAVED = "Perfil actualizado"
    }
}
