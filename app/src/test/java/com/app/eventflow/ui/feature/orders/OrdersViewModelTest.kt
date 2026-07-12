package com.app.eventflow.ui.feature.orders

import com.app.eventflow.core.network.AppError
import com.app.eventflow.domain.model.orders.OrderStatus
import com.app.eventflow.domain.usecase.orders.CancelOrderUseCase
import com.app.eventflow.domain.usecase.orders.ObserveOrdersUseCase
import com.app.eventflow.domain.usecase.orders.PayOrderUseCase
import com.app.eventflow.domain.usecase.orders.RefreshOrdersUseCase
import com.app.eventflow.testutil.FakeOrdersRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.testutil.anOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun viewModel(repo: FakeOrdersRepository) = OrdersViewModel(
        observeOrders = ObserveOrdersUseCase(repo),
        refreshOrders = RefreshOrdersUseCase(repo),
        payOrder = PayOrderUseCase(repo),
        cancelOrder = CancelOrderUseCase(repo),
    )

    @Test
    fun `orders visible from cache even offline`() = runTest {
        val repo = FakeOrdersRepository().apply {
            orders.value = listOf(anOrder(status = OrderStatus.PAID))
            refreshError = AppError.Network
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.orders.size)
        assertTrue(vm.state.value.isOffline)
    }

    @Test
    fun `pay and cancel delegate to repository`() = runTest {
        val repo = FakeOrdersRepository().apply { orders.value = listOf(anOrder()) }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onEvent(OrdersUiEvent.PayClicked("o1"))
        advanceUntilIdle()
        vm.onEvent(OrdersUiEvent.CancelClicked("o1"))
        advanceUntilIdle()

        assertEquals(listOf("o1"), repo.payCalls)
        assertEquals(listOf("o1"), repo.cancelCalls)
    }
}
