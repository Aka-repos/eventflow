package com.app.eventflow.ui.feature.checkin

import androidx.lifecycle.SavedStateHandle
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.checkin.TicketQr
import com.app.eventflow.domain.usecase.checkin.GetTicketQrUseCase
import com.app.eventflow.testutil.FakeCheckInRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.ui.feature.qr.TicketQrViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TicketQrViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeCheckInRepository) = TicketQrViewModel(
        SavedStateHandle(mapOf("ticketId" to "tk1")),
        GetTicketQrUseCase(repo),
    )

    @Test
    fun `loads qr token on start`() = runTest {
        val repo = FakeCheckInRepository()
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertEquals("jws.token.aaa", viewModel.state.value.qr?.qrToken)
        assertEquals(false, viewModel.state.value.isLoading)
        assertEquals(listOf("tk1"), repo.qrCalls)
    }

    @Test
    fun `qr not yet visible shows dedicated state without error`() = runTest {
        val repo = FakeCheckInRepository().apply {
            qrResult = AppResult.Failure(AppError.Forbidden("qr_not_yet_visible"))
        }
        val viewModel = vm(repo)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.notYetVisible)
        assertEquals(null, viewModel.state.value.error)
    }
}
