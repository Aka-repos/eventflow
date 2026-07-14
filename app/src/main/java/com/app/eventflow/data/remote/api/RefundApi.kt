package com.app.eventflow.data.remote.api

import com.app.eventflow.data.remote.dto.DataEnvelope
import com.app.eventflow.data.remote.dto.refunds.CreateRefundRequestDto
import com.app.eventflow.data.remote.dto.refunds.RecoveryOptionsDto
import com.app.eventflow.data.remote.dto.refunds.RefundResponseDto
import com.app.eventflow.data.remote.dto.refunds.RefundsPageDto
import com.app.eventflow.data.remote.dto.refunds.RejectRefundRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Métodos = operationId del contrato (getRecoveryOptions, requestRefund ⚡, approveRefund ⚡, …). */
interface RefundApi {

    @GET("tickets/{ticketId}/recovery-options")
    suspend fun getRecoveryOptions(@Path("ticketId") ticketId: String): DataEnvelope<RecoveryOptionsDto>

    @POST("tickets/{ticketId}/refund-requests")
    suspend fun requestRefund(
        @Path("ticketId") ticketId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateRefundRequestDto,
    ): DataEnvelope<RefundResponseDto>

    @POST("refund-requests/{refundId}/approve")
    suspend fun approveRefund(
        @Path("refundId") refundId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DataEnvelope<RefundResponseDto>

    @POST("refund-requests/{refundId}/reject")
    suspend fun rejectRefund(
        @Path("refundId") refundId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: RejectRefundRequestDto,
    ): DataEnvelope<RefundResponseDto>

    @GET("organizer/events/{eventId}/refund-requests")
    suspend fun listEventRefunds(
        @Path("eventId") eventId: String,
        @Query("status") status: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): RefundsPageDto
}
