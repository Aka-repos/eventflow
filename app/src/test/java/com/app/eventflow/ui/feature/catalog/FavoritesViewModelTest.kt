package com.app.eventflow.ui.feature.catalog

import com.app.eventflow.core.network.AppError
import com.app.eventflow.domain.usecase.catalog.ObserveFavoritesUseCase
import com.app.eventflow.domain.usecase.catalog.RefreshFavoritesUseCase
import com.app.eventflow.domain.usecase.catalog.ToggleFavoriteUseCase
import com.app.eventflow.testutil.FakeCatalogRepository
import com.app.eventflow.testutil.MainDispatcherRule
import com.app.eventflow.testutil.anEventSummary
import com.app.eventflow.ui.feature.catalog.favorites.FavoritesUiEvent
import com.app.eventflow.ui.feature.catalog.favorites.FavoritesViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun viewModel(repo: FakeCatalogRepository) = FavoritesViewModel(
        observeFavorites = ObserveFavoritesUseCase(repo),
        refreshFavorites = RefreshFavoritesUseCase(repo),
        toggleFavorite = ToggleFavoriteUseCase(repo),
    )

    @Test
    fun `favorites from local cache are visible even offline`() = runTest {
        val repo = FakeCatalogRepository().apply {
            favorites.value = listOf(anEventSummary(id = "fav1", isFavorite = true))
            refreshError = AppError.Network
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertEquals(listOf("fav1"), vm.state.value.favorites.map { it.id })
        assertTrue(vm.state.value.isOffline)
    }

    @Test
    fun `remove favorite delegates to repository`() = runTest {
        val repo = FakeCatalogRepository().apply {
            favorites.value = listOf(anEventSummary(id = "fav1", isFavorite = true))
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onEvent(FavoritesUiEvent.FavoriteRemoved(repo.favorites.value.first()))
        advanceUntilIdle()

        assertEquals("fav1" to false, repo.toggles.single())
    }
}
