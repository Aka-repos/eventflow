package com.app.eventflow.ui.feature.catalog.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.R
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.model.catalog.EventQuery
import com.app.eventflow.domain.model.catalog.EventSummary
import com.app.eventflow.domain.usecase.catalog.GetCategoriesUseCase
import com.app.eventflow.domain.usecase.catalog.SearchEventsUseCase
import com.app.eventflow.domain.usecase.catalog.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class CatalogListViewModel @Inject constructor(
    private val searchEvents: SearchEventsUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogListUiState())
    val state: StateFlow<CatalogListUiState> = _state.asStateFlow()

    private val _effects = Channel<CatalogListUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        loadCategories()
        search(reset = true)
        viewModelScope.launch {
            _state.map { it.query }.distinctUntilChanged().drop(1).debounce(350).collect {
                search(reset = true)
            }
        }
    }

    fun onEvent(event: CatalogListUiEvent) {
        when (event) {
            is CatalogListUiEvent.QueryChanged -> _state.update { copy(query = event.query) }
            is CatalogListUiEvent.CategorySelected -> {
                _state.update { copy(selectedCategoryId = event.categoryId) }
                search(reset = true)
            }
            CatalogListUiEvent.LoadMore -> search(reset = false)
            CatalogListUiEvent.Retry -> {
                loadCategories()
                search(reset = true)
            }
            is CatalogListUiEvent.EventClicked ->
                viewModelScope.launch { _effects.send(CatalogListUiEffect.NavigateToDetail(event.eventId)) }
            is CatalogListUiEvent.FavoriteToggled -> onFavoriteToggled(event.event)
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            getCategories().onSuccess { categories -> _state.update { copy(categories = categories) } }
        }
    }

    private fun search(reset: Boolean) {
        val current = _state.value
        if (!reset && current.nextCursor == null) {
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update {
                if (reset) copy(isLoading = true, hasError = false, isOffline = false)
                else copy(isLoadingMore = true)
            }
            val query = EventQuery(
                q = current.query,
                categoryId = current.selectedCategoryId,
                cursor = if (reset) null else current.nextCursor,
            )
            searchEvents(query)
                .onSuccess { page ->
                    _state.update {
                        copy(
                            events = if (reset) page.items else events + page.items,
                            nextCursor = page.nextCursor,
                            isLoading = false,
                            isLoadingMore = false,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        copy(
                            isLoading = false,
                            isLoadingMore = false,
                            hasError = reset,
                            isOffline = error is AppError.Network,
                        )
                    }
                }
        }
    }

    private fun onFavoriteToggled(event: EventSummary) {
        val newValue = event.isFavorite != true
        // Optimista en la lista visible; el repositorio garantiza cache + cola offline
        _state.update {
            copy(events = events.map { if (it.id == event.id) it.copy(isFavorite = newValue) else it })
        }
        viewModelScope.launch {
            toggleFavorite(event, newValue).onFailure {
                _state.update {
                    copy(events = events.map {
                        if (it.id == event.id) it.copy(isFavorite = !newValue) else it
                    })
                }
                _effects.send(CatalogListUiEffect.ShowMessage(R.string.error_unknown))
            }
        }
    }

    private inline fun MutableStateFlow<CatalogListUiState>.update(
        transform: CatalogListUiState.() -> CatalogListUiState,
    ) {
        value = value.transform()
    }
}
