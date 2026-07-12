package com.app.eventflow.data.remote.api

import com.app.eventflow.data.remote.dto.DataEnvelope
import com.app.eventflow.data.remote.dto.orders.CreateOrderRequestDto
import com.app.eventflow.data.remote.dto.orders.OrderResponseDto
import com.app.eventflow.data.remote.dto.orders.OrdersPageDto
import com.app.eventflow.data.remote.dto.orders.PayOrderRequestDto
import com.app.eventflow.data.remote.dto.orders.TicketsPageDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Métodos = operationId (tags orders/tickets). ⚡ = Idempotency-Key obligatorio. */
interface OrdersApi {

    @POST("orders")
    suspend fun createOrder(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateOrderRequestDto,
    ): DataEnvelope<OrderResponseDto>

    @POST("orders/{orderId}/pay")
    suspend fun payOrder(
        @Path("orderId") orderId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: PayOrderRequestDto,
    ): DataEnvelope<OrderResponseDto>

    @POST("orders/{orderId}/cancel")
    suspend fun cancelOrder(@Path("orderId") orderId: String): DataEnvelope<OrderResponseDto>

    @GET("orders")
    suspend fun listOrders(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): OrdersPageDto

    @GET("tickets")
    suspend fun listTickets(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): TicketsPageDto
}
