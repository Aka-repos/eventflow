package com.app.eventflow.ui.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.eventflow.R
import com.app.eventflow.ui.components.EfPrimaryButton

@Composable
fun ProfileRoute(onNavigateBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect {
            when (it) {
                ProfileUiEffect.NavigateBack -> onNavigateBack()
                is ProfileUiEffect.ShowMessage -> snackbar.showSnackbar(it.message)
            }
        }
    }
    ProfileScreen(state = state, snackbar = snackbar, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    snackbar: SnackbarHostState,
    onEvent: (ProfileUiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(ProfileUiEvent.BackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Solo lectura: correo y roles (no editables por contrato)
            Text(stringResource(R.string.profile_email, state.email),
                style = MaterialTheme.typography.bodyMedium)
            if (state.roles.isNotBlank()) {
                Text(stringResource(R.string.profile_roles, state.roles),
                    style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = state.fullName,
                onValueChange = { onEvent(ProfileUiEvent.FullNameChanged(it)) },
                label = { Text(stringResource(R.string.profile_full_name)) },
                isError = state.fullNameError != null,
                supportingText = state.fullNameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.phone,
                onValueChange = { onEvent(ProfileUiEvent.PhoneChanged(it)) },
                label = { Text(stringResource(R.string.profile_phone)) },
                isError = state.phoneError != null,
                supportingText = state.phoneError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            EfPrimaryButton(
                text = stringResource(R.string.profile_save),
                onClick = { onEvent(ProfileUiEvent.Save) },
                enabled = state.canSave,
                loading = state.saving,
            )
        }
    }
}
