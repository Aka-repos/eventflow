package com.app.eventflow.ui.feature.refunds

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.refunds.RecoveryOption
import com.app.eventflow.domain.usecase.refunds.GetRecoveryOptionsUseCase
import com.app.eventflow.domain.usecase.refunds.RequestRefundUseCase
import com.app.eventflow.testutil.FakeRefundRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.ui.feature.refunds.recovery.RecoveryUiEffect
import com.app.eventflow.ui.feature.refunds.recovery.RecoveryUiEvent
import com.app.eventflow.ui.feature.refunds.recovery.RecoveryViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeRefundRepository) = RecoveryViewModel(
        SavedStateHandle(mapOf("ticketId" to "tk1")),
        GetRecoveryOptionsUseCase(repo),
        RequestRefundUseCase(repo),
    )

    @Test
    fun `loads refund option on start`() = runTest {
        val repo = FakeRefundRepository()
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertEquals(RecoveryOption.REFUND, viewModel.state.value.options?.option)
        assertEquals("30.00 USD", viewModel.state.value.options?.refund?.amount?.formatted())
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("tk1"), repo.recoveryCalls)
    }

    @Test
    fun `network error marks offline without error text`() = runTest {
        val repo = FakeRefundRepository().apply { recoveryResult = AppResult.Failure(AppError.Network) }
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.offline)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `request refund passes reason and emits navigate back`() = runTest {
        val repo = FakeRefundRepository()
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onEvent(RecoveryUiEvent.ReasonChanged("No podré ir"))
            viewModel.onEvent(RecoveryUiEvent.RequestRefund)
            advanceUntilIdle()
            assertEquals(RecoveryUiEffect.NavigateBack, awaitItem())
        }
        assertEquals(listOf("tk1" to "No podré ir"), repo.requestCalls)
        assertTrue(viewModel.state.value.requested)
    }

    @Test
    fun `blank reason sent as null`() = runTest {
        val repo = FakeRefundRepository()
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onEvent(RecoveryUiEvent.RequestRefund)
        advanceUntilIdle()

        assertEquals(listOf("tk1" to null), repo.requestCalls)
    }

    @Test
    fun `already requested conflict surfaces a message effect`() = runTest {
        val repo = FakeRefundRepository().apply {
            requestResult = AppResult.Failure(AppError.Conflict("refund_already_requested"))
        }
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onEvent(RecoveryUiEvent.RequestRefund)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is RecoveryUiEffect.ShowMessage)
            assertEquals("Ya existe una solicitud para este boleto",
                (effect as RecoveryUiEffect.ShowMessage).message)
        }
        assertFalse(viewModel.state.value.submitting)
    }
}
