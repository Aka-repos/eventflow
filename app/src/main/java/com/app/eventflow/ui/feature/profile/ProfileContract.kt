package com.app.eventflow.ui.feature.profile

data class ProfileUiState(
    val email: String = "",
    val roles: String = "",
    val fullName: String = "",
    val phone: String = "",
    val prefilled: Boolean = false,
    val saving: Boolean = false,
    val fullNameError: String? = null,
    val phoneError: String? = null,
) {
    /** Habilita "Guardar": nombre no vacío y teléfono válido (si se escribió) y no guardando. */
    val canSave: Boolean
        get() = !saving && fullName.isNotBlank() && fullNameError == null && phoneError == null
}

sealed interface ProfileUiEvent {
    data class FullNameChanged(val value: String) : ProfileUiEvent
    data class PhoneChanged(val value: String) : ProfileUiEvent
    data object Save : ProfileUiEvent
    data object BackClicked : ProfileUiEvent
}

sealed interface ProfileUiEffect {
    data object NavigateBack : ProfileUiEffect
    data class ShowMessage(val message: String) : ProfileUiEffect
}
