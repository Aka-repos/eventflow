package com.app.eventflow.ui.feature.refunds.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.eventflow.R
import com.app.eventflow.domain.model.refunds.RecoveryOption
import com.app.eventflow.domain.model.refunds.RecoveryOptions
import com.app.eventflow.ui.components.EfPrimaryButton

@Composable
fun RecoveryRoute(onNavigateBack: () -> Unit, viewModel: RecoveryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect {
            when (it) {
                RecoveryUiEffect.NavigateBack -> onNavigateBack()
                is RecoveryUiEffect.ShowMessage -> snackbar.showSnackbar(it.message)
            }
        }
    }
    RecoveryScreen(state = state, snackbar = snackbar, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryScreen(
    state: RecoveryUiState,
    snackbar: SnackbarHostState,
    onEvent: (RecoveryUiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recovery_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(RecoveryUiEvent.BackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                state.isLoading -> CircularProgressIndicator()
                state.offline -> CenteredMessage(stringResource(R.string.recovery_offline))
                state.error != null -> CenteredMessage(state.error)
                state.options != null -> RecoveryBody(state, onEvent)
            }
        }
    }
}

@Composable
private fun RecoveryBody(state: RecoveryUiState, onEvent: (RecoveryUiEvent) -> Unit) {
    val options = state.options!!
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OptionCard(options)
        if (options.option == RecoveryOption.REFUND) {
            OutlinedTextField(
                value = state.reason,
                onValueChange = { onEvent(RecoveryUiEvent.ReasonChanged(it)) },
                label = { Text(stringResource(R.string.recovery_reason_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 2,
            )
            EfPrimaryButton(
                text = stringResource(R.string.recovery_request_refund),
                onClick = { onEvent(RecoveryUiEvent.RequestRefund) },
                loading = state.submitting,
            )
        }
    }
}

@Composable
private fun OptionCard(options: RecoveryOptions) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (options.option) {
                RecoveryOption.REFUND -> {
                    Text(stringResource(R.string.recovery_option_refund_title),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(
                        R.string.recovery_option_refund_body,
                        options.refund?.amount?.formatted() ?: "—"))
                }
                RecoveryOption.EXCHANGE -> {
                    Text(stringResource(R.string.recovery_option_exchange_title),
                        style = MaterialTheme.typography.titleMedium)
                    val ex = options.exchange
                    Text(stringResource(
                        R.string.recovery_option_exchange_body,
                        ex?.listPrice?.formatted() ?: "—",
                        ex?.originalPrice?.formatted() ?: "—",
                        ex?.depreciationPct ?: 0))
                }
                RecoveryOption.NONE -> {
                    Text(stringResource(R.string.recovery_option_none_title),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.recovery_option_none_body))
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Text(text, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
}
