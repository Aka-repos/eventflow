package com.app.eventflow.ui.feature.catalog.list

import com.app.eventflow.domain.model.catalog.Category
import com.app.eventflow.domain.model.catalog.EventSummary

/** Tríada UiState/UiEvent/UiEffect (docs/engineering/03 §2). */

data class CatalogListUiState(
    val query: String = "",
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Int? = null,
    val events: List<EventSummary> = emptyList(),
    val nextCursor: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOffline: Boolean = false,
    val hasError: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && !hasError && events.isEmpty()
    val canLoadMore: Boolean get() = nextCursor != null && !isLoadingMore
}

sealed interface CatalogListUiEvent {
    data class QueryChanged(val query: String) : CatalogListUiEvent
    data class CategorySelected(val categoryId: Int?) : CatalogListUiEvent
    data object LoadMore : CatalogListUiEvent
    data object Retry : CatalogListUiEvent
    data class EventClicked(val eventId: String) : CatalogListUiEvent
    data class FavoriteToggled(val event: EventSummary) : CatalogListUiEvent
}

sealed interface CatalogListUiEffect {
    data class NavigateToDetail(val eventId: String) : CatalogListUiEffect
    data class ShowMessage(val messageRes: Int) : CatalogListUiEffect
}
