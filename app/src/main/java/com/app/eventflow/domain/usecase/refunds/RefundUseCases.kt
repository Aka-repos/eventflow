package com.app.eventflow.domain.usecase.refunds

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.refunds.RecoveryOptions
import com.app.eventflow.domain.model.refunds.RefundPage
import com.app.eventflow.domain.model.refunds.RefundRequest
import com.app.eventflow.domain.model.refunds.RefundStatus
import com.app.eventflow.domain.repository.RefundRepository
import javax.inject.Inject

class GetRecoveryOptionsUseCase @Inject constructor(private val repository: RefundRepository) {
    suspend operator fun invoke(ticketId: String): AppResult<RecoveryOptions> =
        repository.getRecoveryOptions(ticketId)
}

class RequestRefundUseCase @Inject constructor(private val repository: RefundRepository) {
    suspend operator fun invoke(ticketId: String, reason: String?): AppResult<RefundRequest> =
        repository.requestRefund(ticketId, reason)
}

class ApproveRefundUseCase @Inject constructor(private val repository: RefundRepository) {
    suspend operator fun invoke(refundId: String): AppResult<RefundRequest> =
        repository.approveRefund(refundId)
}

class RejectRefundUseCase @Inject constructor(private val repository: RefundRepository) {
    suspend operator fun invoke(refundId: String, reason: String): AppResult<RefundRequest> =
        repository.rejectRefund(refundId, reason)
}

class ListEventRefundsUseCase @Inject constructor(private val repository: RefundRepository) {
    suspend operator fun invoke(
        eventId: String,
        status: RefundStatus? = null,
        cursor: String? = null,
    ): AppResult<RefundPage> = repository.listEventRefunds(eventId, status, cursor)
}
