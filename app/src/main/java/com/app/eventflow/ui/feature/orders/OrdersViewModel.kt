package com.app.eventflow.ui.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.domain.usecase.orders.CancelOrderUseCase
import com.app.eventflow.domain.usecase.orders.ObserveOrdersUseCase
import com.app.eventflow.domain.usecase.orders.PayOrderUseCase
import com.app.eventflow.domain.usecase.orders.RefreshOrdersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    observeOrders: ObserveOrdersUseCase,
    private val refreshOrders: RefreshOrdersUseCase,
    private val payOrder: PayOrderUseCase,
    private val cancelOrder: CancelOrderUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    private val _effects = Channel<OrdersUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeOrders().collect { orders ->
                _state.value = _state.value.copy(orders = orders)
            }
        }
        onEvent(OrdersUiEvent.Refresh)
    }

    fun onEvent(event: OrdersUiEvent) {
        when (event) {
            OrdersUiEvent.Refresh -> refresh()
            // sacudida: avisa al usuario y reutiliza el mismo refresh (GET /orders?limit=50)
            OrdersUiEvent.ShakeRefresh -> {
                if (_state.value.isRefreshing) return
                viewModelScope.launch { _effects.send(OrdersUiEffect.ShowMessage("Actualizando órdenes…")) }
                refresh()
            }
            is OrdersUiEvent.PayClicked -> mutate(event.orderId) { payOrder(event.orderId, "FAKE") }
            is OrdersUiEvent.CancelClicked -> mutate(event.orderId) { cancelOrder(event.orderId) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = true)
            val result = refreshOrders()
            _state.value = _state.value.copy(
                isRefreshing = false,
                isOffline = result is AppResult.Failure && result.error is AppError.Network,
            )
        }
    }

    private fun mutate(orderId: String, operation: suspend () -> AppResult<*>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(processingOrderId = orderId)
            operation().onFailure { error ->
                val message = when (error) {
                    is AppError.Business -> error.detail ?: error.code
                    is AppError.Conflict -> error.code
                    is AppError.Network -> "Sin conexión"
                    else -> "Algo salió mal"
                }
                _effects.send(OrdersUiEffect.ShowMessage(message))
            }
            _state.value = _state.value.copy(processingOrderId = null)
        }
    }
}
