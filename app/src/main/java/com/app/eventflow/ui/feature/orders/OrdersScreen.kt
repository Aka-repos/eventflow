package com.app.eventflow.ui.feature.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.app.eventflow.core.sensor.ShakeEffect
import com.app.eventflow.domain.model.orders.Order
import com.app.eventflow.domain.model.orders.OrderStatus

@Composable
fun OrdersRoute(viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OrdersUiEffect.ShowMessage -> snackbar.showSnackbar(effect.message)
            }
        }
    }
    // Sensor: sacudir el teléfono refresca las órdenes (el detector solo cablea; la reacción está en el VM)
    ShakeEffect { viewModel.onEvent(OrdersUiEvent.ShakeRefresh) }
    Box(Modifier.fillMaxSize()) {
        OrdersScreen(state = state, onEvent = viewModel::onEvent)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun OrdersScreen(state: OrdersUiState, onEvent: (OrdersUiEvent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (state.isRefreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.isOffline) {
            Text(
                stringResource(R.string.tickets_offline_notice),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.orders_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.orders, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        isProcessing = state.processingOrderId == order.id,
                        onPay = { onEvent(OrdersUiEvent.PayClicked(order.id)) },
                        onCancel = { onEvent(OrdersUiEvent.CancelClicked(order.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, isProcessing: Boolean, onPay: () -> Unit, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(order.createdAt.take(16).replace('T', ' '),
                    style = MaterialTheme.typography.bodySmall)
                Text(
                    order.status.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = when (order.status) {
                        OrderStatus.PAID -> MaterialTheme.colorScheme.primary
                        OrderStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
            order.items.forEach { item ->
                Text("${item.quantity} × ${item.description}", style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.checkout_total), style = MaterialTheme.typography.bodyMedium)
                Text(order.total.display(), style = MaterialTheme.typography.titleMedium)
            }
            if (order.status == OrderStatus.PENDING && !isProcessing) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPay) { Text(stringResource(R.string.checkout_pay)) }
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.orders_cancel))
                    }
                }
            }
        }
    }
}
