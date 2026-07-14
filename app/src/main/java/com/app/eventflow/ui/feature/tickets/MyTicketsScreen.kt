package com.app.eventflow.ui.feature.tickets

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.eventflow.R
import com.app.eventflow.domain.model.orders.Ticket
import com.app.eventflow.domain.model.orders.TicketStatus

@Composable
fun MyTicketsRoute(
    onNavigateToQr: (String) -> Unit = {},
    onNavigateToRecovery: (String) -> Unit = {},
    viewModel: MyTicketsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyTicketsScreen(state = state, onShowQr = onNavigateToQr, onRecover = onNavigateToRecovery)
}

@Composable
fun MyTicketsScreen(
    state: MyTicketsUiState,
    onShowQr: (String) -> Unit = {},
    onRecover: (String) -> Unit = {},
) {
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
                Text(
                    stringResource(R.string.tickets_empty),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.tickets, key = { it.id }) { ticket ->
                    TicketCard(ticket, onShowQr = onShowQr, onRecover = onRecover)
                }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: Ticket, onShowQr: (String) -> Unit = {}, onRecover: (String) -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(ticket.event?.title ?: "", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ticket.ticketTypeName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    ticket.status.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ticket.status == TicketStatus.ACTIVE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            ticket.zoneName?.let {
                Text(stringResource(R.string.detail_zone, it), style = MaterialTheme.typography.bodySmall)
            }
            ticket.event?.let {
                Text("${it.venueName} · ${it.startsAt.take(16).replace('T', ' ')}",
                    style = MaterialTheme.typography.bodySmall)
            }
            ticket.qrAvailableAt?.let {
                Text(stringResource(R.string.tickets_qr_available_at, it.take(16).replace('T', ' ')),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (ticket.status == TicketStatus.ACTIVE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    androidx.compose.material3.TextButton(onClick = { onShowQr(ticket.id) }) {
                        Text(stringResource(R.string.ticket_show_qr))
                    }
                    if (ticket.canRecover) {
                        androidx.compose.material3.TextButton(onClick = { onRecover(ticket.id) }) {
                            Text(stringResource(R.string.recovery_title))
                        }
                    }
                }
            }
        }
    }
}
