package com.app.eventflow.ui.feature.profile

import app.cash.turbine.test
import com.app.eventflow.core.network.AppError
import com.app.eventflow.domain.model.UserProfile
import com.app.eventflow.domain.model.UserRole
import com.app.eventflow.domain.usecase.auth.ObserveSessionUseCase
import com.app.eventflow.domain.usecase.profile.UpdateProfileUseCase
import com.app.eventflow.testutil.FakeAuthRepository
import com.app.eventflow.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val profile = UserProfile("u-1", "ana@mail.com", "Ana P.", "+50761234567", listOf(UserRole.ATTENDEE))

    private fun vm(repo: FakeAuthRepository) = ProfileViewModel(
        ObserveSessionUseCase(repo),
        UpdateProfileUseCase(repo),
    )

    @Test
    fun `prefills fields from session`() = runTest {
        val repo = FakeAuthRepository().apply { setSession(profile) }
        val viewModel = vm(repo)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Ana P.", state.fullName)
        assertEquals("+50761234567", state.phone)
        assertEquals("ana@mail.com", state.email)
        assertTrue(state.roles.contains("ATTENDEE"))
    }

    @Test
    fun `blank name disables save`() = runTest {
        val repo = FakeAuthRepository().apply { setSession(profile) }
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onEvent(ProfileUiEvent.FullNameChanged(""))
        assertFalse(viewModel.state.value.canSave)
        assertNotNull(viewModel.state.value.fullNameError)
    }

    @Test
    fun `invalid phone disables save`() = runTest {
        val repo = FakeAuthRepository().apply { setSession(profile) }
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onEvent(ProfileUiEvent.PhoneChanged("12345"))
        assertFalse(viewModel.state.value.canSave)
        assertNotNull(viewModel.state.value.phoneError)
    }

    @Test
    fun `save sends trimmed values and navigates back`() = runTest {
        val repo = FakeAuthRepository().apply { setSession(profile) }
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onEvent(ProfileUiEvent.FullNameChanged("  Ana María  "))
        viewModel.onEvent(ProfileUiEvent.PhoneChanged("+50769999999"))

        viewModel.effects.test {
            viewModel.onEvent(ProfileUiEvent.Save)
            advanceUntilIdle()
            assertTrue(awaitItem() is ProfileUiEffect.ShowMessage)   // "Perfil actualizado"
            assertEquals(ProfileUiEffect.NavigateBack, awaitItem())
        }
        assertEquals("Ana María" to "+50769999999", repo.lastUpdate)
        assertEquals(1, repo.updateCalls)
    }

    @Test
    fun `blank phone is sent as null`() = runTest {
        val repo = FakeAuthRepository().apply { setSession(profile) }
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.onEvent(ProfileUiEvent.PhoneChanged("   "))
        viewModel.onEvent(ProfileUiEvent.Save)
        advanceUntilIdle()

        assertEquals("Ana P." to null, repo.lastUpdate)
    }

    @Test
    fun `failure surfaces message and stays on screen`() = runTest {
        val repo = FakeAuthRepository().apply {
            setSession(profile)
            updateError = AppError.Network
        }
        val viewModel = vm(repo)
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onEvent(ProfileUiEvent.Save)
            advanceUntilIdle()
            assertTrue(awaitItem() is ProfileUiEffect.ShowMessage)
        }
        assertFalse(viewModel.state.value.saving)
    }
}
