package com.app.eventflow.ui.feature.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
fun LoginRoute(
    onNavigateHome: () -> Unit,
    onNavigateRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LoginUiEffect.NavigateHome -> onNavigateHome()
                LoginUiEffect.NavigateRegister -> onNavigateRegister()
            }
        }
    }
    LoginScreen(uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
fun LoginScreen(uiState: LoginUiState, onEvent: (LoginUiEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)

        EfTextField(
            value = uiState.email,
            onValueChange = { onEvent(LoginUiEvent.EmailChanged(it)) },
            label = stringResource(R.string.field_email),
            error = uiState.emailError,
            keyboardType = KeyboardType.Email,
            enabled = !uiState.isSubmitting,
        )
        EfPasswordField(
            value = uiState.password,
            onValueChange = { onEvent(LoginUiEvent.PasswordChanged(it)) },
            label = stringResource(R.string.field_password),
            error = uiState.passwordError,
            enabled = !uiState.isSubmitting,
        )

        uiState.generalError?.let { error ->
            Text(
                text = stringResource(
                    when (error) {
                        LoginError.INVALID_CREDENTIALS -> R.string.error_invalid_credentials
                        LoginError.ACCOUNT_BLOCKED -> R.string.error_account_blocked
                        LoginError.NETWORK -> R.string.error_network
                        LoginError.UNKNOWN -> R.string.error_unknown
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        EfPrimaryButton(
            text = stringResource(R.string.login_submit),
            onClick = { onEvent(LoginUiEvent.Submit) },
            enabled = uiState.canSubmit,
            loading = uiState.isSubmitting,
        )
        TextButton(onClick = { onEvent(LoginUiEvent.GoToRegister) }) {
            Text(stringResource(R.string.login_go_register))
        }
    }
}
