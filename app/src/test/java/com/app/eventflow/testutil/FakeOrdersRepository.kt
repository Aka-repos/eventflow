package com.app.eventflow.testutil

import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.catalog.Money
import com.app.eventflow.domain.model.orders.Order
import com.app.eventflow.domain.model.orders.OrderItem
import com.app.eventflow.domain.model.orders.OrderStatus
import com.app.eventflow.domain.model.orders.Ticket
import com.app.eventflow.domain.repository.OrdersRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

fun anOrder(id: String = "o1", status: OrderStatus = OrderStatus.PENDING) = Order(
    id = id,
    status = status,
    total = Money("80.00", "USD"),
    expiresAt = "2027-01-01T12:15:00Z",
    createdAt = "2027-01-01T12:00:00Z",
    items = listOf(OrderItem("i1", "TICKET", "VIP — Concierto", 2, Money("40.00", "USD"), emptyList())),
    payment = null,
)

class FakeOrdersRepository : OrdersRepository {

    var createResult: AppResult<Order> = AppResult.Success(anOrder())
    var payResult: AppResult<Order> = AppResult.Success(anOrder(status = OrderStatus.PAID))
    var cancelResult: AppResult<Order> = AppResult.Success(anOrder(status = OrderStatus.CANCELLED))
    var refreshError: AppError? = null
    val orders = MutableStateFlow<List<Order>>(emptyList())
    val tickets = MutableStateFlow<List<Ticket>>(emptyList())
    val createCalls = mutableListOf<Pair<String, Int>>()
    val payCalls = mutableListOf<String>()
    val cancelCalls = mutableListOf<String>()

    override suspend fun createOrder(ticketTypeId: String, quantity: Int): AppResult<Order> {
        createCalls += ticketTypeId to quantity
        return createResult
    }

    override suspend fun payOrder(orderId: String, method: String): AppResult<Order> {
        payCalls += orderId
        return payResult
    }

    override suspend fun cancelOrder(orderId: String): AppResult<Order> {
        cancelCalls += orderId
        return cancelResult
    }

    override fun observeOrders(): Flow<List<Order>> = orders

    override suspend fun refreshOrders(): AppResult<Unit> =
        refreshError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)

    override fun observeTickets(): Flow<List<Ticket>> = tickets

    override suspend fun refreshTickets(): AppResult<Unit> =
        refreshError?.let { AppResult.Failure(it) } ?: AppResult.Success(Unit)
}
