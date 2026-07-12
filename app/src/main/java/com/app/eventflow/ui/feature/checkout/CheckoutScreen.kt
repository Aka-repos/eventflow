package com.app.eventflow.ui.feature.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.eventflow.R
import com.app.eventflow.ui.components.EfPrimaryButton

@Composable
fun CheckoutRoute(
    onNavigateToTickets: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                CheckoutUiEffect.NavigateToTickets -> onNavigateToTickets()
                CheckoutUiEffect.NavigateBack -> onNavigateBack()
            }
        }
    }
    CheckoutScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(state: CheckoutUiState, onEvent: (CheckoutUiEvent) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkout_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(CheckoutUiEvent.BackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.fatalError -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.detail_event_not_found)) }

            else -> Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(state.eventTitle, style = MaterialTheme.typography.titleLarge)
                state.tariff?.let { tariff ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(tariff.name, style = MaterialTheme.typography.titleMedium)
                            tariff.zoneName?.let {
                                Text(stringResource(R.string.detail_zone, it),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(tariff.price.display(), style = MaterialTheme.typography.titleMedium)
                                if (state.order == null) {
                                    TextButton(onClick = {
                                        onEvent(CheckoutUiEvent.QuantityChanged(state.quantity - 1))
                                    }) { Text("−") }
                                    Text("${state.quantity}", style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = {
                                        onEvent(CheckoutUiEvent.QuantityChanged(state.quantity + 1))
                                    }) { Text("+") }
                                } else {
                                    Text(stringResource(R.string.checkout_quantity, state.quantity))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.checkout_total), style = MaterialTheme.typography.titleMedium)
                    Text(state.totalLabel, style = MaterialTheme.typography.titleLarge)
                }

                state.order?.let { order ->
                    Text(
                        stringResource(R.string.checkout_expires, order.expiresAt.take(16).replace('T', ' ')),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.soldOut) {
                    Text(stringResource(R.string.checkout_sold_out),
                        color = MaterialTheme.colorScheme.error)
                }
                state.paymentError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                if (state.order == null) {
                    EfPrimaryButton(
                        text = if (state.isProcessing) stringResource(R.string.checkout_processing)
                        else stringResource(R.string.checkout_confirm),
                        onClick = { if (!state.isProcessing) onEvent(CheckoutUiEvent.ConfirmOrder) },
                    )
                } else {
                    EfPrimaryButton(
                        text = if (state.isProcessing) stringResource(R.string.checkout_processing)
                        else stringResource(R.string.checkout_pay),
                        onClick = { if (!state.isProcessing) onEvent(CheckoutUiEvent.Pay) },
                    )
                    OutlinedButton(
                        onClick = { onEvent(CheckoutUiEvent.CancelOrder) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.checkout_cancel_order)) }
                }
            }
        }
    }
}
