package com.app.eventflow.ui.feature.catalog.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.usecase.catalog.ObserveFavoritesUseCase
import com.app.eventflow.domain.usecase.catalog.RefreshFavoritesUseCase
import com.app.eventflow.domain.usecase.catalog.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Favoritos offline-first: Room es la fuente de verdad visible; el refresh drena la cola y resincroniza. */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    observeFavorites: ObserveFavoritesUseCase,
    private val refreshFavorites: RefreshFavoritesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    private val _effects = Channel<FavoritesUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeFavorites().collect { favorites ->
                _state.value = _state.value.copy(favorites = favorites)
            }
        }
        onEvent(FavoritesUiEvent.Refresh)
    }

    fun onEvent(event: FavoritesUiEvent) {
        when (event) {
            FavoritesUiEvent.Refresh -> refresh()
            is FavoritesUiEvent.EventClicked ->
                viewModelScope.launch { _effects.send(FavoritesUiEffect.NavigateToDetail(event.eventId)) }
            is FavoritesUiEvent.FavoriteRemoved ->
                viewModelScope.launch { toggleFavorite(event.event, false) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            val result = refreshFavorites()
            _state.value = _state.value.copy(
                isRefreshing = false,
                isOffline = result is AppResult.Failure && result.error is AppError.Network,
            )
        }
    }
}
