package com.app.eventflow.ui.feature.auth

import app.cash.turbine.test
import com.app.eventflow.core.network.AppError
import com.app.eventflow.domain.usecase.auth.RegisterUseCase
import com.app.eventflow.testutil.FakeAuthRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.ui.feature.auth.register.RegisterError
import com.app.eventflow.ui.feature.auth.register.RegisterUiEffect
import com.app.eventflow.ui.feature.auth.register.RegisterUiEvent
import com.app.eventflow.ui.feature.auth.register.RegisterViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()

    private fun viewModel() = RegisterViewModel(RegisterUseCase(repository))

    private fun RegisterViewModel.fillValidForm() {
        onEvent(RegisterUiEvent.FullNameChanged("Ana P."))
        onEvent(RegisterUiEvent.EmailChanged("ana@mail.com"))
        onEvent(RegisterUiEvent.PasswordChanged("S3gura!pass"))
    }

    @Test
    fun `registro exitoso emite NavigateHome`() = runTest {
        val vm = viewModel()
        vm.fillValidForm()

        vm.effects.test {
            vm.onEvent(RegisterUiEvent.Submit)
            assertEquals(RegisterUiEffect.NavigateHome, awaitItem())
        }
        assertEquals(1, repository.registerCalls)
    }

    @Test
    fun `email duplicado (409) muestra EMAIL_TAKEN`() = runTest {
        repository.nextError = AppError.Conflict("email_already_registered")
        val vm = viewModel()
        vm.fillValidForm()

        vm.onEvent(RegisterUiEvent.Submit)
        advanceUntilIdle()

        assertEquals(RegisterError.EMAIL_TAKEN, vm.uiState.value.generalError)
    }

    @Test
    fun `errores de validacion 422 se mapean por campo`() = runTest {
        repository.nextError = AppError.Validation(mapOf("email" to "debe ser un email válido"))
        val vm = viewModel()
        vm.fillValidForm()

        vm.onEvent(RegisterUiEvent.Submit)
        advanceUntilIdle()

        assertEquals("debe ser un email válido", vm.uiState.value.fieldErrors["email"])
    }

    @Test
    fun `password menor a 8 caracteres bloquea el submit`() = runTest {
        val vm = viewModel()
        vm.onEvent(RegisterUiEvent.FullNameChanged("Ana"))
        vm.onEvent(RegisterUiEvent.EmailChanged("ana@mail.com"))
        vm.onEvent(RegisterUiEvent.PasswordChanged("corta"))

        vm.onEvent(RegisterUiEvent.Submit)
        advanceUntilIdle()

        assertEquals(0, repository.registerCalls)
    }
}
