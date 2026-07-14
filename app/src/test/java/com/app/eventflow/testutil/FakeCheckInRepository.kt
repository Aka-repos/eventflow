package com.app.eventflow.testutil

import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.checkin.CheckInOutcome
import com.app.eventflow.domain.model.checkin.TicketQr
import com.app.eventflow.domain.repository.CheckInRepository

class FakeCheckInRepository : CheckInRepository {

    var qrResult: AppResult<TicketQr> = AppResult.Success(
        TicketQr("jws.token.aaa", "2027-02-01T21:00:00Z", "2027-02-01T20:40:00Z"),
    )
    var checkInResult: AppResult<CheckInOutcome> =
        AppResult.Success(CheckInOutcome.Granted("Ana", "VIP", "Palco"))
    val qrCalls = mutableListOf<String>()
    val checkInCalls = mutableListOf<Pair<String, String>>()

    override suspend fun getTicketQr(ticketId: String): AppResult<TicketQr> {
        qrCalls += ticketId
        return qrResult
    }

    override suspend fun checkIn(eventId: String, qrToken: String): AppResult<CheckInOutcome> {
        checkInCalls += eventId to qrToken
        return checkInResult
    }
}
