package com.app.eventflow.ui.feature.refunds.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.eventflow.R
import com.app.eventflow.domain.model.refunds.RefundRequest
import com.app.eventflow.domain.model.refunds.RefundStatus

@Composable
fun RefundInboxRoute(onNavigateBack: () -> Unit, viewModel: RefundInboxViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effects.collect {
            when (it) {
                RefundInboxUiEffect.NavigateBack -> onNavigateBack()
                is RefundInboxUiEffect.ShowMessage -> snackbar.showSnackbar(it.message)
            }
        }
    }
    RefundInboxScreen(state = state, snackbar = snackbar, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefundInboxScreen(
    state: RefundInboxUiState,
    snackbar: SnackbarHostState,
    onEvent: (RefundInboxUiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.refund_inbox_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(RefundInboxUiEvent.BackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.offline -> Centered(stringResource(R.string.refund_inbox_offline))
                state.error != null -> Centered(state.error)
                state.items.isEmpty() -> Centered(stringResource(R.string.refund_inbox_empty))
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { refund ->
                        RefundCard(refund, state.actioningId == refund.id, onEvent)
                    }
                }
            }
        }
    }

    if (state.rejectingId != null) {
        AlertDialog(
            onDismissRequest = { onEvent(RefundInboxUiEvent.DismissReject) },
            title = { Text(stringResource(R.string.refund_reject)) },
            text = {
                OutlinedTextField(
                    value = state.rejectReason,
                    onValueChange = { onEvent(RefundInboxUiEvent.RejectReasonChanged(it)) },
                    label = { Text(stringResource(R.string.refund_reject_reason_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(RefundInboxUiEvent.ConfirmReject) },
                    enabled = state.rejectReason.isNotBlank(),
                ) { Text(stringResource(R.string.refund_reject_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(RefundInboxUiEvent.DismissReject) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RefundCard(refund: RefundRequest, busy: Boolean, onEvent: (RefundInboxUiEvent) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.refund_amount_label, refund.amount.formatted()),
                    style = MaterialTheme.typography.titleMedium,
                )
                AssistChip(onClick = {}, label = { Text(statusLabel(refund.status)) })
            }
            refund.reason?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            if (refund.status == RefundStatus.REQUESTED) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { onEvent(RefundInboxUiEvent.StartReject(refund.id)) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.refund_reject)) }
                    androidx.compose.material3.Button(
                        onClick = { onEvent(RefundInboxUiEvent.Approve(refund.id)) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.refund_approve))
                    }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: RefundStatus): String = stringResource(
    when (status) {
        RefundStatus.REQUESTED -> R.string.refund_status_requested
        RefundStatus.APPROVED -> R.string.refund_status_approved
        RefundStatus.REJECTED -> R.string.refund_status_rejected
        RefundStatus.CANCELLED -> R.string.refund_status_cancelled
        RefundStatus.UNKNOWN -> R.string.refund_status_requested
    },
)

@Composable
private fun BoxScope.Centered(text: String) {
    Text(text, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(24.dp))
}
