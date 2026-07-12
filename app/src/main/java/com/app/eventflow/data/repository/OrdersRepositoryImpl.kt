package com.app.eventflow.data.repository

import com.app.eventflow.core.di.IoDispatcher
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.core.network.map
import com.app.eventflow.core.network.onSuccess
import com.app.eventflow.core.network.safeApiCall
import com.app.eventflow.data.local.OrdersDao
import com.app.eventflow.data.mapper.toDomain
import com.app.eventflow.data.mapper.toEntity
import com.app.eventflow.data.remote.api.OrdersApi
import com.app.eventflow.data.remote.dto.orders.CreateOrderRequestDto
import com.app.eventflow.data.remote.dto.orders.OrderItemRequestDto
import com.app.eventflow.data.remote.dto.orders.OrderResponseDto
import com.app.eventflow.data.remote.dto.orders.PayOrderRequestDto
import com.app.eventflow.domain.model.orders.Order
import com.app.eventflow.domain.model.orders.Ticket
import com.app.eventflow.domain.repository.OrdersRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrdersRepositoryImpl @Inject constructor(
    private val api: OrdersApi,
    private val dao: OrdersDao,
    private val converter: ProblemConverter,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : OrdersRepository {

    override suspend fun createOrder(ticketTypeId: String, quantity: Int): AppResult<Order> =
        safeApiCall(dispatcher, converter) {
            api.createOrder(
                idempotencyKey = UUID.randomUUID().toString(),
                request = CreateOrderRequestDto(listOf(OrderItemRequestDto("TICKET", ticketTypeId, quantity))),
            )
        }.map { it.data.toDomain() }
            .onSuccess { cacheOrder(it) }

    override suspend fun payOrder(orderId: String, method: String): AppResult<Order> =
        safeApiCall(dispatcher, converter) {
            api.payOrder(orderId, UUID.randomUUID().toString(), PayOrderRequestDto(method))
        }.map { it.data.toDomain() }
            .onSuccess {
                cacheOrder(it)
                refreshTickets()
            }

    override suspend fun cancelOrder(orderId: String): AppResult<Order> =
        safeApiCall(dispatcher, converter) { api.cancelOrder(orderId) }
            .map { it.data.toDomain() }
            .onSuccess { cacheOrder(it) }

    override fun observeOrders(): Flow<List<Order>> =
        dao.observeOrders().map { rows -> rows.map { it.toDomain() } }

    override suspend fun refreshOrders(): AppResult<Unit> =
        safeApiCall(dispatcher, converter) { api.listOrders(limit = 50) }.map { page ->
            dao.clearOrders()
            dao.upsertOrders(page.data.map { it.toEntity() })
        }

    override fun observeTickets(): Flow<List<Ticket>> =
        dao.observeTickets().map { rows -> rows.map { it.toDomain() } }

    override suspend fun refreshTickets(): AppResult<Unit> =
        safeApiCall(dispatcher, converter) { api.listTickets(limit = 100) }.map { page ->
            dao.clearTickets()
            dao.upsertTickets(page.data.map { it.toEntity() })
        }

    private suspend fun cacheOrder(order: Order) {
        // re-sincroniza el historial tras cada mutación (fuente de verdad: servidor)
        refreshOrders()
        // el parámetro evita cachear estados parciales sin red; Room ya quedó actualizado por refresh
        order.id
    }
}
