package com.app.eventflow.ui.feature.catalog.favorites

import com.app.eventflow.domain.model.catalog.EventSummary

data class FavoritesUiState(
    val favorites: List<EventSummary> = emptyList(),
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
) {
    val isEmpty: Boolean get() = favorites.isEmpty()
}

sealed interface FavoritesUiEvent {
    data object Refresh : FavoritesUiEvent
    data class EventClicked(val eventId: String) : FavoritesUiEvent
    data class FavoriteRemoved(val event: EventSummary) : FavoritesUiEvent
}

sealed interface FavoritesUiEffect {
    data class NavigateToDetail(val eventId: String) : FavoritesUiEffect
}
