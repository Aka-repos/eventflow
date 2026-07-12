package com.app.eventflow.ui.feature.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.onFailure
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.domain.usecase.catalog.GetEventDetailUseCase
import com.app.eventflow.domain.usecase.orders.CancelOrderUseCase
import com.app.eventflow.domain.usecase.orders.CreateOrderUseCase
import com.app.eventflow.domain.usecase.orders.PayOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEventDetail: GetEventDetailUseCase,
    private val createOrder: CreateOrderUseCase,
    private val payOrder: PayOrderUseCase,
    private val cancelOrder: CancelOrderUseCase,
) : ViewModel() {

    private val eventId: String = requireNotNull(savedStateHandle["eventId"])
    private val tariffId: String = requireNotNull(savedStateHandle["tariffId"])
    private val quantity: Int = requireNotNull(savedStateHandle.get<String>("quantity")).toInt()

    private val _state = MutableStateFlow(CheckoutUiState(quantity = quantity))
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    private val _effects = Channel<CheckoutUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            getEventDetail(eventId)
                .onSuccess { detail ->
                    val tariff = detail.ticketTypes.firstOrNull { it.id == tariffId }
                    _state.value = _state.value.copy(
                        eventTitle = detail.summary.title,
                        tariff = tariff,
                        isLoading = false,
                        fatalError = tariff == null,
                    )
                }
                .onFailure { _state.value = _state.value.copy(isLoading = false, fatalError = true) }
        }
    }

    fun onEvent(event: CheckoutUiEvent) {
        when (event) {
            is CheckoutUiEvent.QuantityChanged -> {
                if (_state.value.order == null && event.quantity in 1..10) {
                    _state.value = _state.value.copy(quantity = event.quantity)
                }
            }
            CheckoutUiEvent.ConfirmOrder -> confirmOrder()
            CheckoutUiEvent.Pay -> pay()
            CheckoutUiEvent.CancelOrder -> cancel()
            CheckoutUiEvent.BackClicked ->
                viewModelScope.launch { _effects.send(CheckoutUiEffect.NavigateBack) }
        }
    }

    private fun confirmOrder() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, paymentError = null, soldOut = false)
            createOrder(tariffId, _state.value.quantity)
                .onSuccess { order ->
                    _state.value = _state.value.copy(order = order, isProcessing = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        soldOut = error is AppError.Conflict && error.code == "event_sold_out",
                        paymentError = if (error is AppError.Conflict && error.code == "event_sold_out") {
                            null
                        } else {
                            describe(error)
                        },
                    )
                }
        }
    }

    private fun pay() {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, paymentError = null)
            payOrder(order.id, "FAKE")
                .onSuccess {
                    _state.value = _state.value.copy(isProcessing = false)
                    _effects.send(CheckoutUiEffect.NavigateToTickets)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(isProcessing = false, paymentError = describe(error))
                }
        }
    }

    private fun cancel() {
        val order = _state.value.order ?: return
        viewModelScope.launch {
            cancelOrder(order.id)
            _effects.send(CheckoutUiEffect.NavigateBack)
        }
    }

    private fun describe(error: AppError): String = when (error) {
        is AppError.Business -> error.detail ?: error.code
        is AppError.Conflict -> when (error.code) {
            "order_expired" -> "La orden expiró; el inventario fue liberado"
            "order_not_pending" -> "La orden ya no está pendiente"
            else -> error.code
        }
        is AppError.Network -> "Sin conexión. La compra nunca se encola offline"
        else -> "Algo salió mal. Intenta de nuevo"
    }
}
