package com.app.eventflow.ui.feature.catalog.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.eventflow.R
import com.app.eventflow.ui.feature.catalog.list.EventCard

@Composable
fun FavoritesRoute(
    onNavigateToDetail: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FavoritesUiEffect.NavigateToDetail -> onNavigateToDetail(effect.eventId)
            }
        }
    }
    FavoritesScreen(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun FavoritesScreen(state: FavoritesUiState, onEvent: (FavoritesUiEvent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (state.isRefreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.isOffline) {
            Text(
                stringResource(R.string.error_network),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.catalog_favorites_empty),
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
                items(state.favorites, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        onClick = { onEvent(FavoritesUiEvent.EventClicked(event.id)) },
                        onFavorite = { onEvent(FavoritesUiEvent.FavoriteRemoved(event)) },
                    )
                }
            }
        }
    }
}
