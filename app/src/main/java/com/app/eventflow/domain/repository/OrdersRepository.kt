package com.app.eventflow.domain.repository

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.orders.Order
import com.app.eventflow.domain.model.orders.Ticket
import kotlinx.coroutines.flow.Flow

interface OrdersRepository {

    /** ⚡ genera Idempotency-Key por intento (api/01 §7). JAMÁS se encola offline (api/09). */
    suspend fun createOrder(ticketTypeId: String, quantity: Int): AppResult<Order>

    suspend fun payOrder(orderId: String, method: String): AppResult<Order>

    suspend fun cancelOrder(orderId: String): AppResult<Order>

    /** Historial cacheado en Room (lectura offline, api/09 §3). */
    fun observeOrders(): Flow<List<Order>>

    suspend fun refreshOrders(): AppResult<Unit>

    /** Mis boletos cacheados en Room (lectura offline). */
    fun observeTickets(): Flow<List<Ticket>>

    suspend fun refreshTickets(): AppResult<Unit>
}
