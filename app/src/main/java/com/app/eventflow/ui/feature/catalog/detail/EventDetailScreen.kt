package com.app.eventflow.ui.feature.catalog.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.app.eventflow.domain.model.catalog.EventDetail
import com.app.eventflow.ui.components.EfPrimaryButton

@Composable
fun EventDetailRoute(
    onNavigateBack: () -> Unit,
    onNavigateToCheckout: (eventId: String, tariffId: String) -> Unit = { _, _ -> },
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EventDetailUiEffect.NavigateBack -> onNavigateBack()
                is EventDetailUiEffect.ShowMessage -> Unit
            }
        }
    }
    EventDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBuy = { tariffId ->
            state.detail?.let { onNavigateToCheckout(it.summary.id, tariffId) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(state: EventDetailUiState, onEvent: (EventDetailUiEvent) -> Unit, onBuy: (String) -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.summary?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(EventDetailUiEvent.BackClicked) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
                actions = {
                    state.detail?.let { detail ->
                        val isFavorite = detail.summary.isFavorite == true
                        IconButton(onClick = { onEvent(EventDetailUiEvent.FavoriteToggled) }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite
                                else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (isFavorite) R.string.catalog_favorite_remove
                                    else R.string.catalog_favorite_add,
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.notFound -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.detail_event_not_found)) }

            state.hasError -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.error_unknown))
                EfPrimaryButton(
                    text = stringResource(R.string.catalog_retry),
                    onClick = { onEvent(EventDetailUiEvent.Retry) },
                )
            }

            state.detail != null -> DetailContent(
                detail = state.detail,
                onBuy = onBuy,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DetailContent(detail: EventDetail, onBuy: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(detail.summary.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "${detail.summary.venueName} · ${detail.summary.startsAt.take(16).replace('T', ' ')} " +
                "(${detail.summary.timezone})",
            style = MaterialTheme.typography.bodyMedium,
        )
        detail.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(
            stringResource(R.string.detail_organized_by, detail.organizer.name),
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text(detail.description, style = MaterialTheme.typography.bodyMedium)

        Text(stringResource(R.string.detail_tickets), style = MaterialTheme.typography.titleMedium)
        detail.ticketTypes.forEach { ticket ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ticket.name, style = MaterialTheme.typography.titleSmall)
                        ticket.zoneName?.let {
                            Text(
                                stringResource(R.string.detail_zone, it),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            stringResource(
                                if (ticket.available) R.string.detail_available
                                else R.string.detail_not_available,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ticket.available) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(ticket.price.display(), style = MaterialTheme.typography.titleMedium)
                        if (ticket.available) {
                            androidx.compose.material3.TextButton(onClick = { onBuy(ticket.id) }) {
                                Text(stringResource(R.string.detail_buy))
                            }
                        }
                    }
                }
            }
        }

        Text(stringResource(R.string.detail_policies), style = MaterialTheme.typography.titleMedium)
        val policies = detail.policies
        Text(
            policies.refundWindowEndsAt?.let {
                stringResource(R.string.detail_policy_refund_until, it.take(10))
            } ?: stringResource(R.string.detail_policy_no_refund),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (policies.exchangeEnabled) {
                stringResource(R.string.detail_policy_exchange_on, policies.exchangeDepreciationPct)
            } else {
                stringResource(R.string.detail_policy_exchange_off)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (policies.waitlistEnabled) {
            Text(
                stringResource(R.string.detail_policy_waitlist_on),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            stringResource(R.string.detail_policy_qr, policies.qrVisibilityHoursBefore),
            style = MaterialTheme.typography.bodySmall,
        )

        if (detail.sponsors.isNotEmpty()) {
            Text(stringResource(R.string.detail_sponsors), style = MaterialTheme.typography.titleMedium)
            detail.sponsors.forEach { Text("· ${it.name}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
