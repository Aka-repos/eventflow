package com.app.eventflow.ui.feature.catalog.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.app.eventflow.domain.model.catalog.EventStatus
import com.app.eventflow.domain.model.catalog.EventSummary
import com.app.eventflow.ui.components.EfPrimaryButton

@Composable
fun CatalogListRoute(
    onNavigateToDetail: (String) -> Unit,
    viewModel: CatalogListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CatalogListUiEffect.NavigateToDetail -> onNavigateToDetail(effect.eventId)
                is CatalogListUiEffect.ShowMessage -> Unit // los errores visibles viven en el estado
            }
        }
    }
    CatalogListScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun CatalogListScreen(state: CatalogListUiState, onEvent: (CatalogListUiEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(CatalogListUiEvent.QueryChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.catalog_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )
        if (state.categories.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCategoryId == null,
                        onClick = { onEvent(CatalogListUiEvent.CategorySelected(null)) },
                        label = { Text(stringResource(R.string.catalog_all_categories)) },
                    )
                }
                items(state.categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = { onEvent(CatalogListUiEvent.CategorySelected(category.id)) },
                        label = { Text(category.name) },
                    )
                }
            }
        }
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.hasError -> ErrorState(
                message = stringResource(
                    if (state.isOffline) R.string.error_network else R.string.error_unknown,
                ),
                onRetry = { onEvent(CatalogListUiEvent.Retry) },
            )
            state.isEmpty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.catalog_empty), style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.events, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        onClick = { onEvent(CatalogListUiEvent.EventClicked(event.id)) },
                        onFavorite = { onEvent(CatalogListUiEvent.FavoriteToggled(event)) },
                    )
                }
                if (state.nextCursor != null) {
                    item {
                        TextButton(
                            onClick = { onEvent(CatalogListUiEvent.LoadMore) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoadingMore,
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                            } else {
                                Text(stringResource(R.string.catalog_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        EfPrimaryButton(text = stringResource(R.string.catalog_retry), onClick = onRetry)
    }
}

@Composable
fun EventCard(event: EventSummary, onClick: () -> Unit, onFavorite: (() -> Unit)?) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(event.venueName, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${event.category.name} · ${event.startsAt.take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    event.priceFrom?.let {
                        Text(
                            stringResource(R.string.catalog_price_from, it.display()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (event.status == EventStatus.SOLD_OUT) {
                        Text(
                            stringResource(R.string.catalog_sold_out),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            if (onFavorite != null) {
                val isFavorite = event.isFavorite == true
                IconButton(onClick = onFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (isFavorite) R.string.catalog_favorite_remove else R.string.catalog_favorite_add,
                        ),
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                        else AssistChipDefaults.assistChipColors().labelColor,
                    )
                }
            }
        }
    }
}
