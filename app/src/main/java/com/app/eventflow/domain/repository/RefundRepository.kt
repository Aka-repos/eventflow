package com.app.eventflow.domain.repository

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.refunds.RecoveryOptions
import com.app.eventflow.domain.model.refunds.RefundPage
import com.app.eventflow.domain.model.refunds.RefundRequest
import com.app.eventflow.domain.model.refunds.RefundStatus

interface RefundRepository {

    /** Opciones de recuperación (ADR-19). Requiere estar en línea — nunca se cachea (api/09). */
    suspend fun getRecoveryOptions(ticketId: String): AppResult<RecoveryOptions>

    /** ⚡ Solicitud de reembolso del asistente ("no podré asistir"). Idempotency-Key por intento. */
    suspend fun requestRefund(ticketId: String, reason: String?): AppResult<RefundRequest>

    /** ⚡ Aprobación por el organizador dueño del evento. */
    suspend fun approveRefund(refundId: String): AppResult<RefundRequest>

    /** ⚡ Rechazo por el organizador dueño del evento (motivo obligatorio). */
    suspend fun rejectRefund(refundId: String, reason: String): AppResult<RefundRequest>

    /** Bandeja del organizador: expedientes del evento, filtrable por estado, con cursor. */
    suspend fun listEventRefunds(
        eventId: String,
        status: RefundStatus?,
        cursor: String?,
    ): AppResult<RefundPage>
}
