package com.app.eventflow.ui.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.eventflow.domain.model.UserProfile
import com.app.eventflow.domain.usecase.auth.LogoutUseCase
import com.app.eventflow.domain.usecase.auth.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val logout: LogoutUseCase,
) : ViewModel() {

    val session: StateFlow<UserProfile?> = observeSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onLogout() {
        viewModelScope.launch { logout() }
    }
}
