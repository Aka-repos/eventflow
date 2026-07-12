package com.app.eventflow.ui.feature.tickets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.usecase.orders.ObserveTicketsUseCase
import com.app.eventflow.domain.usecase.orders.RefreshTicketsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mis boletos offline-first: Room es la fuente visible; refresh re-sincroniza (api/09 §3). */
@HiltViewModel
class MyTicketsViewModel @Inject constructor(
    observeTickets: ObserveTicketsUseCase,
    private val refreshTickets: RefreshTicketsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(MyTicketsUiState())
    val state: StateFlow<MyTicketsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeTickets().collect { tickets ->
                _state.value = _state.value.copy(tickets = tickets)
            }
        }
        onEvent(MyTicketsUiEvent.Refresh)
    }

    fun onEvent(event: MyTicketsUiEvent) {
        when (event) {
            MyTicketsUiEvent.Refresh -> viewModelScope.launch {
                _state.value = _state.value.copy(isRefreshing = true)
                val result = refreshTickets()
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    isOffline = result is AppResult.Failure && result.error is AppError.Network,
                )
            }
        }
    }
}
