package com.app.eventflow.ui.feature.catalog.detail

import com.app.eventflow.domain.model.catalog.EventDetail

data class EventDetailUiState(
    val detail: EventDetail? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val hasError: Boolean = false,
)

sealed interface EventDetailUiEvent {
    data object Retry : EventDetailUiEvent
    data object FavoriteToggled : EventDetailUiEvent
    data object BackClicked : EventDetailUiEvent
}

sealed interface EventDetailUiEffect {
    data object NavigateBack : EventDetailUiEffect
    data class ShowMessage(val messageRes: Int) : EventDetailUiEffect
}
