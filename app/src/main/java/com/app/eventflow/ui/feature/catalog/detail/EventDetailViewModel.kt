package com.app.eventflow.ui.feature.catalog.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.R
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.usecase.catalog.GetEventDetailUseCase
import com.app.eventflow.domain.usecase.catalog.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEventDetail: GetEventDetailUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val eventId: String = requireNotNull(savedStateHandle["eventId"]) {
        "La ruta de detalle requiere eventId"
    }

    private val _state = MutableStateFlow(EventDetailUiState())
    val state: StateFlow<EventDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<EventDetailUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        load()
    }

    fun onEvent(event: EventDetailUiEvent) {
        when (event) {
            EventDetailUiEvent.Retry -> load()
            EventDetailUiEvent.FavoriteToggled -> onFavoriteToggled()
            EventDetailUiEvent.BackClicked ->
                viewModelScope.launch { _effects.send(EventDetailUiEffect.NavigateBack) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, hasError = false, notFound = false)
            getEventDetail(eventId)
                .onSuccess { detail ->
                    _state.value = EventDetailUiState(detail = detail, isLoading = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        notFound = error is AppError.NotFound,
                        hasError = error !is AppError.NotFound,
                    )
                }
        }
    }

    private fun onFavoriteToggled() {
        val detail = _state.value.detail ?: return
        val newValue = detail.summary.isFavorite != true
        _state.value = _state.value.copy(
            detail = detail.copy(summary = detail.summary.copy(isFavorite = newValue)),
        )
        viewModelScope.launch {
            toggleFavorite(detail.summary, newValue).onFailure {
                _state.value = _state.value.copy(detail = detail)
                _effects.send(EventDetailUiEffect.ShowMessage(R.string.error_unknown))
            }
        }
    }
}
