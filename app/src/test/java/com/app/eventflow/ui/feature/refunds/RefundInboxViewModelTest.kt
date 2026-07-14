package com.app.eventflow.ui.feature.refunds

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.refunds.RefundPage
import com.app.eventflow.domain.model.refunds.RefundStatus
import com.app.eventflow.domain.usecase.refunds.ApproveRefundUseCase
import com.app.eventflow.domain.usecase.refunds.ListEventRefundsUseCase
import com.app.eventflow.domain.usecase.refunds.RejectRefundUseCase
import com.app.eventflow.testutil.FakeRefundRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.ui.feature.refunds.inbox.RefundInboxUiEffect
import com.app.eventflow.ui.feature.refunds.inbox.RefundInboxUiEvent
import com.app.eventflow.ui.feature.refunds.inbox.RefundInboxViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefundInboxViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeRefundRepository) = RefundInboxViewModel(
        SavedStateHandle(mapOf("eventId" to "ev1")),
        ListEventRefundsUseCase(repo),
        ApproveRefundUseCase(repo),
        RejectRefundUseCase(repo),
    )

    @Test
    fun `loads pending refunds on start`() = runTest {
        val repo = FakeRefundRepository()
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.items.size)
        assertEquals(RefundStatus.REQUESTED, viewModel.state.value.items.first().status)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("ev1"), repo.listCalls)
    }

    @Test
    fun `empty page renders empty inbox`() = runTest {
        val repo = FakeRefundRepository().apply {
            listResult = AppResult.Success(RefundPage(emptyList(), hasNext = false, nextCursor = null))
        }
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test
    fun `approve calls use case and reloads`() = runTest {
        val repo = FakeRefundRepository()
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onEvent(RefundInboxUiEvent.Approve("rf1"))
        advanceUntilIdle()

        assertEquals(listOf("rf1"), repo.approveCalls)
        // recarga: una llamada inicial + una tras aprobar
        assertEquals(2, repo.listCalls.size)
        assertNull(viewModel.state.value.actioningId)
    }

    @Test
    fun `reject requires reason then calls use case`() = runTest {
        val repo = FakeRefundRepository()
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onEvent(RefundInboxUiEvent.StartReject("rf1"))
        // confirmar con motivo vacío no dispara nada
        viewModel.onEvent(RefundInboxUiEvent.ConfirmReject)
        advanceUntilIdle()
        assertTrue(repo.rejectCalls.isEmpty())

        viewModel.onEvent(RefundInboxUiEvent.RejectReasonChanged("No aplica"))
        viewModel.onEvent(RefundInboxUiEvent.ConfirmReject)
        advanceUntilIdle()

        assertEquals(listOf("rf1" to "No aplica"), repo.rejectCalls)
        assertNull(viewModel.state.value.rejectingId)
    }

    @Test
    fun `conflict on approve surfaces message`() = runTest {
        val repo = FakeRefundRepository().apply {
            approveResult = AppResult.Failure(AppError.Conflict("refund_not_pending"))
        }
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onEvent(RefundInboxUiEvent.Approve("rf1"))
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is RefundInboxUiEffect.ShowMessage)
            assertEquals("La solicitud ya fue resuelta",
                (effect as RefundInboxUiEffect.ShowMessage).message)
        }
    }

    @Test
    fun `network error marks offline`() = runTest {
        val repo = FakeRefundRepository().apply { listResult = AppResult.Failure(AppError.Network) }
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.offline)
    }
}
