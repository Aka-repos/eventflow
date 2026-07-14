package com.app.eventflow.data.remote.api

import com.app.eventflow.data.remote.dto.DataEnvelope
import com.app.eventflow.data.remote.dto.checkin.CheckInRequestDto
import com.app.eventflow.data.remote.dto.checkin.CheckInResponseDto
import com.app.eventflow.data.remote.dto.checkin.QrResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/** Métodos = operationId (getTicketQr, eventCheckIn ⚡). */
interface CheckInApi {

    @GET("tickets/{ticketId}/qr")
    suspend fun getTicketQr(@Path("ticketId") ticketId: String): DataEnvelope<QrResponseDto>

    @POST("events/{eventId}/check-ins")
    suspend fun eventCheckIn(
        @Path("eventId") eventId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CheckInRequestDto,
    ): DataEnvelope<CheckInResponseDto>
}
