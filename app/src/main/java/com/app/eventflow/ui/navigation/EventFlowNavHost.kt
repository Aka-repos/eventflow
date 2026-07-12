package com.app.eventflow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.eventflow.core.security.TokenStore
import com.app.eventflow.ui.feature.auth.login.LoginRoute
import com.app.eventflow.ui.feature.auth.register.RegisterRoute
import com.app.eventflow.ui.feature.catalog.detail.EventDetailRoute
import com.app.eventflow.ui.feature.checkout.CheckoutRoute
import com.app.eventflow.ui.feature.home.HomeRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Rutas tipadas centralizadas (docs/engineering/03 §3). */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val EVENT_DETAIL = "event/{eventId}"
    const val CHECKOUT = "checkout/{eventId}/{tariffId}/{quantity}"

    fun eventDetail(eventId: String) = "event/$eventId"

    fun checkout(eventId: String, tariffId: String, quantity: Int) =
        "checkout/$eventId/$tariffId/$quantity"
}

@HiltViewModel
class SessionGateViewModel @Inject constructor(tokenStore: TokenStore) : ViewModel() {
    val hasSession = tokenStore.hasSession
}

@Composable
fun EventFlowNavHost(
    navController: NavHostController = rememberNavController(),
    gate: SessionGateViewModel = hiltViewModel(),
) {
    val hasSession by gate.hasSession.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (hasSession) Routes.HOME else Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginRoute(
                onNavigateHome = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onNavigateRegister = { navController.navigate(Routes.REGISTER) },
            )
        }
        composable(Routes.REGISTER) {
            RegisterRoute(
                onNavigateHome = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } }
                },
                onNavigateLogin = { navController.popBackStack() },
            )
        }
        composable(Routes.HOME) {
            HomeRoute(
                onNavigateToDetail = { eventId -> navController.navigate(Routes.eventDetail(eventId)) },
            )
        }
        composable(Routes.EVENT_DETAIL) {
            EventDetailRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCheckout = { eventId, tariffId ->
                    navController.navigate(Routes.checkout(eventId, tariffId, 1))
                },
            )
        }
        composable(Routes.CHECKOUT) {
            CheckoutRoute(
                onNavigateToTickets = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }

    // Sesión revocada (logout, refresh reusado, familia revocada) → volver a Login
    if (!hasSession && navController.currentDestination?.route == Routes.HOME) {
        navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }
}
