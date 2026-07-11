package com.app.eventflow.ui.feature.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.eventflow.R
import com.app.eventflow.ui.components.EfPasswordField
import com.app.eventflow.ui.components.EfPrimaryButton
import com.app.eventflow.ui.components.EfTextField

@Composable
fun RegisterRoute(
    onNavigateHome: () -> Unit,
    onNavigateLogin: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RegisterUiEffect.NavigateHome -> onNavigateHome()
                RegisterUiEffect.NavigateLogin -> onNavigateLogin()
            }
        }
    }
    RegisterScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
fun RegisterScreen(uiState: RegisterUiState, onEvent: (RegisterUiEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.register_title), style = MaterialTheme.typography.headlineMedium)

        EfTextField(
            value = uiState.fullName,
            onValueChange = { onEvent(RegisterUiEvent.FullNameChanged(it)) },
            label = stringResource(R.string.field_full_name),
            error = uiState.fieldErrors["fullName"],
            enabled = !uiState.isSubmitting,
        )
        EfTextField(
            value = uiState.email,
            onValueChange = { onEvent(RegisterUiEvent.EmailChanged(it)) },
            label = stringResource(R.string.field_email),
            error = uiState.fieldErrors["email"],
            keyboardType = KeyboardType.Email,
            enabled = !uiState.isSubmitting,
        )
        EfPasswordField(
            value = uiState.password,
            onValueChange = { onEvent(RegisterUiEvent.PasswordChanged(it)) },
            label = stringResource(R.string.field_password),
            error = uiState.fieldErrors["password"],
            enabled = !uiState.isSubmitting,
        )
        EfTextField(
            value = uiState.phone,
            onValueChange = { onEvent(RegisterUiEvent.PhoneChanged(it)) },
            label = stringResource(R.string.field_phone),
            error = uiState.fieldErrors["phone"],
            keyboardType = KeyboardType.Phone,
            enabled = !uiState.isSubmitting,
        )

        uiState.generalError?.let { error ->
            Text(
                text = stringResource(
                    when (error) {
                        RegisterError.EMAIL_TAKEN -> R.string.error_email_taken
                        RegisterError.NETWORK -> R.string.error_network
                        RegisterError.UNKNOWN -> R.string.error_unknown
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        EfPrimaryButton(
            text = stringResource(R.string.register_submit),
            onClick = { onEvent(RegisterUiEvent.Submit) },
            enabled = uiState.canSubmit,
            loading = uiState.isSubmitting,
        )
        TextButton(onClick = { onEvent(RegisterUiEvent.GoToLogin) }) {
            Text(stringResource(R.string.register_go_login))
        }
    }
}
