package com.app.eventflow.domain.usecase.orders

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.orders.Order
import com.app.eventflow.domain.model.orders.Ticket
import com.app.eventflow.domain.repository.OrdersRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CreateOrderUseCase @Inject constructor(private val repository: OrdersRepository) {
    suspend operator fun invoke(ticketTypeId: String, quantity: Int): AppResult<Order> =
        repository.createOrder(ticketTypeId, quantity)
}

class PayOrderUseCase @Inject constructor(private val repository: OrdersRepository) {
    suspend operator fun invoke(orderId: String, method: String): AppResult<Order> =
        repository.payOrder(orderId, method)
}

class CancelOrderUseCase @Inject constructor(private val repository: OrdersRepository) {
    suspend operator fun invoke(orderId: String): AppResult<Order> = repository.cancelOrder(orderId)
}

class ObserveOrdersUseCase @Inject constructor(private val repository: OrdersRepository) {
    operator fun invoke(): Flow<List<Order>> = repository.observeOrders()
}

class RefreshOrdersUseCase @Inject constructor(private val repository: OrdersRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refreshOrders()
}

class ObserveTicketsUseCase @Inject constructor(private val repository: OrdersRepository) {
    operator fun invoke(): Flow<List<Ticket>> = repository.observeTickets()
}

class RefreshTicketsUseCase @Inject constructor(private val repository: OrdersRepository) {
    suspend operator fun invoke(): AppResult<Unit> = repository.refreshTickets()
}
