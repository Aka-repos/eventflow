package com.app.eventflow.testutil

import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.refunds.Money
import com.app.eventflow.domain.model.refunds.RecoveryOption
import com.app.eventflow.domain.model.refunds.RecoveryOptions
import com.app.eventflow.domain.model.refunds.RefundPage
import com.app.eventflow.domain.model.refunds.RefundQuote
import com.app.eventflow.domain.model.refunds.RefundRequest
import com.app.eventflow.domain.model.refunds.RefundStatus
import com.app.eventflow.domain.repository.RefundRepository

/** Fake hecho a mano (preferido sobre mocks) para probar los ViewModels de recuperación/bandeja. */
class FakeRefundRepository : RefundRepository {

    var recoveryResult: AppResult<RecoveryOptions> = AppResult.Success(
        RecoveryOptions(
            ticketId = "tk1",
            option = RecoveryOption.REFUND,
            reason = "refund_window_active",
            refund = RefundQuote(Money("30.00", "USD"), "2027-01-20T00:00:00Z"),
            exchange = null,
        ),
    )
    var requestResult: AppResult<RefundRequest> = AppResult.Success(sampleRefund(RefundStatus.REQUESTED))
    var approveResult: AppResult<RefundRequest> = AppResult.Success(sampleRefund(RefundStatus.APPROVED))
    var rejectResult: AppResult<RefundRequest> = AppResult.Success(sampleRefund(RefundStatus.REJECTED))
    var listResult: AppResult<RefundPage> = AppResult.Success(
        RefundPage(listOf(sampleRefund(RefundStatus.REQUESTED)), hasNext = false, nextCursor = null),
    )

    val recoveryCalls = mutableListOf<String>()
    val requestCalls = mutableListOf<Pair<String, String?>>()
    val approveCalls = mutableListOf<String>()
    val rejectCalls = mutableListOf<Pair<String, String>>()
    val listCalls = mutableListOf<String>()

    override suspend fun getRecoveryOptions(ticketId: String): AppResult<RecoveryOptions> {
        recoveryCalls += ticketId
        return recoveryResult
    }

    override suspend fun requestRefund(ticketId: String, reason: String?): AppResult<RefundRequest> {
        requestCalls += ticketId to reason
        return requestResult
    }

    override suspend fun approveRefund(refundId: String): AppResult<RefundRequest> {
        approveCalls += refundId
        return approveResult
    }

    override suspend fun rejectRefund(refundId: String, reason: String): AppResult<RefundRequest> {
        rejectCalls += refundId to reason
        return rejectResult
    }

    override suspend fun listEventRefunds(
        eventId: String,
        status: RefundStatus?,
        cursor: String?,
    ): AppResult<RefundPage> {
        listCalls += eventId
        return listResult
    }

    companion object {
        fun sampleRefund(status: RefundStatus, id: String = "rf1") = RefundRequest(
            id = id,
            ticketId = "tk1",
            amount = Money("30.00", "USD"),
            status = status,
            reason = "No podré asistir",
            createdAt = "2027-01-10T12:00:00Z",
            resolvedAt = if (status == RefundStatus.REQUESTED) null else "2027-01-11T09:00:00Z",
        )

        val NETWORK: AppResult<Nothing> = AppResult.Failure(AppError.Network)
    }
}
