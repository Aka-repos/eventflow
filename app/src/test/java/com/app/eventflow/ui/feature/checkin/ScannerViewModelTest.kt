package com.app.eventflow.ui.feature.checkin

import androidx.lifecycle.SavedStateHandle
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.checkin.CheckInOutcome
import com.app.eventflow.domain.usecase.checkin.EventCheckInUseCase
import com.app.eventflow.testutil.FakeCheckInRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.ui.feature.scanner.ScannerUiEvent
import com.app.eventflow.ui.feature.scanner.ScannerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun vm(repo: FakeCheckInRepository) = ScannerViewModel(
        SavedStateHandle(mapOf("eventId" to "e1")),
        EventCheckInUseCase(repo),
    )

    @Test
    fun `granted outcome shown and camera pauses`() = runTest {
        val repo = FakeCheckInRepository()
        val viewModel = vm(repo)

        viewModel.onEvent(ScannerUiEvent.QrDetected("jws.aaa"))
        advanceUntilIdle()

        val outcome = viewModel.state.value.lastOutcome
        assertTrue(outcome is CheckInOutcome.Granted)
        assertEquals("Ana", (outcome as CheckInOutcome.Granted).attendeeName)
        assertEquals(false, viewModel.state.value.cameraActive) // pausada tras resultado
        assertEquals("e1" to "jws.aaa", repo.checkInCalls.single())
    }

    @Test
    fun `denied outcome from contract is shown as Denied not error`() = runTest {
        val repo = FakeCheckInRepository().apply {
            checkInResult = AppResult.Success(CheckInOutcome.Denied("already_used", "Boleto ya usado"))
        }
        val viewModel = vm(repo)

        viewModel.onEvent(ScannerUiEvent.QrDetected("jws.bbb"))
        advanceUntilIdle()

        val outcome = viewModel.state.value.lastOutcome
        assertTrue(outcome is CheckInOutcome.Denied)
        assertEquals("already_used", (outcome as CheckInOutcome.Denied).code)
    }

    @Test
    fun `duplicate frame of same token is ignored`() = runTest {
        val repo = FakeCheckInRepository()
        val viewModel = vm(repo)

        viewModel.onEvent(ScannerUiEvent.QrDetected("jws.aaa"))
        viewModel.onEvent(ScannerUiEvent.QrDetected("jws.aaa")) // frame repetido
        advanceUntilIdle()

        assertEquals(1, repo.checkInCalls.size)
    }

    @Test
    fun `scan next resets to active camera`() = runTest {
        val repo = FakeCheckInRepository()
        val viewModel = vm(repo)
        viewModel.onEvent(ScannerUiEvent.QrDetected("jws.aaa"))
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.cameraActive)

        viewModel.onEvent(ScannerUiEvent.ScanNext)
        assertTrue(viewModel.state.value.cameraActive)

        // ahora puede escanear otro
        viewModel.onEvent(ScannerUiEvent.QrDetected("jws.ccc"))
        advanceUntilIdle()
        assertEquals(2, repo.checkInCalls.size)
    }

    @Test
    fun `network failure surfaces as network error not denied`() = runTest {
        val repo = FakeCheckInRepository().apply {
            checkInResult = AppResult.Failure(AppError.Network)
        }
        val viewModel = vm(repo)
        viewModel.onEvent(ScannerUiEvent.QrDetected("jws.aaa"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.networkError)
    }
}
