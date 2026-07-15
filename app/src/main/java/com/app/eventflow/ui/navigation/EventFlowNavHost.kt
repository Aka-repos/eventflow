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
import com.app.eventflow.ui.feature.profile.ProfileRoute
import com.app.eventflow.ui.feature.qr.TicketQrRoute
import com.app.eventflow.ui.feature.refunds.inbox.RefundInboxRoute
import com.app.eventflow.ui.feature.refunds.recovery.RecoveryRoute
import com.app.eventflow.ui.feature.scanner.ScannerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Rutas tipadas centralizadas (docs/engineering/03 §3). */
object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val EVENT_DETAIL = "event/{eventId}"
    const val CHECKOUT = "checkout/{eventId}/{tariffId}/{quantity}"
    const val TICKET_QR = "ticket/{ticketId}/qr"
    const val SCANNER = "event/{eventId}/scan"
    const val RECOVERY = "ticket/{ticketId}/recovery"
    const val REFUND_INBOX = "organizer/event/{eventId}/refunds"
    const val PROFILE = "me/profile"

    fun eventDetail(eventId: String) = "event/$eventId"

    fun checkout(eventId: String, tariffId: String, quantity: Int) =
        "checkout/$eventId/$tariffId/$quantity"

    fun ticketQr(ticketId: String) = "ticket/$ticketId/qr"

    fun scanner(eventId: String) = "event/$eventId/scan"

    fun recovery(ticketId: String) = "ticket/$ticketId/recovery"

    fun refundInbox(eventId: String) = "organizer/event/$eventId/refunds"
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
                onNavigateToTicketQr = { ticketId -> navController.navigate(Routes.ticketQr(ticketId)) },
                onNavigateToRecovery = { ticketId -> navController.navigate(Routes.recovery(ticketId)) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
            )
        }
        composable(Routes.EVENT_DETAIL) {
            EventDetailRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCheckout = { eventId, tariffId ->
                    navController.navigate(Routes.checkout(eventId, tariffId, 1))
                },
                onNavigateToScanner = { eventId -> navController.navigate(Routes.scanner(eventId)) },
                onNavigateToRefundInbox = { eventId -> navController.navigate(Routes.refundInbox(eventId)) },
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
        composable(Routes.TICKET_QR) {
            TicketQrRoute(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.SCANNER) {
            ScannerRoute(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.RECOVERY) {
            RecoveryRoute(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.REFUND_INBOX) {
            RefundInboxRoute(onNavigateBack = { navController.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileRoute(onNavigateBack = { navController.popBackStack() })
        }
    }

    // Sesión revocada (logout, refresh reusado, familia revocada) → volver a Login
    if (!hasSession && navController.currentDestination?.route == Routes.HOME) {
        navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }
}
