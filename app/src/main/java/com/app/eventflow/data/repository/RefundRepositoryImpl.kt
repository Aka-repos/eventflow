package com.app.eventflow.data.repository

import com.app.eventflow.core.network.AppResult
import com.app.eventflow.core.network.ProblemConverter
import com.app.eventflow.core.network.map
import com.app.eventflow.core.network.safeApiCall
import com.app.eventflow.data.remote.api.RefundApi
import com.app.eventflow.data.remote.dto.catalog.MoneyDto
import com.app.eventflow.data.remote.dto.refunds.CreateRefundRequestDto
import com.app.eventflow.data.remote.dto.refunds.RecoveryOptionsDto
import com.app.eventflow.data.remote.dto.refunds.RefundResponseDto
import com.app.eventflow.data.remote.dto.refunds.RejectRefundRequestDto
import com.app.eventflow.domain.model.refunds.ExchangeQuote
import com.app.eventflow.domain.model.refunds.Money
import com.app.eventflow.domain.model.refunds.RecoveryOption
import com.app.eventflow.domain.model.refunds.RecoveryOptions
import com.app.eventflow.domain.model.refunds.RefundPage
import com.app.eventflow.domain.model.refunds.RefundQuote
import com.app.eventflow.domain.model.refunds.RefundRequest
import com.app.eventflow.domain.model.refunds.RefundStatus
import com.app.eventflow.domain.repository.RefundRepository
import kotlinx.coroutines.CoroutineDispatcher
import java.util.UUID

class RefundRepositoryImpl(
    private val api: RefundApi,
    private val converter: ProblemConverter,
    private val dispatcher: CoroutineDispatcher,
) : RefundRepository {

    override suspend fun getRecoveryOptions(ticketId: String): AppResult<RecoveryOptions> =
        safeApiCall(dispatcher, converter) { api.getRecoveryOptions(ticketId) }
            .map { it.data.toModel() }

    override suspend fun requestRefund(ticketId: String, reason: String?): AppResult<RefundRequest> =
        safeApiCall(dispatcher, converter) {
            api.requestRefund(ticketId, UUID.randomUUID().toString(), CreateRefundRequestDto(reason))
        }.map { it.data.toModel() }

    override suspend fun approveRefund(refundId: String): AppResult<RefundRequest> =
        safeApiCall(dispatcher, converter) {
            api.approveRefund(refundId, UUID.randomUUID().toString())
        }.map { it.data.toModel() }

    override suspend fun rejectRefund(refundId: String, reason: String): AppResult<RefundRequest> =
        safeApiCall(dispatcher, converter) {
            api.rejectRefund(refundId, UUID.randomUUID().toString(), RejectRefundRequestDto(reason))
        }.map { it.data.toModel() }

    override suspend fun listEventRefunds(
        eventId: String,
        status: RefundStatus?,
        cursor: String?,
    ): AppResult<RefundPage> =
        safeApiCall(dispatcher, converter) {
            api.listEventRefunds(eventId, status?.takeIf { it != RefundStatus.UNKNOWN }?.name, cursor)
        }.map { page ->
            RefundPage(
                items = page.data.map { it.toModel() },
                hasNext = page.meta.hasNext,
                nextCursor = page.meta.nextCursor,
            )
        }
}

private fun MoneyDto.toModel() = Money(amount, currency)

private fun RefundResponseDto.toModel() = RefundRequest(
    id = id,
    ticketId = ticketId,
    amount = amount.toModel(),
    status = parseStatus(status),
    reason = reason,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
)

private fun RecoveryOptionsDto.toModel() = RecoveryOptions(
    ticketId = ticketId,
    option = parseOption(option),
    reason = reason,
    refund = refund?.let { RefundQuote(it.amount.toModel(), it.deadline) },
    exchange = exchange?.let {
        ExchangeQuote(it.originalPrice.toModel(), it.depreciationPct, it.listPrice.toModel(), it.listingDeadline)
    },
)

private fun parseOption(raw: String): RecoveryOption =
    runCatching { RecoveryOption.valueOf(raw) }.getOrDefault(RecoveryOption.NONE)

private fun parseStatus(raw: String): RefundStatus =
    runCatching { RefundStatus.valueOf(raw) }.getOrDefault(RefundStatus.UNKNOWN)
