package com.app.eventflow.ui.feature.auth

import app.cash.turbine.test
import com.app.eventflow.core.network.AppError
import com.app.eventflow.domain.usecase.auth.LoginUseCase
import com.app.eventflow.testutil.FakeAuthRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.ui.feature.auth.login.LoginError
import com.app.eventflow.ui.feature.auth.login.LoginUiEffect
import com.app.eventflow.ui.feature.auth.login.LoginUiEvent
import com.app.eventflow.ui.feature.auth.login.LoginViewModel
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
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()

    private fun viewModel() = LoginViewModel(LoginUseCase(repository))

    private fun LoginViewModel.fillValidForm() {
        onEvent(LoginUiEvent.EmailChanged("ana@mail.com"))
        onEvent(LoginUiEvent.PasswordChanged("S3gura!pass"))
    }

    @Test
    fun `login exitoso emite NavigateHome y termina el loading`() = runTest {
        val vm = viewModel()
        vm.fillValidForm()

        vm.effects.test {
            vm.onEvent(LoginUiEvent.Submit)
            assertEquals(LoginUiEffect.NavigateHome, awaitItem())
        }
        assertFalse(vm.uiState.value.isSubmitting)
        assertNull(vm.uiState.value.generalError)
        assertEquals(1, repository.loginCalls)
    }

    @Test
    fun `credenciales invalidas muestran error tipado sin navegar`() = runTest {
        repository.nextError = AppError.Auth
        val vm = viewModel()
        vm.fillValidForm()

        vm.onEvent(LoginUiEvent.Submit)
        advanceUntilIdle()

        assertEquals(LoginError.INVALID_CREDENTIALS, vm.uiState.value.generalError)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `cuenta bloqueada se distingue de credenciales invalidas`() = runTest {
        repository.nextError = AppError.Forbidden("account_blocked")
        val vm = viewModel()
        vm.fillValidForm()

        vm.onEvent(LoginUiEvent.Submit)
        advanceUntilIdle()

        assertEquals(LoginError.ACCOUNT_BLOCKED, vm.uiState.value.generalError)
    }

    @Test
    fun `sin red muestra error de conexion`() = runTest {
        repository.nextError = AppError.Network
        val vm = viewModel()
        vm.fillValidForm()

        vm.onEvent(LoginUiEvent.Submit)
        advanceUntilIdle()

        assertEquals(LoginError.NETWORK, vm.uiState.value.generalError)
    }

    @Test
    fun `submit con formulario incompleto no llama al repositorio`() = runTest {
        val vm = viewModel()
        vm.onEvent(LoginUiEvent.EmailChanged("ana@mail.com"))

        vm.onEvent(LoginUiEvent.Submit)
        advanceUntilIdle()

        assertEquals(0, repository.loginCalls)
    }

    @Test
    fun `escribir limpia el error general previo`() = runTest {
        repository.nextError = AppError.Auth
        val vm = viewModel()
        vm.fillValidForm()
        vm.onEvent(LoginUiEvent.Submit)
        advanceUntilIdle()

        vm.onEvent(LoginUiEvent.PasswordChanged("otra"))

        assertNull(vm.uiState.value.generalError)
        assertTrue(vm.uiState.value.password == "otra")
    }
}
