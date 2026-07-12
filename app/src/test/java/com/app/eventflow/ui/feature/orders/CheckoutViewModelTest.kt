package com.app.eventflow.ui.feature.orders

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.app.eventflow.core.network.AppError
import com.app.eventflow.core.network.AppResult
import com.app.eventflow.domain.model.catalog.Category
import com.app.eventflow.domain.model.catalog.EventDetail
import com.app.eventflow.domain.model.catalog.EventPolicyPublic
import com.app.eventflow.domain.model.catalog.Money
import com.app.eventflow.domain.model.catalog.Organizer
import com.app.eventflow.domain.model.catalog.TicketType
import com.app.eventflow.domain.usecase.catalog.GetEventDetailUseCase
import com.app.eventflow.domain.usecase.orders.CancelOrderUseCase
import com.app.eventflow.domain.usecase.orders.CreateOrderUseCase
import com.app.eventflow.domain.usecase.orders.PayOrderUseCase
import com.app.eventflow.testutil.FakeCatalogRepository
import com.app.eventflow.testutil.FakeOrdersRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.testutil.anEventSummary
import com.app.eventflow.ui.feature.checkout.CheckoutUiEffect
import com.app.eventflow.ui.feature.checkout.CheckoutUiEvent
import com.app.eventflow.ui.feature.checkout.CheckoutViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun detailWithTariff(tariffId: String) = EventDetail(
        summary = anEventSummary(id = "e1"),
        description = "desc",
        address = null,
        latitude = null,
        longitude = null,
        organizer = Organizer("org1", "Organizadora"),
        ticketTypes = listOf(
            TicketType(tariffId, "VIP", null, Money("80.00", "USD"), null, true, null),
        ),
        zones = emptyList(),
        sponsors = emptyList(),
        policies = EventPolicyPublic(null, false, 10, false, 24),
        waitlistOpen = false,
    )

    private fun viewModel(
        catalog: FakeCatalogRepository,
        orders: FakeOrdersRepository,
    ) = CheckoutViewModel(
        savedStateHandle = SavedStateHandle(mapOf("eventId" to "e1", "tariffId" to "t1", "quantity" to "2")),
        getEventDetail = GetEventDetailUseCase(catalog),
        createOrder = CreateOrderUseCase(orders),
        payOrder = PayOrderUseCase(orders),
        cancelOrder = CancelOrderUseCase(orders),
    )

    @Test
    fun `happy path - confirm creates order then pay navigates to tickets`() = runTest {
        val catalog = FakeCatalogRepository().apply { detail = detailWithTariff("t1") }
        val orders = FakeOrdersRepository()
        val vm = viewModel(catalog, orders)
        advanceUntilIdle()
        assertEquals("VIP", vm.state.value.tariff?.name)
        assertEquals("USD 160.00", vm.state.value.totalLabel)

        vm.onEvent(CheckoutUiEvent.ConfirmOrder)
        advanceUntilIdle()
        assertNotNull(vm.state.value.order)
        assertEquals("t1" to 2, orders.createCalls.single())

        vm.effects.test {
            vm.onEvent(CheckoutUiEvent.Pay)
            advanceUntilIdle()
            assertEquals(CheckoutUiEffect.NavigateToTickets, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `payment declined shows provider reason and stays on screen`() = runTest {
        val catalog = FakeCatalogRepository().apply { detail = detailWithTariff("t1") }
        val orders = FakeOrdersRepository().apply {
            payResult = AppResult.Failure(
                AppError.Business("payment_failed", "La tarjeta fue rechazada por el emisor"),
            )
        }
        val vm = viewModel(catalog, orders)
        advanceUntilIdle()
        vm.onEvent(CheckoutUiEvent.ConfirmOrder)
        advanceUntilIdle()

        vm.onEvent(CheckoutUiEvent.Pay)
        advanceUntilIdle()

        assertEquals("La tarjeta fue rechazada por el emisor", vm.state.value.paymentError)
        assertNotNull(vm.state.value.order)
    }

    @Test
    fun `sold out on confirm surfaces dedicated state`() = runTest {
        val catalog = FakeCatalogRepository().apply { detail = detailWithTariff("t1") }
        val orders = FakeOrdersRepository().apply {
            createResult = AppResult.Failure(AppError.Conflict("event_sold_out"))
        }
        val vm = viewModel(catalog, orders)
        advanceUntilIdle()
        vm.onEvent(CheckoutUiEvent.ConfirmOrder)
        advanceUntilIdle()

        assertTrue(vm.state.value.soldOut)
        assertNull(vm.state.value.order)
    }

    @Test
    fun `quantity is frozen after order creation`() = runTest {
        val catalog = FakeCatalogRepository().apply { detail = detailWithTariff("t1") }
        val orders = FakeOrdersRepository()
        val vm = viewModel(catalog, orders)
        advanceUntilIdle()

        vm.onEvent(CheckoutUiEvent.QuantityChanged(3))
        assertEquals(3, vm.state.value.quantity)

        vm.onEvent(CheckoutUiEvent.ConfirmOrder)
        advanceUntilIdle()
        vm.onEvent(CheckoutUiEvent.QuantityChanged(5))
        assertEquals(3, vm.state.value.quantity)
    }
}
