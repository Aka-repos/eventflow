package com.app.eventflow.ui.feature.catalog

import app.cash.turbine.test
import com.app.eventflow.core.network.AppError
import com.app.eventflow.domain.model.catalog.EventsPage
import com.app.eventflow.domain.usecase.catalog.GetCategoriesUseCase
import com.app.eventflow.domain.usecase.catalog.SearchEventsUseCase
import com.app.eventflow.domain.usecase.catalog.ToggleFavoriteUseCase
import com.app.eventflow.testutil.FakeCatalogRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.testutil.anEventSummary
import com.app.eventflow.ui.feature.catalog.list.CatalogListUiEvent
import com.app.eventflow.ui.feature.catalog.list.CatalogListViewModel
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
class CatalogListViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun viewModel(repo: FakeCatalogRepository) = CatalogListViewModel(
        searchEvents = SearchEventsUseCase(repo),
        getCategories = GetCategoriesUseCase(repo),
        toggleFavorite = ToggleFavoriteUseCase(repo),
    )

    @Test
    fun `initial load emits events and categories`() = runTest {
        val repo = FakeCatalogRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(1, state.events.size)
        assertEquals("Concierto Test", state.events.first().title)
        assertEquals(1, state.categories.size)
    }

    @Test
    fun `network failure surfaces offline error state`() = runTest {
        val repo = FakeCatalogRepository().apply { searchError = AppError.Network }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertTrue(vm.state.value.hasError)
        assertTrue(vm.state.value.isOffline)
    }

    @Test
    fun `query change debounces and searches with filter`() = runTest {
        val repo = FakeCatalogRepository()
        val vm = viewModel(repo)
        advanceUntilIdle()
        repo.searchQueries.clear()

        vm.onEvent(CatalogListUiEvent.QueryChanged("rock"))
        vm.onEvent(CatalogListUiEvent.QueryChanged("rock en"))
        advanceUntilIdle()

        // debounce: solo la última query dispara búsqueda
        assertEquals(1, repo.searchQueries.size)
        assertEquals("rock en", repo.searchQueries.first().q)
    }

    @Test
    fun `load more appends next page using cursor`() = runTest {
        val first = anEventSummary(id = "e1")
        val second = anEventSummary(id = "e2", title = "Otro Evento")
        val repo = FakeCatalogRepository().apply {
            pages = mutableListOf(
                EventsPage(listOf(first), nextCursor = "cur1"),
                EventsPage(listOf(second), nextCursor = null),
            )
        }
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertEquals(listOf("e1"), vm.state.value.events.map { it.id })

        vm.onEvent(CatalogListUiEvent.LoadMore)
        advanceUntilIdle()

        assertEquals(listOf("e1", "e2"), vm.state.value.events.map { it.id })
        assertNull(vm.state.value.nextCursor)
        assertEquals("cur1", repo.searchQueries.last().cursor)
    }

    @Test
    fun `favorite toggle is optimistic and reverts on failure`() = runTest {
        val repo = FakeCatalogRepository().apply { toggleError = AppError.Unknown() }
        val vm = viewModel(repo)
        advanceUntilIdle()
        val event = vm.state.value.events.first()

        vm.effects.test {
            vm.onEvent(CatalogListUiEvent.FavoriteToggled(event))
            // optimista inmediato
            assertEquals(true, vm.state.value.events.first().isFavorite)
            advanceUntilIdle()
            // revertido tras el fallo + mensaje
            assertEquals(false, vm.state.value.events.first().isFavorite)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("e1" to true, repo.toggles.single())
    }
}
