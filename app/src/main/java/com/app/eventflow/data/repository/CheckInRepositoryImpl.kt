package com.app.eventflow.data.repository

import com.app.eventflow.core.di.IoDispatcher
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.core.network.map
import com.app.eventflow.core.network.safeApiCall
import com.app.eventflow.data.remote.api.CheckInApi
import com.app.eventflow.data.remote.dto.checkin.CheckInRequestDto
import com.app.eventflow.domain.model.checkin.CheckInOutcome
import com.app.eventflow.domain.model.checkin.TicketQr
import com.app.eventflow.domain.repository.CheckInRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheckInRepositoryImpl @Inject constructor(
    private val api: CheckInApi,
    private val converter: ProblemConverter,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : CheckInRepository {

    override suspend fun getTicketQr(ticketId: String): AppResult<TicketQr> =
        safeApiCall(dispatcher, converter) { api.getTicketQr(ticketId) }
            .map { TicketQr(it.data.qrToken, it.data.expiresAt, it.data.refreshAfter) }

    override suspend fun checkIn(eventId: String, qrToken: String): AppResult<CheckInOutcome> {
        val result = safeApiCall(dispatcher, converter) {
            api.eventCheckIn(eventId, UUID.randomUUID().toString(), CheckInRequestDto(qrToken))
        }
        return when (result) {
            is AppResult.Success -> AppResult.Success(
                CheckInOutcome.Granted(
                    result.value.data.attendeeName,
                    result.value.data.ticketTypeName,
                    result.value.data.zoneName,
                ),
            )
            // los rechazos del contrato son Problem; los convertimos a Denied (no a error de red)
            is AppResult.Failure -> when (val e = result.error) {
                is AppError.Business -> AppResult.Success(CheckInOutcome.Denied(e.code, denialMessage(e.code)))
                is AppError.Conflict -> AppResult.Success(CheckInOutcome.Denied(e.code, denialMessage(e.code)))
                is AppError.Forbidden -> AppResult.Success(CheckInOutcome.Denied(e.code, denialMessage(e.code)))
                else -> AppResult.Failure(e)
            }
        }
    }

    private fun denialMessage(code: String): String = when (code) {
        "qr_invalid" -> "QR no válido"
        "qr_expired" -> "QR expirado"
        "already_used" -> "Boleto ya usado"
        "checkin_wrong_event" -> "QR de otro evento"
        "ticket_blocked" -> "Boleto bloqueado"
        "qr_not_yet_visible" -> "QR aún no disponible"
        "staff_not_assigned" -> "No autorizado para este evento"
        else -> "Acceso denegado"
    }
}
