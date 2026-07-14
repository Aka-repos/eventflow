package com.app.eventflow.domain.repository

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.checkin.CheckInOutcome
import com.app.eventflow.domain.model.checkin.TicketQr

interface CheckInRepository {

    /** QR del boleto (nunca se cachea en disco — solo en memoria del ViewModel; regla api/09). */
    suspend fun getTicketQr(ticketId: String): AppResult<TicketQr>

    /** ⚡ Idempotency-Key por escaneo. Toda validación es server-side. */
    suspend fun checkIn(eventId: String, qrToken: String): AppResult<CheckInOutcome>
}
