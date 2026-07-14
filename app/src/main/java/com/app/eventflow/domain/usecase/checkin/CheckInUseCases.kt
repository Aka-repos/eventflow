package com.app.eventflow.domain.usecase.checkin

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.checkin.CheckInOutcome
import com.app.eventflow.domain.model.checkin.TicketQr
import com.app.eventflow.domain.repository.CheckInRepository
import javax.inject.Inject

class GetTicketQrUseCase @Inject constructor(private val repository: CheckInRepository) {
    suspend operator fun invoke(ticketId: String): AppResult<TicketQr> = repository.getTicketQr(ticketId)
}

class EventCheckInUseCase @Inject constructor(private val repository: CheckInRepository) {
    suspend operator fun invoke(eventId: String, qrToken: String): AppResult<CheckInOutcome> =
        repository.checkIn(eventId, qrToken)
}
